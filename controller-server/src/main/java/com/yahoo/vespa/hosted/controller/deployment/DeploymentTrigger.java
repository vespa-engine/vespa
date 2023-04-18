// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus.DelayCause;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus.Readiness;

import java.math.BigDecimal;
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
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

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
    public static final Duration maxFailingRevisionTime = Duration.ofDays(5);
    private final static Logger log = Logger.getLogger(DeploymentTrigger.class.getName());

    private final Controller controller;
    private final Clock clock;
    private final JobController jobs;

    public DeploymentTrigger(Controller controller, Clock clock) {
        this.controller = Objects.requireNonNull(controller, "controller cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.jobs = controller.jobController();
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
                boolean deployOutstanding =    outstanding.hasTargets()
                                            && status.instanceSteps().get(instanceName)
                                                     .readiness(outstanding).okAt(clock.instant())
                                            && acceptNewRevision(status, instanceName, outstanding.revision().get());
                application = application.with(instanceName,
                                               instance -> withRemainingChange(instance,
                                                                               deployOutstanding ? outstanding.onTopOf(instance.change())
                                                                                                 : instance.change(),
                                                                               status,
                                                                               false));
            }

            // If app has been broken since it was first submitted, and not fixed for a long time, we stop managing it until a new submission comes in.
            if (applicationWasAlwaysBroken(status))
                application = application.withProjectId(OptionalLong.empty());

            applications().store(application);
        });
    }

    private boolean applicationWasAlwaysBroken(DeploymentStatus status) {
        // If application has a production deployment, we cannot forget it.
        if (status.application().instances().values().stream().anyMatch(instance -> ! instance.productionDeployments().isEmpty()))
            return false;

        // Then, we need a job that always failed, and failed on the last revision for at least 30 days.
        RevisionId last = status.application().revisions().last().get().id();
        Instant threshold = clock.instant().minus(Duration.ofDays(30));
        for (JobStatus job : status.jobs().asList())
            for (Run run : job.runs().descendingMap().values())
                if (run.hasEnded() && ! run.hasFailed() || ! run.versions().targetRevision().equals(last)) break;
                else if (run.start().isBefore(threshold)) return true;

        return false;
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

        applications().lockApplicationOrThrow(TenantAndApplicationId.from(id), application -> {
            if (application.get().deploymentSpec().instance(id.instance()).isPresent())
                applications().store(application.with(id.instance(),
                                                      instance -> withRemainingChange(instance,
                                                                                      instance.change(),
                                                                                      jobs.deploymentStatus(application.get()),
                                                                                      true)));
        });
    }

    /**
     * Finds and triggers jobs that can and should run but are currently not, and returns the number of triggered jobs.
     * Only one job per type is triggered each run for test jobs, since their environments have limited capacity.
     */
    public TriggerResult triggerReadyJobs() {
        List<Job> readyJobs = computeReadyJobs();

        var prodJobs = new ArrayList<Job>();
        var testJobs = new ArrayList<Job>();
        for (Job job : readyJobs)
            (job.jobType().isProduction() ? prodJobs : testJobs).add(job);

        // Flat list of prod jobs, grouped by application id, retaining the step order
        List<Job> sortedProdJobs = prodJobs.stream()
                .collect(groupingBy(Job::applicationId))
                .values().stream()
                .flatMap(List::stream)
                .toList();

        // Map of test jobs, a list for each job type. Jobs in each list are sorted by priority.
        Map<JobType, List<Job>> sortedTestJobsByType = testJobs.stream()
                .sorted(comparing(Job::isRetry)
                                .thenComparing(Job::applicationUpgrade)
                                .reversed()
                                .thenComparing(Job::availableSince))
                .collect(groupingBy(Job::jobType));

        // Trigger all prod jobs
        long triggeredJobs = 0;
        long failedJobs = 0;
        for (Job job : sortedProdJobs) {
            if (trigger(job)) ++triggeredJobs;
            else ++failedJobs;
        }

        // Trigger max one test job per type
        for (Collection<Job> jobs: sortedTestJobsByType.values())
            for (Job job : jobs)
                if (trigger(job)) { ++triggeredJobs; break; }
                else ++failedJobs;

        return new TriggerResult(triggeredJobs, failedJobs);
    }

    public record TriggerResult(long triggered, long failed) { }

    /** Attempts to trigger the given job. */
    private boolean trigger(Job job) {
        try {
            trigger(job, null);
            return true;
        }
        catch (Exception e) {
            log.log(Level.WARNING, "Failed triggering " + job.jobType() + " for " + job.instanceId, e);
            return false;
        }
    }

    /** Attempts to trigger the given job. */
    private void trigger(Job job, String reason) {
        log.log(Level.FINE, () -> "Triggering " + job);
        applications().lockApplicationOrThrow(TenantAndApplicationId.from(job.applicationId()), application -> {
            jobs.start(job.applicationId(), job.jobType, job.versions, false, Optional.ofNullable(reason));
            applications().store(application.with(job.applicationId().instance(), instance ->
                    instance.withJobPause(job.jobType, OptionalLong.empty())));
        });
    }

    /** Force triggering of a job for given instance, with same versions as last run. */
    public JobId reTrigger(ApplicationId applicationId, JobType jobType, String reason) {
        Application application = applications().requireApplication(TenantAndApplicationId.from(applicationId));
        Instance instance = application.require(applicationId.instance());
        JobId job = new JobId(instance.id(), jobType);
        JobStatus jobStatus = jobs.jobStatus(new JobId(applicationId, jobType));
        Run last = jobStatus.lastTriggered()
                            .orElseThrow(() -> new IllegalArgumentException(job + " has never been triggered"));
        trigger(deploymentJob(instance, last.versions(), last.id().type(), jobStatus.isNodeAllocationFailure(), clock.instant()), reason);
        return job;
    }

    /** Force triggering of a job for given instance. */
    public List<JobId> forceTrigger(ApplicationId applicationId, JobType jobType, String reason, boolean requireTests,
                                    boolean upgradeRevision, boolean upgradePlatform) {
        Application application = applications().requireApplication(TenantAndApplicationId.from(applicationId));
        Instance instance = application.require(applicationId.instance());
        DeploymentStatus status = jobs.deploymentStatus(application);
        if (jobType.environment().isTest()) {
            CloudName cloud = status.firstDependentProductionJobsWithDeployment(applicationId.instance()).keySet().stream().findFirst()
                                    .orElse(controller.zoneRegistry().systemZone().getCloudName());
            jobType = jobType.isSystemTest() ? JobType.systemTest(controller.zoneRegistry(), cloud)
                                             : JobType.stagingTest(controller.zoneRegistry(), cloud);
        }
        JobId job = new JobId(instance.id(), jobType);
        if (job.type().environment().isManuallyDeployed())
            return forceTriggerManualJob(job, reason);

        Change change = instance.change();
        if ( ! upgradeRevision && change.revision().isPresent()) change = change.withoutApplication();
        if ( ! upgradePlatform && change.platform().isPresent()) change = change.withoutPlatform();
        Versions versions = Versions.from(change, application, status.deploymentFor(job), status.fallbackPlatform(change, job));
        DeploymentStatus.Job toTrigger = new DeploymentStatus.Job(job.type(), versions, new Readiness(controller.clock().instant()), instance.change());
        Map<JobId, List<DeploymentStatus.Job>> testJobs = status.testJobs(Map.of(job, List.of(toTrigger)));

        Map<JobId, List<DeploymentStatus.Job>> jobs = testJobs.isEmpty() || ! requireTests
                                                      ? Map.of(job, List.of(toTrigger))
                                                      : testJobs.entrySet().stream()
                                                                .filter(entry -> controller.jobController().last(entry.getKey()).map(Run::hasEnded).orElse(true))
                                                                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        jobs.forEach((jobId, versionsList) -> {
            trigger(deploymentJob(application.require(job.application().instance()),
                                  versionsList.get(0).versions(),
                                  jobId.type(),
                                  status.jobs().get(jobId).get().isNodeAllocationFailure(),
                                  clock.instant()),
                    reason);
        });
        return List.copyOf(jobs.keySet());
    }

    private List<JobId> forceTriggerManualJob(JobId job, String reason) {
        Run last = jobs.last(job).orElseThrow(() -> new IllegalArgumentException(job + " has never been run"));
        Versions target = new Versions(controller.readSystemVersion(),
                                       last.versions().targetRevision(),
                                       Optional.of(last.versions().targetPlatform()),
                                       Optional.of(last.versions().targetRevision()));
        jobs.start(job.application(), job.type(), target, true, Optional.of(reason));
        return List.of(job);
    }

    /** Retrigger job. If the job is already running, it will be canceled, and retrigger enqueued. */
    public Optional<JobId> reTriggerOrAddToQueue(DeploymentId deployment, String reason) {
        JobType jobType = JobType.deploymentTo(deployment.zoneId());
        Optional<Run> existingRun = controller.jobController().active(deployment.applicationId()).stream()
                .filter(run -> run.id().type().equals(jobType))
                .findFirst();

        if (existingRun.isPresent()) {
            Run run = existingRun.get();
            try (Mutex lock = controller.curator().lockDeploymentRetriggerQueue()) {
                List<RetriggerEntry> retriggerEntries = controller.curator().readRetriggerEntries();
                List<RetriggerEntry> newList = new ArrayList<>(retriggerEntries);
                RetriggerEntry requiredEntry = new RetriggerEntry(new JobId(deployment.applicationId(), jobType), run.id().number() + 1);
                if(newList.stream().noneMatch(entry -> entry.jobId().equals(requiredEntry.jobId()) && entry.requiredRun()>=requiredEntry.requiredRun())) {
                    newList.add(requiredEntry);
                }
                newList = newList.stream()
                        .filter(entry -> !(entry.jobId().equals(requiredEntry.jobId()) && entry.requiredRun() < requiredEntry.requiredRun()))
                        .toList();
                controller.curator().writeRetriggerEntries(newList);
            }
            controller.jobController().abort(run.id(), "force re-triggered");
            return Optional.empty();
        } else {
            return Optional.of(reTrigger(deployment.applicationId(), jobType, reason));
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

    /** Overrides the given instance's platform and application changes with any contained in the given change. */
    public void forceChange(ApplicationId instanceId, Change change) {
        forceChange(instanceId, change, true);
    }

    /** Overrides the given instance's platform and application changes with any contained in the given change. */
    public void forceChange(ApplicationId instanceId, Change change, boolean allowOutdatedPlatform) {
        applications().lockApplicationOrThrow(TenantAndApplicationId.from(instanceId), application -> {
            applications().store(application.with(instanceId.instance(),
                                                  instance -> withRemainingChange(instance,
                                                                                  change.onTopOf(instance.change()),
                                                                                  jobs.deploymentStatus(application.get()),
                                                                                  allowOutdatedPlatform)));
        });
    }

    /** Cancels the indicated part of the given application's change. */
    public void cancelChange(ApplicationId instanceId, ChangesToCancel cancellation) {
        applications().lockApplicationOrThrow(TenantAndApplicationId.from(instanceId), application -> {
            Change change = switch (cancellation) {
                case ALL -> Change.empty();
                case PLATFORM -> application.get().require(instanceId.instance()).change().withoutPlatform();
                case APPLICATION -> application.get().require(instanceId.instance()).change().withoutApplication();
                case PIN -> application.get().require(instanceId.instance()).change().withoutPlatformPin();
                case PLATFORM_PIN -> application.get().require(instanceId.instance()).change().withoutPlatformPin();
                case APPLICATION_PIN -> application.get().require(instanceId.instance()).change().withoutRevisionPin();
            };
            applications().store(application.with(instanceId.instance(),
                                                  instance -> withRemainingChange(instance,
                                                                                  change,
                                                                                  jobs.deploymentStatus(application.get()),
                                                                                  true)));
        });
    }

    public enum ChangesToCancel { ALL, PLATFORM, APPLICATION, PIN, PLATFORM_PIN, APPLICATION_PIN }

    // ---------- Conveniences ----------

    private ApplicationController applications() {
        return controller.applications();
    }

    // ---------- Ready job computation ----------

    /** Returns the set of all jobs which have changes to propagate from the upstream steps. */
    private List<Job> computeReadyJobs() {
        return jobs.deploymentStatuses(ApplicationList.from(applications().readable())
                                                      .withProjectId() // Need to keep this, as we have applications with deployment spec that shouldn't be orchestrated.
                                                      .withJobs())
                   .withChanges()
                   .asList().stream()
                   .filter(status -> ! hasExceededQuota(status.application().id().tenant()))
                   .map(this::computeReadyJobs)
                   .flatMap(Collection::stream)
                   .toList();
    }

    /** Finds the next step to trigger for the given application, if any, and returns these as a list. */
    private List<Job> computeReadyJobs(DeploymentStatus status) {
        List<Job> jobs = new ArrayList<>();
        Map<JobId, List<DeploymentStatus.Job>> jobsToRun = status.jobsToRun();
        jobsToRun.forEach((jobId, jobsList) -> {
            abortIfOutdated(status, jobsToRun, jobId);
            DeploymentStatus.Job job = jobsList.get(0);
            if (     job.readiness().okAt(clock.instant())
                && ! controller.jobController().isDisabled(new JobId(jobId.application(), job.type()))
                && ! (jobId.type().isProduction() && isUnhealthyInAnotherZone(status.application(), jobId))) {
                jobs.add(deploymentJob(status.application().require(jobId.application().instance()),
                                       job.versions(),
                                       job.type(),
                                       status.instanceJobs(jobId.application().instance()).get(jobId.type()).isNodeAllocationFailure(),
                                       job.readiness().at()));
            }
        });
        return Collections.unmodifiableList(jobs);
    }

    private boolean hasExceededQuota(TenantName tenant) {
        return controller.serviceRegistry().billingController().getQuota(tenant).budget().equals(Optional.of(BigDecimal.ZERO));
    }

    /** Returns whether the application is healthy in all other production zones. */
    private boolean isUnhealthyInAnotherZone(Application application, JobId job) {
        for (Deployment deployment : application.require(job.application().instance()).productionDeployments().values()) {
            if (   ! deployment.zone().equals(job.type().zone())
                && ! controller.applications().isHealthy(new DeploymentId(job.application(), deployment.zone())))
                return true;
        }
        return false;
    }

    private void abortIfOutdated(JobStatus job, List<DeploymentStatus.Job> jobs) {
        job.lastTriggered()
           .filter(last -> ! last.hasEnded() && last.reason().isEmpty())
           .ifPresent(last -> {
               if (jobs.stream().noneMatch(versions ->    versions.versions().targetsMatch(last.versions())
                                                       && versions.versions().sourcesMatchIfPresent(last.versions()))) {
                   String blocked = jobs.stream()
                                        .map(scheduled -> scheduled.versions().toString())
                                        .collect(Collectors.joining(", "));
                   log.log(Level.INFO, "Aborting outdated run " + last + ", which is blocking runs: " + blocked);
                   controller.jobController().abort(last.id(), "run no longer scheduled, and is blocking scheduled runs: " + blocked);
               }
           });
    }

    /** Returns whether the job is free to start, and also aborts it if it's running with outdated versions. */
    private void abortIfOutdated(DeploymentStatus status, Map<JobId, List<DeploymentStatus.Job>> jobs, JobId job) {
        Readiness readiness = jobs.get(job).get(0).readiness();
        if (readiness.cause() == DelayCause.running)
            abortIfOutdated(status.jobs().get(job).get(), jobs.get(job));
        if (readiness.cause() == DelayCause.blocked && ! job.type().isTest())
            status.jobs().get(new JobId(job.application(), JobType.productionTestOf(job.type().zone())))
                  .ifPresent(jobStatus -> abortIfOutdated(jobStatus, jobs.get(jobStatus.id())));
    }

    // ---------- Change management o_O ----------

    private boolean acceptNewRevision(DeploymentStatus status, InstanceName instance, RevisionId revision) {
        if (status.application().deploymentSpec().instance(instance).isEmpty()) return false; // Unknown instance.
        if ( ! status.jobs().failingWithBrokenRevisionSince(revision, clock.instant().minus(maxFailingRevisionTime))
                     .isEmpty()) return false; // Don't deploy a broken revision.
        boolean isChangingRevision = status.application().require(instance).change().revision().isPresent();
        DeploymentInstanceSpec spec = status.application().deploymentSpec().requireInstance(instance);
        Predicate<RevisionId> revisionFilter = spec.revisionTarget() == DeploymentSpec.RevisionTarget.next
                                               ? failing -> status.application().require(instance).change().revision().get().compareTo(failing) == 0
                                               : failing -> revision.compareTo(failing) > 0;
        return switch (spec.revisionChange()) {
            case whenClear -> ! isChangingRevision;
            case whenFailing -> ! isChangingRevision || status.hasFailures(revisionFilter);
            case always -> true;
        };
    }

    private Instance withRemainingChange(Instance instance, Change change, DeploymentStatus status, boolean allowOutdatedPlatform) {
        Change remaining = change;
        if (status.hasCompleted(instance.name(), change.withoutApplication()))
            remaining = remaining.withoutPlatform();
        if (status.hasCompleted(instance.name(), change.withoutPlatform()))
            remaining = remaining.withoutApplication();

        return instance.withChange(status.withPermittedPlatform(remaining, instance.name(), allowOutdatedPlatform));
    }

    // ---------- Version and job helpers ----------

    private Job deploymentJob(Instance instance, Versions versions, JobType jobType, boolean isNodeAllocationFailure, Instant availableSince) {
        return new Job(instance, versions, jobType, availableSince, isNodeAllocationFailure, instance.change().revision().isPresent());
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
                   ", " + versions.targetRevision() + versions.sourceRevision().map(version -> " <-- " + version).orElse("") +
                   "), ready since " + availableSince;
        }

    }

}
