// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.DeploymentSpec.ChangeBlocker;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.NotExistsException;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.ConvergenceSummary;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus;
import com.yahoo.vespa.hosted.controller.deployment.JobController;
import com.yahoo.vespa.hosted.controller.deployment.JobStatus;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.RunLog;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.deployment.Versions;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.net.URI;
import java.time.Instant;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy.canary;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installReal;
import static com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence.broken;
import static com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence.normal;

/**
 * Implements the REST API for the job controller delegated from the Application API.
 *
 * @see JobController
 * @see ApplicationApiHandler
 *
 * @author smorgrav
 * @author jonmv
 */
class JobControllerApiHandlerHelper {

    /**
     * @return Response with all job types that have recorded runs for the application _and_ the status for the last run of that type
     */
    static HttpResponse jobTypeResponse(Controller controller, ApplicationId id, URI baseUriForJobs) {
        Slime slime = new Slime();
        Cursor responseObject = slime.setObject();

        Cursor jobsArray = responseObject.setArray("deployment");
        Arrays.stream(JobType.values())
              .filter(type -> type.environment().isManuallyDeployed())
              .map(devType -> new JobId(id, devType))
              .forEach(job -> {
                  Collection<Run> runs = controller.jobController().runs(job).descendingMap().values();
                  if (runs.isEmpty())
                      return;

                  Cursor jobObject = jobsArray.addObject();
                  jobObject.setString("jobName", job.type().jobName());
                  toSlime(jobObject.setArray("runs"), runs, baseUriForJobs);
              });

        return new SlimeJsonResponse(slime);
    }

    private static void runToSlime(Cursor runObject, Run run, URI baseUriForJobType) {
        runObject.setLong("id", run.id().number());
        runObject.setString("status", nameOf(run.status()));
        runObject.setLong("start", run.start().toEpochMilli());
        run.end().ifPresent(instant -> runObject.setLong("end", instant.toEpochMilli()));

        versionsToSlime(runObject, run.versions());

        Cursor stepsObject = runObject.setObject("steps");
        run.steps().forEach((step, info) -> stepsObject.setString(step.name(), info.status().name()));
        Cursor tasksObject = runObject.setObject("tasks");
        taskStatus(deployReal, run).ifPresent(status -> tasksObject.setString("deploy", status));
        taskStatus(Step.installReal, run).ifPresent(status -> tasksObject.setString("install", status));
        taskStatus(Step.endTests, run).ifPresent(status -> tasksObject.setString("test", status));

        runObject.setString("log", baseUriForJobType.resolve(baseUriForJobType.getPath() + "/run/" + run.id().number()).normalize().toString());
    }

    /** Returns the status of the task represented by the given step, if it has started. */
    private static Optional<String> taskStatus(Step step, Run run) {
        return run.readySteps().contains(step) ? Optional.of("running")
                                               : Optional.ofNullable(run.steps().get(step))
                                                         .filter(info -> info.status() != unfinished)
                                                         .map(info -> info.status().name());
    }

    /** Returns a response with the runs for the given job type. */
    static HttpResponse runResponse(Map<RunId, Run> runs, URI baseUriForJobType) {
        Slime slime = new Slime();
        Cursor cursor = slime.setObject();

        runs.forEach((runid, run) -> runToSlime(cursor.setObject(Long.toString(runid.number())), run, baseUriForJobType));

        return new SlimeJsonResponse(slime);
    }

    private static void versionsToSlime(Cursor runObject, Versions versions) {
        runObject.setString("wantedPlatform", versions.targetPlatform().toString());
        applicationVersionToSlime(runObject.setObject("wantedApplication"), versions.targetApplication());
        versions.sourcePlatform().ifPresent(version -> runObject.setString("currentPlatform", version.toString()));
        versions.sourceApplication().ifPresent(version -> applicationVersionToSlime(runObject.setObject("currentApplication"), version));
    }

    static void applicationVersionToSlime(Cursor versionObject, ApplicationVersion version) {
        versionObject.setString("hash", version.id());
        if (version.isUnknown())
            return;

        versionObject.setLong("build", version.buildNumber().getAsLong());
        Cursor sourceObject = versionObject.setObject("source");
        version.source().ifPresent(source -> {
            sourceObject.setString("gitRepository", source.repository());
            sourceObject.setString("gitBranch", source.branch());
            sourceObject.setString("gitCommit", source.commit());
        });
        version.sourceUrl().ifPresent(url -> versionObject.setString("sourceUrl", url));
        version.commit().ifPresent(commit -> versionObject.setString("commit", commit));
    }

    /**
     * @return Response with logs from a single run
     */
    static HttpResponse runDetailsResponse(JobController jobController, RunId runId, String after) {
        Slime slime = new Slime();
        Cursor detailsObject = slime.setObject();

        Run run = jobController.run(runId)
                               .orElseThrow(() -> new IllegalStateException("Unknown run '" + runId + "'"));
        detailsObject.setBool("active", ! run.hasEnded());
        detailsObject.setString("status", nameOf(run.status()));
        try {
            jobController.updateTestLog(runId);
            jobController.updateVespaLog(runId);
        }
        catch (RuntimeException ignored) { } // Return response when this fails, which it does when, e.g., logserver is booting.

        RunLog runLog = (after == null ? jobController.details(runId) : jobController.details(runId, Long.parseLong(after)))
                .orElseThrow(() -> new NotExistsException(String.format(
                        "No run details exist for application: %s, job type: %s, number: %d",
                        runId.application().toShortString(), runId.type().jobName(), runId.number())));

        Cursor logObject = detailsObject.setObject("log");
        for (Step step : Step.values()) {
            if ( ! runLog.get(step).isEmpty())
                toSlime(logObject.setArray(step.name()), runLog.get(step));
        }
        runLog.lastId().ifPresent(id -> detailsObject.setLong("lastId", id));

        Cursor stepsObject = detailsObject.setObject("steps");
        run.steps().forEach((step, info) -> {
            Cursor stepCursor = stepsObject.setObject(step.name());
            stepCursor.setString("status", info.status().name());
            info.startTime().ifPresent(startTime -> stepCursor.setLong("startMillis", startTime.toEpochMilli()));
            run.convergenceSummary().ifPresent(summary -> {
                // If initial installation never succeeded, but is part of the job, summary concerns it.
                // If initial succeeded, or is not part of this job, summary concerns upgrade installation.
                if (   step == installInitialReal && info.status() != succeeded
                    || step == installReal && run.stepStatus(installInitialReal).map(status -> status == succeeded).orElse(true))
                    toSlime(stepCursor.setObject("convergence"), summary);
            });
        });

        // If a test report is available, include it in the response.
        Optional<String> testReport = jobController.getTestReport(runId);
        testReport.map(SlimeUtils::jsonToSlime)
                .map(Slime::get)
                .ifPresent(reportCursor -> SlimeUtils.copyObject(reportCursor, detailsObject.setObject("testReport")));

        return new SlimeJsonResponse(slime);
    }

    private static void toSlime(Cursor summaryObject, ConvergenceSummary summary) {
        summaryObject.setLong("nodes", summary.nodes());
        summaryObject.setLong("down", summary.down());
        summaryObject.setLong("needPlatformUpgrade", summary.needPlatformUpgrade());
        summaryObject.setLong("upgrading", summary.upgradingPlatform());
        summaryObject.setLong("needReboot", summary.needReboot());
        summaryObject.setLong("rebooting", summary.rebooting());
        summaryObject.setLong("needRestart", summary.needRestart());
        summaryObject.setLong("restarting", summary.restarting());
        summaryObject.setLong("upgradingOs", summary.upgradingOs());
        summaryObject.setLong("upgradingFirmware", summary.upgradingFirmware());
        summaryObject.setLong("services", summary.services());
        summaryObject.setLong("needNewConfig", summary.needNewConfig());
    }

    private static void toSlime(Cursor entryArray, List<LogEntry> entries) {
        entries.forEach(entry -> toSlime(entryArray.addObject(), entry));
    }

    private static void toSlime(Cursor entryObject, LogEntry entry) {
        entryObject.setLong("at", entry.at().toEpochMilli());
        entryObject.setString("type", entry.type().name());
        entryObject.setString("message", entry.message());
    }

    /**
     * Unpack payload and submit to job controller. Defaults instance to 'default' and renders the
     * application version on success.
     *
     * @return Response with the new application version
     */
    static HttpResponse submitResponse(JobController jobController, String tenant, String application,
                                       Optional<SourceRevision> sourceRevision, Optional<String> authorEmail,
                                       Optional<String> sourceUrl, long projectId,
                                       ApplicationPackage applicationPackage, byte[] testPackage) {
        ApplicationVersion version = jobController.submit(TenantAndApplicationId.from(tenant, application),
                                                          sourceRevision,
                                                          authorEmail,
                                                          sourceUrl,
                                                          projectId,
                                                          applicationPackage,
                                                          testPackage);

        return new MessageResponse(version.toString());
    }

    /** Aborts any job of the given type. */
    static HttpResponse abortJobResponse(JobController jobs, ApplicationId id, JobType type) {
        Slime slime = new Slime();
        Cursor responseObject = slime.setObject();
        Optional<Run> run = jobs.last(id, type).flatMap(last -> jobs.active(last.id()));
        if (run.isPresent()) {
            jobs.abort(run.get().id());
            responseObject.setString("message", "Aborting " + run.get().id());
        }
        else
            responseObject.setString("message", "Nothing to abort.");
        return new SlimeJsonResponse(slime);
    }

    private static String nameOf(RunStatus status) {
        switch (status) {
            case running:                    return "running";
            case aborted:                    return "aborted";
            case error:                      return "error";
            case testFailure:                return "testFailure";
            case endpointCertificateTimeout: return "endpointCertificateTimeout";
            case outOfCapacity:              return "outOfCapacity";
            case installationFailed:         return "installationFailed";
            case deploymentFailed:           return "deploymentFailed";
            case success:                    return "success";
            default:                         throw new IllegalArgumentException("Unexpected status '" + status + "'");
        }
    }

    /**
     * @return Response with all job types that have recorded runs for the application _and_ the status for the last run of that type
     */
    static HttpResponse overviewResponse(Controller controller, TenantAndApplicationId id, URI baseUriForDeployments) {
        Application application = controller.applications().requireApplication(id);
        DeploymentStatus status = controller.jobController().deploymentStatus(application);

        Slime slime = new Slime();
        Cursor responseObject = slime.setObject();
        responseObject.setString("tenant", id.tenant().value());
        responseObject.setString("application", id.application().value());
        application.projectId().ifPresent(projectId -> responseObject.setLong("projectId", projectId));

        Map<JobId, List<Versions>> jobsToRun = status.jobsToRun();
        Cursor stepsArray = responseObject.setArray("steps");
        VersionStatus versionStatus = controller.readVersionStatus();
        for (DeploymentStatus.StepStatus stepStatus : status.allSteps()) {
            Change change = status.application().require(stepStatus.instance()).change();
            Cursor stepObject = stepsArray.addObject();
            stepObject.setString("type", stepStatus.type().name());
            stepStatus.dependencies().stream()
                      .map(status.allSteps()::indexOf)
                      .forEach(stepObject.setArray("dependencies")::addLong);
            stepObject.setBool("declared", stepStatus.isDeclared());
            stepObject.setString("instance", stepStatus.instance().value());

            stepStatus.readyAt(change).ifPresent(ready -> stepObject.setLong("readyAt", ready.toEpochMilli()));
            stepStatus.readyAt(change)
                      .filter(controller.clock().instant()::isBefore)
                      .ifPresent(until -> stepObject.setLong("delayedUntil", until.toEpochMilli()));
            stepStatus.pausedUntil().ifPresent(until -> stepObject.setLong("pausedUntil", until.toEpochMilli()));
            stepStatus.coolingDownUntil(change).ifPresent(until -> stepObject.setLong("coolingDownUntil", until.toEpochMilli()));
            stepStatus.blockedUntil(Change.of(controller.systemVersion(versionStatus))) // Dummy version — just anything with a platform.
                      .ifPresent(until -> stepObject.setLong("platformBlockedUntil", until.toEpochMilli()));
            application.latestVersion().map(Change::of).flatMap(stepStatus::blockedUntil) // Dummy version — just anything with an application.
                      .ifPresent(until -> stepObject.setLong("applicationBlockedUntil", until.toEpochMilli()));

            if (stepStatus.type() == DeploymentStatus.StepType.delay)
                stepStatus.completedAt(change).ifPresent(completed -> stepObject.setLong("completedAt", completed.toEpochMilli()));

            if (stepStatus.type() == DeploymentStatus.StepType.instance) {
                Cursor deployingObject = stepObject.setObject("deploying");
                if ( ! change.isEmpty()) {
                    change.platform().ifPresent(version -> deployingObject.setString("platform", version.toString()));
                    change.application().ifPresent(version -> toSlime(deployingObject.setObject("application"), version));
                }

                Cursor latestVersionsObject = stepObject.setObject("latestVersions");
                List<ChangeBlocker> blockers = application.deploymentSpec().requireInstance(stepStatus.instance()).changeBlocker();
                latestVersionWithCompatibleConfidenceAndNotNewerThanSystem(versionStatus.versions(),
                                                                           application.deploymentSpec().requireInstance(stepStatus.instance()).upgradePolicy())
                          .ifPresent(latestPlatform -> {
                              Cursor latestPlatformObject = latestVersionsObject.setObject("platform");
                              latestPlatformObject.setString("platform", latestPlatform.versionNumber().toFullString());
                              latestPlatformObject.setLong("at", latestPlatform.committedAt().toEpochMilli());
                              latestPlatformObject.setBool("upgrade", application.require(stepStatus.instance()).productionDeployments().values().stream()
                                                                                 .anyMatch(deployment -> deployment.version().isBefore(latestPlatform.versionNumber())));
                              toSlime(latestPlatformObject.setArray("blockers"), blockers.stream().filter(ChangeBlocker::blocksVersions));
                          });
                application.latestVersion().ifPresent(latestApplication -> {
                    Cursor latestApplicationObject = latestVersionsObject.setObject("application");
                    toSlime(latestApplicationObject.setObject("application"), latestApplication);
                    latestApplicationObject.setLong("at", latestApplication.buildTime().orElse(Instant.EPOCH).toEpochMilli());
                    latestApplicationObject.setBool("upgrade", application.require(stepStatus.instance()).productionDeployments().values().stream()
                                                                          .anyMatch(deployment -> deployment.applicationVersion().compareTo(latestApplication) < 0));
                    toSlime(latestApplicationObject.setArray("blockers"), blockers.stream().filter(ChangeBlocker::blocksRevisions));
                });
            }

            stepStatus.job().ifPresent(job -> {
                stepObject.setString("jobName", job.type().jobName());
                URI baseUriForJob = baseUriForDeployments.resolve(baseUriForDeployments.getPath() +
                                                                     "/../instance/" + job.application().instance().value() +
                                                                     "/job/" + job.type().jobName()).normalize();
                stepObject.setString("url", baseUriForJob.toString());
                stepObject.setString("environment", job.type().environment().value());
                stepObject.setString("region", job.type().zone(controller.system()).value());

                if (job.type().isProduction() && job.type().isDeployment()) {
                    status.deploymentFor(job).ifPresent(deployment -> {
                        stepObject.setString("currentPlatform", deployment.version().toFullString());
                        toSlime(stepObject.setObject("currentApplication"), deployment.applicationVersion());
                    });
                }

                JobStatus jobStatus = status.jobs().get(job).get();
                Cursor toRunArray = stepObject.setArray("toRun");
                for (Versions versions : jobsToRun.getOrDefault(job, List.of())) {
                    boolean running = jobStatus.lastTriggered()
                                               .map(run ->    jobStatus.isRunning()
                                                           && versions.targetsMatch(run.versions())
                                                           && (job.type().isProduction() || versions.sourcesMatchIfPresent(run.versions())))
                                               .orElse(false);
                    if (running)
                        continue; // Run will be contained in the "runs" array.

                    Cursor runObject = toRunArray.addObject();
                    toSlime(runObject.setObject("versions"), versions);
                }

                toSlime(stepObject.setArray("runs"), jobStatus.runs().descendingMap().values(), baseUriForJob);
            });
        }

        return new SlimeJsonResponse(slime);
    }

    private static void toSlime(Cursor versionObject, ApplicationVersion version) {
        version.buildNumber().ifPresent(id -> versionObject.setLong("build", id));
        version.compileVersion().ifPresent(platform -> versionObject.setString("compileVersion", platform.toFullString()));
        version.sourceUrl().ifPresent(url -> versionObject.setString("sourceUrl", url));
        version.commit().ifPresent(commit -> versionObject.setString("commit", commit));
    }

    private static void toSlime(Cursor versionsObject, Versions versions) {
        versionsObject.setString("targetPlatform", versions.targetPlatform().toFullString());
        toSlime(versionsObject.setObject("targetApplication"), versions.targetApplication());
        versions.sourcePlatform().ifPresent(platform -> versionsObject.setString("sourcePlatform", platform.toFullString()));
        versions.sourceApplication().ifPresent(application -> toSlime(versionsObject.setObject("sourceApplication"), application));
    }

    private static void toSlime(Cursor blockersArray, Stream<ChangeBlocker> blockers) {
        blockers.forEach(blocker -> {
            Cursor blockerObject = blockersArray.addObject();
            blocker.window().days().stream()
                   .map(day -> day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                   .forEach(blockerObject.setArray("days")::addString);
            blocker.window().hours()
                   .forEach(blockerObject.setArray("hours")::addLong);
            blockerObject.setString("zone", blocker.window().zone().toString());
        });
    }

    private static Optional<VespaVersion> latestVersionWithCompatibleConfidenceAndNotNewerThanSystem(List<VespaVersion> versions,
                                                                                                     DeploymentSpec.UpgradePolicy policy) {
        int i;
        for (i = versions.size(); i-- > 0; )
            if (versions.get(i).isSystemVersion())
                break;

        if (i < 0)
            return Optional.empty();

        VespaVersion.Confidence required = policy == canary ? broken : normal;
        for (int j = i; j >= 0; j--)
            if (versions.get(j).confidence().equalOrHigherThan(required))
                return Optional.of(versions.get(j));

        return Optional.of(versions.get(i));
    }

    private static void toSlime(Cursor runsArray, Collection<Run> runs, URI baseUriForJob) {
        runs.stream().limit(10).forEach(run -> {
            Cursor runObject = runsArray.addObject();
            runObject.setLong("id", run.id().number());
            runObject.setString("url", baseUriForJob.resolve(baseUriForJob.getPath() + "/run/" + run.id().number()).toString());
            runObject.setLong("start", run.start().toEpochMilli());
            run.end().ifPresent(end -> runObject.setLong("end", end.toEpochMilli()));
            runObject.setString("status", run.status().name());
            toSlime(runObject.setObject("versions"), run.versions());
            Cursor runStepsArray = runObject.setArray("steps");
            run.steps().forEach((step, info) -> {
                Cursor runStepObject = runStepsArray.addObject();
                runStepObject.setString("name", step.name());
                runStepObject.setString("status", info.status().name());
            });
        });
    }

}
