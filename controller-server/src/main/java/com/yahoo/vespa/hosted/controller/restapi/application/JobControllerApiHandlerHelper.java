// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.DeploymentSpec.ChangeBlocker;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.NotExistsException;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.ConvergenceSummary;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus.DelayCause;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus.Readiness;
import com.yahoo.vespa.hosted.controller.deployment.JobController;
import com.yahoo.vespa.hosted.controller.deployment.JobStatus;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.RunLog;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.deployment.Submission;
import com.yahoo.vespa.hosted.controller.deployment.Versions;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.time.Instant;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.stream.Stream;

import static com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy.canary;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.copyVespaLogs;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installReal;
import static com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence.broken;
import static com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence.normal;
import static java.util.Comparator.reverseOrder;

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
        JobType.allIn(controller.zoneRegistry()).stream()
               .filter(type -> type.environment().isManuallyDeployed())
               .map(devType -> new JobId(id, devType))
               .forEach(job -> {
                   Collection<Run> runs = controller.jobController().runs(job).descendingMap().values();
                   if (runs.isEmpty())
                       return;

                   Cursor jobObject = jobsArray.addObject();
                   jobObject.setString("jobName", job.type().jobName());
                   toSlime(jobObject.setArray("runs"), runs, controller.applications().requireApplication(TenantAndApplicationId.from(id)), 10, baseUriForJobs);
               });

        return new SlimeJsonResponse(slime);
    }

    /** Returns a response with the runs for the given job type. */
    static HttpResponse runResponse(Controller controller, JobId id, Optional<String> limitStr, URI baseUriForJobType) {
        Slime slime = new Slime();
        Cursor cursor = slime.setObject();
        Application application = controller.applications().requireApplication(TenantAndApplicationId.from(id.application()));
        NavigableMap<RunId, Run> runs = controller.jobController().runs(id).descendingMap();

        int limit = limitStr.map(Integer::parseInt).orElse(Integer.MAX_VALUE);
        toSlime(cursor.setArray("runs"), runs.values(), application, limit, baseUriForJobType);
        controller.applications().decideCloudAccountOf(new DeploymentId(id.application(),
                                                                        runs.lastEntry().getValue().id().job().type().zone()), // Urgh, must use a job with actual zone.
                                                       application.deploymentSpec())
                  .ifPresent(cloudAccount -> cursor.setObject("enclave").setString("cloudAccount", cloudAccount.value()));

        return new SlimeJsonResponse(slime);
    }

    /**
     * @return Response with logs from a single run
     */
    static HttpResponse runDetailsResponse(JobController jobController, RunId runId, String after) {
        Slime slime = new Slime();
        Cursor detailsObject = slime.setObject();

        Run run = jobController.run(runId);
        detailsObject.setBool("active", ! run.hasEnded());
        detailsObject.setString("status", nameOf(run.status()));
        run.reason().ifPresent(reason -> detailsObject.setString("reason", reason));
        try {
            jobController.updateTestLog(runId);
            jobController.updateVespaLog(runId);
        }
        catch (RuntimeException ignored) { } // Return response when this fails, which it does when, e.g., logserver is booting.

        RunLog runLog = (after == null ? jobController.details(runId) : jobController.details(runId, Long.parseLong(after)))
                .orElseThrow(() -> new NotExistsException(Text.format(
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
        Optional<String> testReport = jobController.getTestReports(runId);
        testReport.map(SlimeUtils::jsonToSlime)
                  .map(Slime::get)
                  .ifPresent(reportArrayCursor -> SlimeUtils.copyArray(reportArrayCursor, detailsObject.setArray("testReports")));

        boolean logsStored = run.stepStatus(copyVespaLogs).map(succeeded::equals).orElse(false);
        if (run.hasStep(copyVespaLogs) && ! runId.type().isProduction() && JobController.deploymentCompletedAt(run, false).isPresent())
            detailsObject.setBool("vespaLogsActive", ! logsStored);

        if (runId.type().isTest() && JobController.deploymentCompletedAt(run, true).isPresent())
            detailsObject.setBool("testerLogsActive", ! logsStored);

        return new SlimeJsonResponse(slime);
    }

    /** Proxies a Vespa log request for a run to S3 once logs have been copied, or to logserver before this. */
    static HttpResponse vespaLogsResponse(JobController jobController, RunId runId, long fromMillis, boolean tester) {
        return new HttpResponse(200) {
            @Override public void render(OutputStream out) throws IOException {
                try (InputStream logs = jobController.getVespaLogs(runId, fromMillis, tester)) {
                    logs.transferTo(out);
                }
            }
        };
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
        summaryObject.setLong("retiring", summary.retiring());
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
    static HttpResponse submitResponse(JobController jobController, TenantAndApplicationId id, Submission submission, long projectId) {
        return new MessageResponse("application " + jobController.submit(id, submission, projectId));
    }

    /** Aborts any job of the given type. */
    static HttpResponse abortJobResponse(JobController jobs, HttpRequest request, ApplicationId id, JobType type) {
        Slime slime = new Slime();
        Cursor responseObject = slime.setObject();
        Optional<Run> run = jobs.last(id, type).flatMap(last -> jobs.active(last.id()));
        if (run.isPresent()) {
            jobs.abort(run.get().id(), "aborted by " + request.getJDiscRequest().getUserPrincipal().getName(), true);
            responseObject.setString("message", "Aborting " + run.get().id());
        }
        else
            responseObject.setString("message", "Nothing to abort.");
        return new SlimeJsonResponse(slime);
    }

    private static String nameOf(RunStatus status) {
        return switch (status) {
            case reset, running                       -> "running";
            case cancelled, aborted                   -> "aborted";
            case error                                -> "error";
            case testFailure                          -> "testFailure";
            case noTests                              -> "noTests";
            case endpointCertificateTimeout           -> "endpointCertificateTimeout";
            case nodeAllocationFailure                -> "nodeAllocationFailure";
            case installationFailed                   -> "installationFailed";
            case invalidApplication, deploymentFailed -> "deploymentFailed";
            case success                              -> "success";
            case quotaExceeded                        -> "quotaExceeded";
        };
    }

    /**
     * Returns response with all job types that have recorded runs for the application
     * _and_ the status for the last run of that type
     */
    static HttpResponse overviewResponse(Controller controller, TenantAndApplicationId id, URI baseUriForDeployments) {
        Application application = controller.applications().requireApplication(id);
        DeploymentStatus status = controller.jobController().deploymentStatus(application);

        Slime slime = new Slime();
        Cursor responseObject = slime.setObject();
        responseObject.setString("tenant", id.tenant().value());
        responseObject.setString("application", id.application().value());
        application.projectId().ifPresent(projectId -> responseObject.setLong("projectId", projectId));

        Map<JobId, List<DeploymentStatus.Job>> jobsToRun = status.jobsToRun();
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

            // TODO: recursively search dependents for what is the relevant partial change when this is a delay step ...
            Instant now = controller.clock().instant();
            Readiness readiness = stepStatus.pausedUntil().okAt(now)
                                  ? stepStatus.job().map(jobsToRun::get).map(job -> job.get(0).readiness())
                                              .orElse(stepStatus.readiness(change))
                                  : stepStatus.pausedUntil();
            if (readiness.ok()) {
                // TODO jonmv: remove after UI changes.
                stepObject.setLong("readyAt", readiness.at().toEpochMilli());

                if ( ! readiness.okAt(now)) stepObject.setLong("delayedUntil", readiness.at().toEpochMilli());
            }

            // TODO jonmv: remove after UI changes.
            if (readiness.cause() == DelayCause.coolingDown) stepObject.setLong("coolingDownUntil", readiness.at().toEpochMilli());
            if (readiness.cause() == DelayCause.paused) stepObject.setLong("pausedUntil", readiness.at().toEpochMilli());

            Readiness platformReadiness = stepStatus.blockedUntil(Change.of(controller.systemVersion(versionStatus))); // Dummy version — just anything with a platform.
            if ( ! platformReadiness.okAt(now))
                stepObject.setLong("platformBlockedUntil", platformReadiness.at().toEpochMilli());
            Readiness applicationReadiness = stepStatus.blockedUntil(Change.of(RevisionId.forProduction(1))); // Dummy version — just anything with an application.
            if ( ! applicationReadiness.okAt(now))
                stepObject.setLong("applicationBlockedUntil", applicationReadiness.at().toEpochMilli());

            if (stepStatus.type() == DeploymentStatus.StepType.delay)
                stepStatus.completedAt(change).ifPresent(completed -> stepObject.setLong("completedAt", completed.toEpochMilli()));

            if (stepStatus.type() == DeploymentStatus.StepType.instance) {
                Cursor deployingObject = stepObject.setObject("deploying");
                if ( ! change.isEmpty()) {
                    change.platform().ifPresent(version -> deployingObject.setString("platform", version.toFullString()));
                    change.revision().ifPresent(revision -> toSlime(deployingObject.setObject("application"), application.revisions().get(revision)));
                    if (change.isPlatformPinned()) deployingObject.setBool("pinned", true);
                    if (change.isPlatformPinned()) deployingObject.setBool("platformPinned", true);
                    if (change.isRevisionPinned()) deployingObject.setBool("revisionPinned", true);
                }

                Cursor latestVersionsObject = stepObject.setObject("latestVersions");
                List<ChangeBlocker> blockers = application.deploymentSpec().requireInstance(stepStatus.instance()).changeBlocker();
                var deployments = application.require(stepStatus.instance()).productionDeployments().values();
                List<VespaVersion> availablePlatforms = availablePlatforms(versionStatus.versions(),
                                                                           application.deploymentSpec().requireInstance(stepStatus.instance()).upgradePolicy());
                if ( ! availablePlatforms.isEmpty()) {
                    Cursor latestPlatformObject = latestVersionsObject.setObject("platform");
                    VespaVersion latestPlatform = availablePlatforms.get(0);
                    latestPlatformObject.setString("platform", latestPlatform.versionNumber().toFullString());
                    latestPlatformObject.setLong("at", latestPlatform.committedAt().toEpochMilli());
                    latestPlatformObject.setBool("upgrade",    change.platform().map(latestPlatform.versionNumber()::isAfter).orElse(true) && deployments.isEmpty()
                                                            || deployments.stream().anyMatch(deployment -> deployment.version().isBefore(latestPlatform.versionNumber())));

                    Cursor availableArray = latestPlatformObject.setArray("available");
                    boolean isUpgrade = true;
                    for (VespaVersion available : availablePlatforms) {
                        if (   deployments.stream().anyMatch(deployment -> deployment.version().isAfter(available.versionNumber()))
                            || deployments.stream().noneMatch(deployment -> deployment.version().isBefore(available.versionNumber())) && ! deployments.isEmpty()
                            || status.hasCompleted(stepStatus.instance(), Change.of(available.versionNumber()))
                            || change.platform().map(available.versionNumber()::compareTo).orElse(1) < 0)
                            isUpgrade = false;

                        Cursor platformObject = availableArray.addObject();
                        platformObject.setString("platform", available.versionNumber().toFullString());
                        platformObject.setBool("upgrade", isUpgrade || change.platform().map(available.versionNumber()::equals).orElse(false));
                    }
                    toSlime(latestPlatformObject.setArray("blockers"), blockers.stream().filter(ChangeBlocker::blocksVersions));
                }
                List<ApplicationVersion> availableApplications = new ArrayList<>(application.revisions().deployable(false));
                if ( ! availableApplications.isEmpty()) {
                    var latestApplication = availableApplications.get(0);
                    Cursor latestApplicationObject = latestVersionsObject.setObject("application");
                    toSlime(latestApplicationObject.setObject("application"), latestApplication);
                    latestApplicationObject.setLong("at", latestApplication.buildTime().orElse(Instant.EPOCH).toEpochMilli());
                    latestApplicationObject.setBool("upgrade",    change.revision().map(latestApplication.id()::compareTo).orElse(1) > 0 && deployments.isEmpty()
                                                               || deployments.stream().anyMatch(deployment -> deployment.revision().compareTo(latestApplication.id()) < 0));

                    Cursor availableArray = latestApplicationObject.setArray("available");
                    for (ApplicationVersion available : availableApplications)
                        toSlime(availableArray.addObject().setObject("application"), available);

                    toSlime(latestApplicationObject.setArray("blockers"), blockers.stream().filter(ChangeBlocker::blocksRevisions));
                }
            }

            boolean showDelayCause = true;
            if (stepStatus.job().isPresent()) {
                JobId job = stepStatus.job().get();
                stepObject.setString("jobName", job.type().jobName());
                URI baseUriForJob = baseUriForDeployments.resolve(baseUriForDeployments.getPath() +
                                                                     "/../instance/" + job.application().instance().value() +
                                                                     "/job/" + job.type().jobName()).normalize();
                stepObject.setString("url", baseUriForJob.toString());
                stepObject.setString("environment", job.type().environment().value());
                if ( ! job.type().environment().isTest()) {
                    stepObject.setString("region", job.type().zone().value());
                }

                if (job.type().isProduction() && job.type().isDeployment()) {
                    status.deploymentFor(job).ifPresent(deployment -> {
                        stepObject.setString("currentPlatform", deployment.version().toFullString());
                        toSlime(stepObject.setObject("currentApplication"), application.revisions().get(deployment.revision()));
                    });
                }

                JobStatus jobStatus = status.jobs().get(job).get();
                Cursor toRunArray = stepObject.setArray("toRun");
                showDelayCause = readiness.cause() == DelayCause.paused;
                for (DeploymentStatus.Job versions : jobsToRun.getOrDefault(job, List.of())) {
                    boolean running = jobStatus.lastTriggered()
                                               .map(run ->    jobStatus.isRunning()
                                                           && versions.versions().targetsMatch(run.versions())
                                                           && (job.type().isProduction() || versions.versions().sourcesMatchIfPresent(run.versions())))
                                               .orElse(false);
                    if (running)
                        continue; // Run will be contained in the "runs" array.

                    showDelayCause = true;
                    Cursor runObject = toRunArray.addObject();
                    toSlime(runObject.setObject("versions"), versions.versions(), application);
                }

                if ( ! jobStatus.runs().isEmpty())
                    controller.applications().decideCloudAccountOf(new DeploymentId(job.application(),
                                                                                    jobStatus.runs().lastEntry().getValue().id().job().type().zone()), // Urgh, must use a job with actual zone.
                                                                   status.application().deploymentSpec())
                              .ifPresent(cloudAccount -> stepObject.setObject("enclave").setString("cloudAccount", cloudAccount.value()));

                toSlime(stepObject.setArray("runs"), jobStatus.runs().descendingMap().values(), application, 10, baseUriForJob);
            }
            stepObject.setString("delayCause",
                                 ! showDelayCause
                                 ? (String) null
                                 : switch (readiness.cause()) {
                                       case none -> null;
                                       case invalidPackage -> "invalidPackage";
                                       case paused -> "paused";
                                       case coolingDown -> "coolingDown";
                                       case changeBlocked -> "changeBlocked";
                                       case blocked -> "blocked";
                                       case running -> "running";
                                       case notReady -> "notReady";
                                       case unverified -> "unverified";
                                   });
        }

        Cursor buildsArray = responseObject.setArray("builds");
        application.revisions().withPackage().stream().sorted(reverseOrder()).forEach(version -> toRichSlime(buildsArray.addObject(), version));

        return new SlimeJsonResponse(slime);
    }

    static void toRichSlime(Cursor versionObject, ApplicationVersion version) {
        toSlime(versionObject, version);
        version.description().ifPresent(description -> versionObject.setString("description", description));
        if (version.risk() != 0) versionObject.setLong("risk", version.risk());
        versionObject.setBool("deployable", version.isDeployable());
        version.submittedAt().ifPresent(submittedAt -> versionObject.setLong("submittedAt", submittedAt.toEpochMilli()));
    }

    static void toSlime(Cursor versionObject, ApplicationVersion version) {
        version.buildNumber().ifPresent(id -> versionObject.setLong("build", id));
        version.compileVersion().ifPresent(platform -> versionObject.setString("compileVersion", platform.toFullString()));
        version.sourceUrl().ifPresent(url -> versionObject.setString("sourceUrl", url));
        version.commit().ifPresent(commit -> versionObject.setString("commit", commit));
    }

    private static void toSlime(Cursor versionsObject, Versions versions, Application application) {
        versionsObject.setString("targetPlatform", versions.targetPlatform().toFullString());
        toSlime(versionsObject.setObject("targetApplication"), application.revisions().get(versions.targetRevision()));
        versions.sourcePlatform().ifPresent(platform -> versionsObject.setString("sourcePlatform", platform.toFullString()));
        versions.sourceRevision().ifPresent(revision -> toSlime(versionsObject.setObject("sourceApplication"), application.revisions().get(revision)));
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

    private static List<VespaVersion> availablePlatforms(List<VespaVersion> versions, DeploymentSpec.UpgradePolicy policy) {
        int i;
        for (i = versions.size(); i-- > 0; )
            if (versions.get(i).isSystemVersion())
                break;

        if (i < 0)
            return List.of();

        List<VespaVersion> candidates = new ArrayList<>();
        VespaVersion.Confidence required = policy == canary ? broken : normal;
        for (int j = i; j >= 0; j--)
            if (versions.get(j).confidence().equalOrHigherThan(required))
                candidates.add(versions.get(j));

        if (candidates.isEmpty())
            candidates.add(versions.get(i));

        return candidates;
    }

    private static void toSlime(Cursor runsArray, Collection<Run> runs, Application application, int limit, URI baseUriForJob) {
        runs.stream().limit(limit).forEach(run -> {
            Cursor runObject = runsArray.addObject();
            runObject.setLong("id", run.id().number());
            runObject.setString("url", baseUriForJob.resolve(baseUriForJob.getPath() + "/run/" + run.id().number()).toString());
            runObject.setLong("start", run.start().toEpochMilli());
            run.end().ifPresent(end -> runObject.setLong("end", end.toEpochMilli()));
            runObject.setString("status", nameOf(run.status()));
            run.reason().ifPresent(reason -> runObject.setString("reason", reason));
            toSlime(runObject.setObject("versions"), run.versions(), application);
            Cursor runStepsArray = runObject.setArray("steps");
            run.steps().forEach((step, info) -> {
                Cursor runStepObject = runStepsArray.addObject();
                runStepObject.setString("name", step.name());
                runStepObject.setString("status", info.status().name());
            });
        });
    }

}
