// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.google.common.base.Joiner;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Instance;
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
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentSteps;
import com.yahoo.vespa.hosted.controller.deployment.JobController;
import com.yahoo.vespa.hosted.controller.deployment.JobList;
import com.yahoo.vespa.hosted.controller.deployment.JobStatus;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.RunLog;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.deployment.Versions;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy.conservative;
import static com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy.defaultPolicy;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployReal;
import static com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence.broken;
import static com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence.high;
import static com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence.normal;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

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
        Application application = controller.applications().requireApplication(TenantAndApplicationId.from(id));
        DeploymentStatus deploymentStatus = controller.jobController().deploymentStatus(application);
        Instance instance = application.require(id.instance());
        Change change = instance.change();
        Optional<DeploymentInstanceSpec> spec = application.deploymentSpec().instance(id.instance());
        Optional<DeploymentSteps> steps = spec.map(s -> new DeploymentSteps(s, controller::system));
        List<JobType> jobs = deploymentStatus.jobSteps().keySet().stream()
                                             .filter(jobId -> id.equals(jobId.application()))
                                             .map(JobId::type)
                                             .collect(Collectors.toUnmodifiableList());
        List<JobType> productionJobs = jobs.stream()
                                           .filter(JobType::isProduction)
                                           .collect(Collectors.toUnmodifiableList());
        Map<JobType, JobStatus> status = deploymentStatus.instanceJobs(id.instance());

        // The logic for pending runs imitates DeploymentTrigger logic; not good, but the trigger wiring must be re-written to reuse :S
        Map<JobType, Versions> pendingProduction =
                productionJobs.stream()
                              .filter(type -> ! controller.applications().deploymentTrigger().isComplete(change, change, instance, type, status.get(type)))
                              .collect(Collectors.toMap(type -> type,
                                                        type -> Versions.from(change,
                                                                              application,
                                                                              Optional.ofNullable(instance.deployments().get(type.zone(controller.system()))),
                                                                              controller.systemVersion()),
                                                        (v1, v2) -> { throw new IllegalStateException("Entries '" + v1 + "' and '" + v2 + "' have the same key!"); },
                                                        LinkedHashMap::new));

        Map<JobType, Run> running = jobs.stream()
                                        .map(type -> controller.jobController().last(id, type))
                                        .flatMap(Optional::stream)
                                        .filter(run -> ! run.hasEnded())
                                        .collect(toMap(run -> run.id().type(),
                                                       run -> run));

        Slime slime = new Slime();
        Cursor responseObject = slime.setObject();

        Cursor lastVersionsObject = responseObject.setObject("lastVersions");
        if (application.latestVersion().isPresent()) {
            lastPlatformToSlime(lastVersionsObject.setObject("platform"), controller, application, instance, status, change, productionJobs, spec);
            lastApplicationToSlime(lastVersionsObject.setObject("application"), application, instance, status, change, productionJobs, controller);
        }

        Cursor deployingObject = responseObject.setObject("deploying");
        if ( ! change.isEmpty()) {
            change.platform().ifPresent(version -> deployingObject.setString("platform", version.toString()));
            change.application().ifPresent(version -> applicationVersionToSlime(deployingObject.setObject("application"), version));
        }

        Cursor deploymentsArray = responseObject.setArray("deployments");
        steps.ifPresent(deploymentSteps -> deploymentSteps.production().forEach(step -> {
            if (step.isTest()) return;
            Cursor deploymentsObject = deploymentsArray.addObject();
            deploymentSteps.toJobs(step).forEach(type -> {
                ZoneId zone = type.zone(controller.system());
                Deployment deployment = instance.deployments().get(zone);
                if (deployment != null)
                    deploymentToSlime(deploymentsObject.setObject(zone.region().value()),
                                      change,
                                      pendingProduction,
                                      running,
                                      type,
                                      status.get(type),
                                      deployment);
            });
        }));

        Cursor jobsObject = responseObject.setObject("jobs");
        steps.ifPresent(deploymentSteps -> jobs.forEach(type -> {
            jobTypeToSlime(jobsObject.setObject(shortNameOf(type, controller.system())),
                           controller,
                           application,
                           instance,
                           status,
                           type,
                           deploymentSteps,
                           pendingProduction,
                           running,
                           baseUriForJobs.resolve(baseUriForJobs.getPath() + "/" + type.jobName()).normalize());
        }));

        Cursor devJobsObject = responseObject.setObject("devJobs");
        for (JobType type : JobType.allIn(controller.system()))
            if (   type.environment() != null
                && type.environment().isManuallyDeployed())
                controller.jobController().last(instance.id(), type)
                          .ifPresent(last -> {
                              Cursor devJobObject = devJobsObject.setObject(type.jobName());
                              runToSlime(devJobObject.setArray("runs").addObject(),
                                         last,
                                         baseUriForJobs.resolve(baseUriForJobs.getPath() + "/" + type.jobName()).normalize());
                              devJobObject.setString("url", baseUriForJobs.resolve(baseUriForJobs.getPath() + "/" + type.jobName()).normalize().toString());
                          });

        return new SlimeJsonResponse(slime);
    }

    private static void lastPlatformToSlime(Cursor lastPlatformObject, Controller controller, Application application,
                                            Instance instance, Map<JobType, JobStatus> status, Change change,
                                            List<JobType> productionJobs, Optional<DeploymentInstanceSpec> instanceSpec) {
        VespaVersion lastVespa = controller.versionStatus().version(controller.systemVersion());
        VespaVersion.Confidence targetConfidence = instanceSpec.map(spec -> Map.of(defaultPolicy, normal,
                                                                                   conservative, high)
                                                                               .getOrDefault(spec.upgradePolicy(), broken))
                                                               .orElse(normal);
        for (VespaVersion version : controller.versionStatus().versions())
            if (   ! version.versionNumber().isAfter(controller.systemVersion())
                &&   version.confidence().equalOrHigherThan(targetConfidence))
                lastVespa = version;

        Version lastPlatform = lastVespa.versionNumber();
        lastPlatformObject.setString("platform", lastPlatform.toString());
        lastPlatformObject.setLong("at", lastVespa.committedAt().toEpochMilli());
        long completed = productionJobs.stream()
                                       .filter(type -> ! type.isTest())
                                       .filter(type -> controller.applications().deploymentTrigger().isComplete(Change.of(lastPlatform), change.withoutPlatform().withoutPin().with(lastPlatform), instance, type, status.get(type)))
                                       .count();
        long total = productionJobs.stream().filter(type -> ! type.isTest()).count();
        if (Optional.of(lastPlatform).equals(change.platform()))
            lastPlatformObject.setString("deploying", completed + " of " + total + " complete");
        else if (completed == total)
            lastPlatformObject.setString("completed", completed + " of " + total + " complete");
        else if ( ! application.deploymentSpec().requireInstance(instance.name()).canUpgradeAt(controller.clock().instant())) {
            lastPlatformObject.setString("blocked", application.deploymentSpec().instances().stream()
                                                               .flatMap(spec -> spec.changeBlocker().stream())
                                                               .filter(blocker -> blocker.blocksVersions())
                                                               .filter(blocker -> blocker.window().includes(controller.clock().instant()))
                                                               .findAny().map(blocker -> blocker.window().toString()).get());
        }
        else
            lastPlatformObject.setString("pending",
                                         instance.change().isEmpty()
                                                 ? "Waiting for upgrade slot"
                                                 : "Waiting for " + instance.change() + " to complete");
    }

    private static void lastApplicationToSlime(Cursor lastApplicationObject, Application application, Instance instance, Map<JobType, JobStatus> status, Change change, List<JobType> productionJobs, Controller controller) {
        ApplicationVersion lastApplication = application.latestVersion().get();
        applicationVersionToSlime(lastApplicationObject.setObject("application"), lastApplication);
        lastApplicationObject.setLong("at", lastApplication.buildTime().get().toEpochMilli());
        long completed = productionJobs.stream()
                                       .filter(type -> ! type.isTest())
                                       .filter(type -> controller.applications().deploymentTrigger().isComplete(Change.of(lastApplication), change.withoutApplication().with(lastApplication), instance, type, status.get(type)))
                                       .count();
        long total = productionJobs.stream().filter(type -> ! type.isTest()).count();
        if (Optional.of(lastApplication).equals(change.application()))
            lastApplicationObject.setString("deploying", completed + " of " + total + " complete");
        else if (completed == total)
            lastApplicationObject.setString("completed", completed + " of " + total + " complete");
        else if ( ! application.deploymentSpec().instances().stream()
                               .allMatch(spec -> spec.canChangeRevisionAt(controller.clock().instant()))) {
            lastApplicationObject.setString("blocked", application.deploymentSpec().instances().stream()
                                                                  .flatMap(spec -> spec.changeBlocker().stream())
                                                                  .filter(blocker -> blocker.blocksRevisions())
                                                                  .filter(blocker -> blocker.window().includes(controller.clock().instant()))
                                                                  .findAny().map(blocker -> blocker.window().toString()).get());
        }
        else
            lastApplicationObject.setString("pending", "Waiting for current deployment to complete");
    }

    private static void deploymentToSlime(Cursor deploymentObject, Change change,
                                          Map<JobType, Versions> pendingProduction, Map<JobType, Run> running,
                                          JobType type, JobStatus jobStatus, Deployment deployment) {
        deploymentObject.setLong("at", deployment.at().toEpochMilli());
        deploymentObject.setString("platform", deployment.version().toString());
        applicationVersionToSlime(deploymentObject.setObject("application"), deployment.applicationVersion());
        deploymentObject.setBool("verified", jobStatus.lastSuccess()
                                                      .map(Run::versions)
                                                      .filter(run ->    run.targetPlatform().equals(deployment.version())
                                                                     && run.targetApplication().equals(deployment.applicationVersion()))
                                                      .isPresent());
        if (running.containsKey(type))
            deploymentObject.setString("status", running.get(type).stepStatus(deployReal).equals(Optional.of(unfinished)) ? "deploying" : "verifying");
        else if (change.hasTargets())
            deploymentObject.setString("status", pendingProduction.containsKey(type) ? "pending" : "completed");
    }

    private static void jobTypeToSlime(Cursor jobObject, Controller controller, Application application, Instance instance,
                                       Map<JobType, JobStatus> status, JobType type, DeploymentSteps steps,
                                       Map<JobType, Versions> pendingProduction, Map<JobType, Run> running, URI baseUriForJob) {
        instance.jobPause(type).ifPresent(until -> jobObject.setLong("pausedUntil", until.toEpochMilli()));
        int runs = 0;
        Cursor runArray = jobObject.setArray("runs");
        JobList jobList = JobList.from(status.values());
        if (type.environment().isTest()) {
            Deque<List<JobType>> pending = new ArrayDeque<>();
            pendingProduction.entrySet().stream()
                             .filter(typeVersions -> jobList.type(type).successOn(typeVersions.getValue()).isEmpty())
                             .filter(typeVersions -> jobList.production().triggeredOn(typeVersions.getValue()).isEmpty())
                             .collect(groupingBy(Map.Entry::getValue,
                                                 LinkedHashMap::new,
                                                 Collectors.mapping(Map.Entry::getKey, toList())))
                             .forEach((versions, types) -> pending.addFirst(types));
            for (List<JobType> productionTypes : pending) {
                Versions versions = pendingProduction.get(productionTypes.get(0));
                if (statusOf(controller, instance.id(), type, versions).equals("running"))
                    continue;

                runs++;
                Cursor runObject = runArray.addObject();
                runObject.setString("status", "pending");
                versionsToSlime(runObject, versions);
                if ( ! controller.applications().deploymentTrigger().triggerAt(controller.clock().instant(), type, status.get(type), versions, instance, application.deploymentSpec()))
                    runObject.setObject("tasks").setString("cooldown", "failed");
                else
                    runObject.setObject("tasks").setString("capacity", "running");

                runObject.setString("reason", "Testing for " + Joiner.on(", ").join(productionTypes));
            }
        }
        else if (     pendingProduction.containsKey(type)
                 && ! running.containsKey(type)) {
            Versions versions = pendingProduction.get(type);
            runs++;
            Cursor runObject = runArray.addObject();
            runObject.setString("status", "pending");
            versionsToSlime(runObject, pendingProduction.get(type));
            Cursor pendingObject = runObject.setObject("tasks");
            if (instance.jobPauses().containsKey(type))
                pendingObject.setString("paused", "pending");
            else if ( ! controller.applications().deploymentTrigger().triggerAt(controller.clock().instant(), type, status.get(type), versions, instance, application.deploymentSpec()))
                pendingObject.setString("cooldown", "failed");
            else {
                int pending = 0;
                controller.applications().deploymentTrigger();
                if (jobList.production().triggeredOn(versions).isEmpty()) {
                    if (jobList.type(systemTest).successOn(versions).isEmpty()) {
                        pending++;
                        pendingObject.setString(shortNameOf(systemTest, controller.system()), statusOf(controller, instance.id(), systemTest, versions));
                    }
                    if (jobList.type(stagingTest).successOn(versions).isEmpty()) {
                        pending++;
                        pendingObject.setString(shortNameOf(stagingTest, controller.system()), statusOf(controller, instance.id(), stagingTest, versions));
                    }
                }
                steps: for (DeploymentSpec.Step step : steps.production()) {
                    if (steps.toJobs(step).contains(type))
                        break;
                    for (JobType stepType : steps.toJobs(step)) {
                        if (pendingProduction.containsKey(stepType)) {
                            Versions jobVersions = Versions.from(instance.change(),
                                                                 application,
                                                                 Optional.ofNullable(instance.deployments().get(stepType.zone(controller.system()))),
                                                                 controller.systemVersion());
                            pendingObject.setString(shortNameOf(stepType, controller.system()), statusOf(controller, instance.id(), stepType, jobVersions));
                            if (++pending == 3)
                                break steps;
                        }
                    }
                }
                if (pending == 0)
                    pendingObject.setString("delay", "running");
            }
        }

        controller.jobController().runs(instance.id(), type).values().stream()
                  .sorted(Comparator.comparing(run -> -run.id().number()))
                  .limit(Math.max(0, 10 - runs))
                  .forEach(run -> runToSlime(runArray.addObject(), run, baseUriForJob));

        jobObject.setString("url", baseUriForJob.toString());
    }

    private static String statusOf(Controller controller, ApplicationId id, JobType type, Versions versions) {
        return controller.jobController().last(id, type)
                         .filter(run -> run.versions().targetsMatch(versions))
                         .filter(run -> type != stagingTest || run.versions().sourcesMatchIfPresent(versions))
                         .map(JobControllerApiHandlerHelper::taskStatusOf)
                         .orElse("pending");
    }

    private static String shortNameOf(JobType type, SystemName system) {
        return type.jobName().replaceFirst("production-", "");
    }

    private static String taskStatusOf(Run run) {
        switch (run.status()) {
            case running: return "running";
            case success: return "succeeded";
            default: return "failed";
        }
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

    private static void applicationVersionToSlime(Cursor versionObject, ApplicationVersion version) {
        versionObject.setString("hash", version.id());
        if (version.isUnknown())
            return;

        versionObject.setLong("build", version.buildNumber().getAsLong());
        Cursor sourceObject = versionObject.setObject("source");
        sourceObject.setString("gitRepository", version.source().get().repository());
        sourceObject.setString("gitBranch", version.source().get().branch());
        sourceObject.setString("gitCommit", version.source().get().commit());
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
        jobController.updateTestLog(runId);
        try { jobController.updateVespaLog(runId); }
        catch (RuntimeException ignored) { } // May be perfectly fine, e.g., when logserver isn't up yet.

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
        });

        return new SlimeJsonResponse(slime);
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
                                       SourceRevision sourceRevision, String authorEmail, Optional<String> sourceUrl,
                                       Optional<String> commit, long projectId,
                                       ApplicationPackage applicationPackage, byte[] testPackage) {
        ApplicationVersion version = jobController.submit(TenantAndApplicationId.from(tenant, application),
                                                          sourceRevision,
                                                          authorEmail,
                                                          sourceUrl,
                                                          commit,
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
            case running:               return "running";
            case aborted:               return "aborted";
            case error:                 return "error";
            case testFailure:           return "testFailure";
            case outOfCapacity:         return "outOfCapacity";
            case installationFailed:    return "installationFailed";
            case deploymentFailed:      return "deploymentFailed";
            case success:               return "success";
            default:                    throw new IllegalArgumentException("Unexpected status '" + status + "'");
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

        Map<JobId, List<Versions>> jobsToRun = status.jobsToRun();
        Cursor stepsArray = responseObject.setArray("steps");
        for (DeploymentStatus.StepStatus stepStatus : status.allSteps()) {
            Change change = status.application().require(stepStatus.instance()).change();
            Cursor stepObject = stepsArray.addObject();
            stepObject.setString("type", stepStatus.type().name());
            stepStatus.dependencies().stream()
                      .map(status.allSteps()::indexOf)
                      .forEach(stepObject.setArray("dependencies")::addLong);
            stepObject.setBool("declared", stepStatus.isDeclared());
            stepObject.setString("instance", stepStatus.instance().value());

            stepStatus.job().ifPresent(job -> {
                stepObject.setString("jobName", job.type().jobName());
                String baseUriForJob = baseUriForDeployments.resolve(baseUriForDeployments.getPath() +
                                                                     "/../instance/" + job.application().instance().value() +
                                                                     "/job/" + job.type().jobName()).normalize().toString();
                stepObject.setString("url", baseUriForJob);
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
                    stepStatus.readyAt(change).ifPresent(ready -> runObject.setLong("readyAt", ready.toEpochMilli()));
                    stepStatus.readyAt(change)
                              .filter(controller.clock().instant()::isBefore)
                              .ifPresent(until -> runObject.setLong("delayedUntil", until.toEpochMilli()));
                    stepStatus.pausedUntil().ifPresent(until -> runObject.setLong("pausedUntil", until.toEpochMilli()));
                    stepStatus.coolingDownUntil(change).ifPresent(until -> runObject.setLong("coolingDownUntil", until.toEpochMilli()));
                    stepStatus.blockedUntil(change).ifPresent(until -> runObject.setLong("blockedUntil", until.toEpochMilli()));
                }

                Cursor runsArray = stepObject.setArray("runs");
                jobStatus.runs().descendingMap().values().stream().limit(10).forEach(run -> {
                    Cursor runObject = runsArray.addObject();
                    runObject.setLong("id", run.id().number());
                    runObject.setString("url", baseUriForJob + "/run/" + run.id());
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
            });
        }

        // TODO jonmv: Add latest platform and application status.

        return new SlimeJsonResponse(slime);
    }

    private static void toSlime(Cursor versionObject, ApplicationVersion version) {
        version.buildNumber().ifPresent(id -> versionObject.setLong("id", id));
        version.source().ifPresent(source -> versionObject.setString("commit", source.commit()));
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

}

