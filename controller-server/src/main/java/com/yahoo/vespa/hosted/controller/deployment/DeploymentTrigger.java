// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.text.Text;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toList;

/**
 * Responsible for scheduling deployment jobs in a build system and keeping
 * {@link Instance#change()} in sync with what is scheduled.
 *
 * This class is multi-thread safe.
 *
 * @author bratseth
 * @author mpolden
 * @author jonmv
 */
public class DeploymentTrigger {

    public static final Duration maxPause = Duration.ofDays(3);
    private final static Logger log = Logger.getLogger(DeploymentTrigger.class.getName());

    private final Controller controller;
    private final Clock clock;
    private final JobController jobs;

    public DeploymentTrigger(Controller controller, Clock clock) {
        this.controller = Objects.requireNonNull(controller, "controller cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.jobs = controller.jobController();
    }

    public DeploymentSteps steps(DeploymentInstanceSpec spec) {
        return new DeploymentSteps(spec, controller::system);
    }

    public void notifyOfSubmission(TenantAndApplicationId id, ApplicationVersion version, long projectId) {
        if (applications().getApplication(id).isEmpty()) {
            log.log(Level.WARNING, "Ignoring submission from project '" + projectId +
                                      "': Unknown application '" + id + "'");
            return;
        }

        applications().lockApplicationOrThrow(id, application -> {
            application = application.withProjectId(OptionalLong.of(projectId));
            application = application.withNewSubmission(version);
            applications().store(application);
        });
        triggerNewRevision(id);
    }

    /**
     * Propagates the latest revision to ready instances.
     * Ready instances are those whose dependencies are complete, and which aren't blocked, and, additionally,
     * which aren't upgrading, or are already deploying an application change, or failing upgrade.
     */
    public void triggerNewRevision(TenantAndApplicationId id) {
        applications().lockApplicationIfPresent(id, application -> {
            DeploymentStatus status = jobs.deploymentStatus(application.get());
            for (InstanceName instanceName : application.get().deploymentSpec().instanceNames()) {
                Change outstanding = status.outstandingChange(instanceName);
                if (   outstanding.hasTargets()
                    && status.instanceSteps().get(instanceName)
                             .readyAt(outstanding)
                             .map(readyAt -> ! readyAt.isAfter(clock.instant())).orElse(false)
                    && acceptNewApplicationVersion(status, instanceName)) {
                    application = application.with(instanceName,
                                                   instance -> {
                                                       instance = instance.withChange(instance.change().with(outstanding.application().get()));
                                                       return instance.withChange(remainingChange(instance, status));
                                                   });
                }
            }
            applications().store(application);
        });
    }

    /**
     * Records information when a job completes (successfully or not). This information is used when deciding what to
     * trigger next.
     */
    public void notifyOfCompletion(ApplicationId id) {
        if (applications().getInstance(id).isEmpty()) {
            log.log(Level.WARNING, "Ignoring completion of job of unknown application '" + id + "'");
            return;
        }

        applications().lockApplicationOrThrow(TenantAndApplicationId.from(id), application ->
                applications().store(application.with(id.instance(),
                                                      instance -> instance.withChange(remainingChange(instance, jobs.deploymentStatus(application.get()))))));
    }

    /**
     * Finds and triggers jobs that can and should run but are currently not, and returns the number of triggered jobs.
     *
     * Only one job is triggered each run for test jobs, since their environments have limited capacity.
     */
    public long triggerReadyJobs() {
        return computeReadyJobs().stream()
                                 .collect(partitioningBy(job -> job.jobType().environment().isTest()))
                                 .entrySet().stream()
                                 .flatMap(entry -> (entry.getKey()
                                         // True for capacity constrained zones -- sort by priority and make a task for each job type.
                                         ? entry.getValue().stream()
                                                .sorted(comparing(Job::isRetry)
                                                                .thenComparing(Job::applicationUpgrade)
                                                                .reversed()
                                                                .thenComparing(Job::availableSince))
                                                .collect(groupingBy(Job::jobType))
                                         // False for production jobs -- keep step order and make a task for each application.
                                         : entry.getValue().stream()
                                                .collect(groupingBy(Job::applicationId)))
                                         .values().stream()
                                         .map(jobs -> (Supplier<Long>) jobs.stream()
                                                                           .peek(this::trigger)
                                                                           .limit(entry.getKey() ? 1 : Long.MAX_VALUE)::count))
                                 .parallel().map(Supplier::get).reduce(0L, Long::sum);
    }

    /** Attempts to trigger the given job. */
    public void trigger(Job job) {
        log.log(Level.FINE, () -> "Triggering " + job);
        applications().lockApplicationOrThrow(TenantAndApplicationId.from(job.applicationId()), application -> {
            jobs.start(job.applicationId(), job.jobType, job.versions);
            applications().store(application.with(job.applicationId().instance(), instance ->
                    instance.withJobPause(job.jobType, OptionalLong.empty())));
        });
    }

    /** Force triggering of a job for given instance, with same versions as last run. */
    public JobId reTrigger(ApplicationId applicationId, JobType jobType) {
        Application application = applications().requireApplication(TenantAndApplicationId.from(applicationId));
        Instance instance = application.require(applicationId.instance());
        JobId job = new JobId(instance.id(), jobType);
        JobStatus jobStatus = jobs.jobStatus(new JobId(applicationId, jobType));
        Versions versions = jobStatus.lastTriggered()
                                     .orElseThrow(() -> new IllegalArgumentException(job + " has never been triggered"))
                                     .versions();
        trigger(deploymentJob(instance, versions, jobType, jobStatus, clock.instant()));
        return job;
    }

    /** Force triggering of a job for given instance. */
    public List<JobId> forceTrigger(ApplicationId applicationId, JobType jobType, String user, boolean requireTests) {
        Application application = applications().requireApplication(TenantAndApplicationId.from(applicationId));
        Instance instance = application.require(applicationId.instance());
        JobId job = new JobId(instance.id(), jobType);
        if (job.type().environment().isManuallyDeployed())
            return forceTriggerManualJob(job);

        DeploymentStatus status = jobs.deploymentStatus(application);
        Versions versions = Versions.from(instance.change(), application, status.deploymentFor(job), controller.readSystemVersion());
        Map<JobId, List<Versions>> jobs = status.testJobs(Map.of(job, versions));
        if (jobs.isEmpty() || ! requireTests)
            jobs = Map.of(job, List.of(versions));
        jobs.forEach((jobId, versionsList) -> {
            trigger(deploymentJob(instance, versionsList.get(0), jobId.type(), status.jobs().get(jobId).get(), clock.instant()));
        });
        return List.copyOf(jobs.keySet());
    }

    private List<JobId> forceTriggerManualJob(JobId job) {
        Run last = jobs.last(job).orElseThrow(() -> new IllegalArgumentException(job + " has never been run"));
        Versions target = new Versions(controller.readSystemVersion(),
                                       last.versions().targetApplication(),
                                       Optional.of(last.versions().targetPlatform()),
                                       Optional.of(last.versions().targetApplication()));
        jobs.start(job.application(), job.type(), target, true);
        return List.of(job);
    }

    /** Retrigger job. If the job is already running, it will be canceled, and retrigger enqueued. */
    public Optional<JobId> reTriggerOrAddToQueue(DeploymentId deployment) {
        JobType jobType = JobType.from(controller.system(), deployment.zoneId())
                .orElseThrow(() -> new IllegalArgumentException(Text.format("No job to trigger for (system/zone): %s/%s", controller.system().value(), deployment.zoneId().value())));
        Optional<Run> existingRun = controller.jobController().active(deployment.applicationId()).stream()
                .filter(run -> run.id().type().equals(jobType))
                .findFirst();

        if (existingRun.isPresent()) {
            Run run = existingRun.get();
            try (Lock lock = controller.curator().lockDeploymentRetriggerQueue()) {
                List<RetriggerEntry> retriggerEntries = controller.curator().readRetriggerEntries();
                List<RetriggerEntry> newList = new ArrayList<>(retriggerEntries);
                RetriggerEntry requiredEntry = new RetriggerEntry(new JobId(deployment.applicationId(), jobType), run.id().number() + 1);
                if(newList.stream().noneMatch(entry -> entry.jobId().equals(requiredEntry.jobId()) && entry.requiredRun()>=requiredEntry.requiredRun())) {
                    newList.add(requiredEntry);
                }
                newList = newList.stream()
                        .filter(entry -> !(entry.jobId().equals(requiredEntry.jobId()) && entry.requiredRun() < requiredEntry.requiredRun()))
                        .collect(toList());
                controller.curator().writeRetriggerEntries(newList);
            }
            controller.jobController().abort(run.id());
            return Optional.empty();
        } else {
            return Optional.of(reTrigger(deployment.applicationId(), jobType));
        }
    }

    /** Prevents jobs of the given type from starting, until the given time. */
    public void pauseJob(ApplicationId id, JobType jobType, Instant until) {
        if (until.isAfter(clock.instant().plus(maxPause)))
            throw new IllegalArgumentException("Pause only allowed for up to " + maxPause);

        applications().lockApplicationOrThrow(TenantAndApplicationId.from(id), application ->
                applications().store(application.with(id.instance(),
                                                      instance -> instance.withJobPause(jobType, OptionalLong.of(until.toEpochMilli())))));
    }

    /** Resumes a previously paused job, letting it be triggered normally. */
    public void resumeJob(ApplicationId id, JobType jobType) {
        applications().lockApplicationOrThrow(TenantAndApplicationId.from(id), application ->
                applications().store(application.with(id.instance(),
                                                      instance -> instance.withJobPause(jobType, OptionalLong.empty()))));
    }

    /** Triggers a change of this application, unless it already has a change. */
    public void triggerChange(ApplicationId instanceId, Change change) {
        applications().lockApplicationOrThrow(TenantAndApplicationId.from(instanceId), application -> {
            if ( ! application.get().require(instanceId.instance()).change().hasTargets())
                forceChange(instanceId, change);
        });
    }

    /** Overrides the given instance's platform and application changes with any contained in the given change. */
    public void forceChange(ApplicationId instanceId, Change change) {
        applications().lockApplicationOrThrow(TenantAndApplicationId.from(instanceId), application -> {
            applications().store(application.with(instanceId.instance(),
                                                  instance -> instance.withChange(change.onTopOf(application.get().require(instanceId.instance()).change()))));
        });
    }

    /** Cancels the indicated part of the given application's change. */
    public void cancelChange(ApplicationId instanceId, ChangesToCancel cancellation) {
        applications().lockApplicationOrThrow(TenantAndApplicationId.from(instanceId), application -> {
            Change change;
            switch (cancellation) {
                case ALL: change = Change.empty(); break;
                case VERSIONS: change = Change.empty().withPin(); break;
                case PLATFORM: change = application.get().require(instanceId.instance()).change().withoutPlatform(); break;
                case APPLICATION: change = application.get().require(instanceId.instance()).change().withoutApplication(); break;
                case PIN: change = application.get().require(instanceId.instance()).change().withoutPin(); break;
                default: throw new IllegalArgumentException("Unknown cancellation choice '" + cancellation + "'!");
            }
            applications().store(application.with(instanceId.instance(),
                                                  instance -> instance.withChange(change)));
        });
    }

    public enum ChangesToCancel { ALL, PLATFORM, APPLICATION, VERSIONS, PIN }

    // ---------- Conveniences ----------

    private ApplicationController applications() {
        return controller.applications();
    }

    // ---------- Ready job computation ----------

    /** Returns the set of all jobs which have changes to propagate from the upstream steps. */
    private List<Job> computeReadyJobs() {
        return jobs.deploymentStatuses(ApplicationList.from(applications().readable())
                                                      .withProjectId() // Need to keep this, as we have applications with deployment spec that shouldn't be orchestrated.
                                                      .withDeploymentSpec())
                   .withChanges()
                   .asList().stream()
                   .map(this::computeReadyJobs)
                   .flatMap(Collection::stream)
                   .collect(toList());
    }

    /** Finds the next step to trigger for the given application, if any, and returns these as a list. */
    private List<Job> computeReadyJobs(DeploymentStatus status) {
        List<Job> jobs = new ArrayList<>();
        status.jobsToRun().forEach((job, versionsList) -> {
            for (Versions versions : versionsList)
                status.jobSteps().get(job).readyAt(status.application().require(job.application().instance()).change())
                      .filter(readyAt -> ! clock.instant().isBefore(readyAt))
                      .filter(__ -> ! (job.type().isProduction() && isUnhealthyInAnotherZone(status.application(), job)))
                      .filter(__ -> abortIfRunning(versionsList, status.jobs().get(job).get())) // Abort and trigger this later if running with outdated parameters.
                      .ifPresent(readyAt -> {
                          jobs.add(deploymentJob(status.application().require(job.application().instance()),
                                                 versions,
                                                 job.type(),
                                                 status.instanceJobs(job.application().instance()).get(job.type()),
                                                 readyAt));
                      });
        });
        return Collections.unmodifiableList(jobs);
    }

    /** Returns whether the application is healthy in all other production zones. */
    private boolean isUnhealthyInAnotherZone(Application application, JobId job) {
        for (Deployment deployment : application.require(job.application().instance()).productionDeployments().values()) {
            if (   ! deployment.zone().equals(job.type().zone(controller.system()))
                && ! controller.applications().isHealthy(new DeploymentId(job.application(), deployment.zone())))
                return true;
        }
        return false;
    }

    /** Returns whether the job is not running, and also aborts it if it's running with outdated versions. */
    private boolean abortIfRunning(List<Versions> versionsList, JobStatus status) {
        if ( ! status.isRunning())
            return true;

        Run last = status.lastTriggered().get();
        if (versionsList.stream().noneMatch(versions ->    versions.targetsMatch(last.versions())
                                                        && versions.sourcesMatchIfPresent(last.versions())))
            controller.jobController().abort(last.id());

        return false;
    }

    // ---------- Change management o_O ----------

    private boolean acceptNewApplicationVersion(DeploymentStatus status, InstanceName instance) {
        if (status.application().require(instance).change().application().isPresent()) return true; // Replacing a previous application change is ok.
        if (status.hasFailures()) return true; // Allow changes to fix upgrade problems.
        if (status.application().deploymentSpec().instance(instance) // Leading upgrade allows app change to join in.
                  .map(spec -> spec.upgradeRollout() == DeploymentSpec.UpgradeRollout.leading).orElse(false)) return true;
        return status.application().require(instance).change().platform().isEmpty();
    }

    private Change remainingChange(Instance instance, DeploymentStatus status) {
        Change change = instance.change();
        if (status.jobsToRun(Map.of(instance.name(), instance.change().withoutApplication())).isEmpty())
            change = change.withoutPlatform();
        if (status.jobsToRun(Map.of(instance.name(), instance.change().withoutPlatform())).isEmpty())
            change = change.withoutApplication();
        return change;
    }

    // ---------- Version and job helpers ----------

    private Job deploymentJob(Instance instance, Versions versions, JobType jobType, JobStatus jobStatus, Instant availableSince) {
        return new Job(instance, versions, jobType, availableSince, jobStatus.isOutOfCapacity(), instance.change().application().isPresent());
    }

    // ---------- Data containers ----------


    private static class Job {

        private final ApplicationId instanceId;
        private final JobType jobType;
        private final Versions versions;
        private final Instant availableSince;
        private final boolean isRetry;
        private final boolean isApplicationUpgrade;

        private Job(Instance instance, Versions versions, JobType jobType, Instant availableSince,
                    boolean isRetry, boolean isApplicationUpgrade) {
            this.instanceId = instance.id();
            this.jobType = jobType;
            this.versions = versions;
            this.availableSince = availableSince;
            this.isRetry = isRetry;
            this.isApplicationUpgrade = isApplicationUpgrade;
        }

        ApplicationId applicationId() { return instanceId; }
        JobType jobType() { return jobType; }
        Instant availableSince() { return availableSince; } // TODO jvenstad: This is 95% broken now. Change.at() can restore it.
        boolean isRetry() { return isRetry; }
        boolean applicationUpgrade() { return isApplicationUpgrade; }

        @Override
        public String toString() {
            return jobType + " for " + instanceId +
                   " on (" + versions.targetPlatform() + versions.sourcePlatform().map(version -> " <-- " + version).orElse("") +
                   ", " + versions.targetApplication().id()  + versions.sourceApplication().map(version -> " <-- " + version.id()).orElse("") +
                   "), ready since " + availableSince;
        }

    }

}
