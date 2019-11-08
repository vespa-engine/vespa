// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.DeploymentSpec.Step;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.log.LogLevel;
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
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobReport;
import com.yahoo.vespa.hosted.controller.application.JobStatus.JobRun;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toList;

/**
 * Responsible for scheduling deployment jobs in a build system and keeping
 * {@link Application#change()} in sync with what is scheduled.
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
            log.log(LogLevel.WARNING, "Ignoring submission from project '" + projectId +
                                      "': Unknown application '" + id + "'");
            return;
        }

        applications().lockApplicationOrThrow(id, application -> {
            if (acceptNewApplicationVersion(application.get())) {
                application = application.withChange(application.get().change().with(version))
                                         .withOutstandingChange(Change.empty());
                for (Run run : jobs.active(id))
                    if ( ! run.id().type().environment().isManuallyDeployed())
                        jobs.abort(run.id());
            }
            else
                application = application.withOutstandingChange(Change.of(version));

            application = application.withProjectId(OptionalLong.of(projectId));
            application = application.withNewSubmission(version);
            applications().store(application.withChange(remainingChange(application.get())));
        });
    }

    /**
     * Records information when a job completes (successfully or not). This information is used when deciding what to
     * trigger next.
     */
    public void notifyOfCompletion(JobReport report) {
        log.log(LogLevel.DEBUG, String.format("Notified of %s for %s of %s (%d)",
                                             report.jobError().map(e -> e.toString() + " error")
                                                   .orElse("success"),
                                             report.jobType(),
                                             report.applicationId(),
                                             report.projectId()));
        if (applications().getInstance(report.applicationId()).isEmpty()) {
            log.log(LogLevel.WARNING, "Ignoring completion of job of project '" + report.projectId() +
                                      "': Unknown application '" + report.applicationId() + "'");
            return;
        }

        applications().lockApplicationOrThrow(TenantAndApplicationId.from(report.applicationId()), application -> {
            var status = application.get().require(report.applicationId().instance())
                                    .deploymentJobs().statusOf(report.jobType());
            var triggering = status.filter(job -> job.lastTriggered().isPresent()
                                                  && job.lastCompleted()
                                                        .map(completion -> ! completion.at().isAfter(job.lastTriggered().get().at()))
                                                        .orElse(true))
                                   .orElseThrow(() -> new IllegalStateException("Notified of completion of " + report.jobType().jobName() + " for " +
                                                                                report.applicationId() + ", but that has not been triggered; last was " +
                                                                                status.flatMap(job -> job.lastTriggered().map(run -> run.at().toString()))
                                                                                      .orElse("never")))
                                   .lastTriggered().get();

            application = application.with(report.applicationId().instance(),
                                           instance -> instance.withJobCompletion(report.jobType(),
                                                                                  triggering.completion(report.buildNumber(), clock.instant()),
                                                                                  report.jobError()));
            applications().store(application.withChange(remainingChange(application.get())));
        });
    }

    /**
     * Finds and triggers jobs that can and should run but are currently not, and returns the number of triggered jobs.
     *
     * Only one job is triggered each run for test jobs, since their environments have limited capacity.
     */
    public long triggerReadyJobs() {
        return computeReadyJobs().stream()
                                 .collect(partitioningBy(job -> job.jobType().isTest()))
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
                                                                           .filter(this::trigger)
                                                                           .limit(entry.getKey() ? 1 : Long.MAX_VALUE)::count))
                                 .parallel().map(Supplier::get).reduce(0L, Long::sum);
    }

    /**
     * Attempts to trigger the given job for the given application and returns the outcome.
     *
     * If the build service can not find the given job, or claims it is illegal to trigger it,
     * the project id is removed from the application owning the job, to prevent further trigger attempts.
     */
    public boolean trigger(Job job) {
        log.log(LogLevel.DEBUG, String.format("Triggering %s: %s", job, job.triggering));
        try {
            applications().lockApplicationOrThrow(TenantAndApplicationId.from(job.applicationId()), application -> {
                jobs.start(job.applicationId(), job.jobType, new Versions(job.triggering.platform(),
                                                                          job.triggering.application(),
                                                                          job.triggering.sourcePlatform(),
                                                                          job.triggering.sourceApplication()));

                applications().store(application.with(job.applicationId().instance(),
                                                      instance -> instance.withJobTriggering(job.jobType, job.triggering)));
            });
            return true;
        }
        catch (RuntimeException e) {
            log.log(LogLevel.WARNING, "Exception triggering " + job + ": " + e);
            if (e instanceof NoSuchElementException || e instanceof IllegalArgumentException)
                applications().lockApplicationOrThrow(TenantAndApplicationId.from(job.applicationId()), application ->
                        applications().store(application.withProjectId(OptionalLong.empty())));
            return false;
        }
    }

    /** Force triggering of a job for given instance. */
    public List<JobType> forceTrigger(ApplicationId applicationId, JobType jobType, String user) {
        Application application = applications().requireApplication(TenantAndApplicationId.from(applicationId));
        Instance instance = application.require(applicationId.instance());
        Versions versions = Versions.from(application.change(), application, deploymentFor(instance, jobType),
                                          controller.systemVersion());
        String reason = "Job triggered manually by " + user;
        var jobStatus = jobs.deploymentStatus(application).instanceJobs(instance.name());
        var jobList = JobList.from(jobStatus.values());
        return (jobType.isProduction() && ! isTested(jobList, versions)
                ? testJobs(application.deploymentSpec(), application.change(), instance, jobList, versions, reason, clock.instant(), __ -> true).stream()
                : Stream.of(deploymentJob(instance, versions, application.change(), jobType, jobStatus.get(jobType), reason, clock.instant())))
                .peek(this::trigger)
                .map(Job::jobType).collect(toList());
    }

    /** Prevents jobs of the given type from starting, until the given time. */
    public void pauseJob(ApplicationId id, JobType jobType, Instant until) {
        if (until.isAfter(clock.instant().plus(maxPause)))
            throw new IllegalArgumentException("Pause only allowed for up to " + maxPause);

        applications().lockApplicationOrThrow(TenantAndApplicationId.from(id), application ->
                applications().store(application.with(id.instance(),
                                                      instance -> instance.withJobPause(jobType, OptionalLong.of(until.toEpochMilli())))));
    }

    /** Triggers a change of this application, unless it already has a change. */
    public void triggerChange(TenantAndApplicationId applicationId, Change change) {
        applications().lockApplicationOrThrow(applicationId, application -> {
            if ( ! application.get().change().hasTargets())
                forceChange(applicationId, change);
        });
    }

    /** Overrides the given application's platform and application changes with any contained in the given change. */
    public void forceChange(TenantAndApplicationId applicationId, Change change) {
        applications().lockApplicationOrThrow(applicationId, application -> {
            if (change.application().isPresent())
                application = application.withOutstandingChange(Change.empty());
            applications().store(application.withChange(change.onTopOf(application.get().change())));
        });
    }

    /** Cancels the indicated part of the given application's change. */
    public void cancelChange(TenantAndApplicationId applicationId, ChangesToCancel cancellation) {
        applications().lockApplicationOrThrow(applicationId, application -> {
            Change change;
            switch (cancellation) {
                case ALL: change = Change.empty(); break;
                case VERSIONS: change = Change.empty().withPin(); break;
                case PLATFORM: change = application.get().change().withoutPlatform(); break;
                case APPLICATION: change = application.get().change().withoutApplication(); break;
                case PIN: change = application.get().change().withoutPin(); break;
                default: throw new IllegalArgumentException("Unknown cancellation choice '" + cancellation + "'!");
            }
            applications().store(application.withChange(change));
        });
    }

    public enum ChangesToCancel { ALL, PLATFORM, APPLICATION, VERSIONS, PIN }

    // ---------- Conveniences ----------

    private ApplicationController applications() {
        return controller.applications();
    }

    private Optional<Run> successOn(JobStatus status, Versions versions) {
        return status.lastSuccess().filter(run -> versions.targetsMatch(run.versions()));
    }

    private Optional<Deployment> deploymentFor(Instance instance, JobType jobType) {
        return Optional.ofNullable(instance.deployments().get(jobType.zone(controller.system())));
    }

    private static <T extends Comparable<T>> Optional<T> max(Optional<T> o1, Optional<T> o2) {
        return o1.isEmpty() ? o2 : o2.isEmpty() ? o1 : o1.get().compareTo(o2.get()) >= 0 ? o1 : o2;
    }

    // ---------- Ready job computation ----------

    /** Returns the set of all jobs which have changes to propagate from the upstream steps. */
    private List<Job> computeReadyJobs() {
        return ApplicationList.from(applications().asList())
                              .withProjectId() // Need to keep this, as we have applications with deployment spec that shouldn't be orchestrated.
                              .withChanges()
                              .withDeploymentSpec()
                              .idList().stream()
                              .map(this::computeReadyJobs)
                              .flatMap(Collection::stream)
                              .collect(toList());
    }

    /**
     * Finds the next step to trigger for the given application, if any, and returns these as a list.
     */
    private List<Job> computeReadyJobs(TenantAndApplicationId id) {
        List<Job> jobs = new ArrayList<>();
        applications().getApplication(id).ifPresent(application -> {
            Collection<Instance> instances = application.deploymentSpec().instances().stream()
                                                        .flatMap(instance -> application.get(instance.name()).stream())
                                                        .collect(Collectors.toUnmodifiableList());
            DeploymentStatus deploymentStatus = this.jobs.deploymentStatus(application);
            for (Instance instance : instances) {
                var jobStatus = deploymentStatus.instanceJobs(instance.name());
                var jobList = JobList.from(jobStatus.values());
                Change change = application.change();
                Optional<Instant> completedAt = max(jobList.type(systemTest).first()
                                                            .<Instant>flatMap(job -> job.lastSuccess().map(run -> run.end().get())),
                                                    jobList.type(stagingTest).first()
                                                            .<Instant>flatMap(job -> job.lastSuccess().map(run -> run.end().get())));
                String reason = "New change available";
                List<Job> testJobs = null; // null means "uninitialised", while empty means "don't run any jobs".
                DeploymentSteps steps = steps(application.deploymentSpec().requireInstance(instance.name()));

                if (change.hasTargets()) {
                    for (Step step : steps.production()) {
                        List<JobType> stepJobs = steps.toJobs(step);
                        List<JobType> remainingJobs = stepJobs.stream().filter(job -> ! isComplete(change, change, instance, job, jobStatus.get(job))).collect(toList());
                        if ( ! remainingJobs.isEmpty()) { // Change is incomplete; trigger remaining jobs if ready, or their test jobs if untested.
                            for (JobType job : remainingJobs) {
                                Versions versions = Versions.from(change, application, deploymentFor(instance, job),
                                                                  controller.systemVersion());
                                if (isTested(jobList, versions)) {
                                    if (completedAt.isPresent() && canTrigger(job, jobList, versions, instance, application.deploymentSpec(), stepJobs)) {
                                        jobs.add(deploymentJob(instance, versions, change, job, jobStatus.get(job), reason, completedAt.get()));
                                    }
                                }
                                else if (testJobs == null) {
                                    testJobs = testJobs(application.deploymentSpec(),
                                                        change, instance, jobList, versions,
                                                        String.format("Testing deployment for %s (%s)",
                                                                      job.jobName(), versions.toString()),
                                                        completedAt.orElseGet(clock::instant));
                                }
                            }
                            completedAt = Optional.empty();
                        }
                        else { // All jobs are complete; find the time of completion of this step.
                            if (stepJobs.isEmpty()) { // No jobs means this is a delay step.
                                completedAt = completedAt.map(at -> at.plus(step.delay())).filter(at -> ! at.isAfter(clock.instant()));
                                reason += " after a delay of " + step.delay();
                            }
                            else {
                                completedAt = stepJobs.stream().map(job -> jobStatus.get(job).lastCompleted().get().end().get()).max(naturalOrder());
                                reason = "Available change in " + stepJobs.stream().map(JobType::jobName).collect(joining(", "));
                            }
                        }
                    }
                }
                if (testJobs == null) { // If nothing to test, but outstanding commits, test those.
                    testJobs = testJobs(application.deploymentSpec(), change, instance, jobList,
                                        Versions.from(application.outstandingChange().onTopOf(change),
                                                      application,
                                                      steps.sortedDeployments(instance.productionDeployments().values()).stream().findFirst(),
                                                      controller.systemVersion()),
                                        "Testing last changes outside prod", clock.instant());
                }
                jobs.addAll(testJobs);
            }
        });
        return Collections.unmodifiableList(jobs);
    }

    /** Returns whether given job should be triggered */
    private boolean canTrigger(JobType type, JobList jobList, Versions versions, Instance instance, DeploymentSpec deploymentSpec, List<JobType> parallelJobs) {
        if ( ! jobList.type(type).running().isEmpty()) return false;

        // Are we already running jobs which are not in the set which can run in parallel with this?
        if (     parallelJobs != null
            && ! parallelJobs.containsAll(jobList.running().production().mapToList(job -> job.id().type()))) return false;

        // Are there another suspended deployment such that we shouldn't simultaneously change this?
        if (type.isProduction() && isSuspendedInAnotherZone(instance, type.zone(controller.system()))) return false;

        return triggerAt(clock.instant(), type, jobList.type(type).first().get(), versions, instance, deploymentSpec);
    }

    /** Returns whether given job should be triggered */
    private boolean canTrigger(JobType job, JobList jobList, Versions versions, Instance instance, DeploymentSpec deploymentSpec) {
        return canTrigger(job, jobList, versions, instance, deploymentSpec, null);
    }

    private boolean isSuspendedInAnotherZone(Instance instance, ZoneId zone) {
        for (Deployment deployment : instance.productionDeployments().values()) {
            if (   ! deployment.zone().equals(zone)
                &&   controller.applications().isSuspended(new DeploymentId(instance.id(), deployment.zone())))
                return true;
        }
        return false;
    }

    /** Returns whether the given job can trigger at the given instant */
    public boolean triggerAt(Instant instant, JobType job, JobStatus jobStatus, Versions versions, Instance instance, DeploymentSpec deploymentSpec) {
        if (instance.deploymentJobs().statusOf(job).map(status -> status.pausedUntil().orElse(0)).orElse(0L) > clock.millis()) return false;
        if (jobStatus.lastTriggered().isEmpty()) return true;
        if (jobStatus.isSuccess()) return true; // Success
        if (jobStatus.lastCompleted().isEmpty()) return true; // Never completed
        if (jobStatus.firstFailing().isEmpty()) return true; // Should not happen as firstFailing should be set for an unsuccessful job
        if ( ! versions.targetsMatch(jobStatus.lastCompleted().get().versions())) return true; // Always trigger as targets have changed
        if (deploymentSpec.requireInstance(instance.name()).upgradePolicy() == DeploymentSpec.UpgradePolicy.canary) return true; // Don't throttle canaries

        Instant firstFailing = jobStatus.firstFailing().get().end().get();
        Instant lastCompleted = jobStatus.lastCompleted().get().end().get();

        // Retry all errors immediately for 1 minute
        if (firstFailing.isAfter(instant.minus(Duration.ofMinutes(1)))) return true;

        // Retry out of capacity errors in test environments every minute
        if (job.isTest() && jobStatus.isOutOfCapacity()) {
            return lastCompleted.isBefore(instant.minus(Duration.ofMinutes(1)));
        }

        // Retry other errors
        if (firstFailing.isAfter(instant.minus(Duration.ofHours(1)))) { // If we failed within the last hour ...
            return lastCompleted.isBefore(instant.minus(Duration.ofMinutes(10))); // ... retry every 10 minutes
        }
        return lastCompleted.isBefore(instant.minus(Duration.ofHours(2))); // Retry at most every 2 hours
    }

    // ---------- Job state helpers ----------

    private List<JobType> runningProductionJobs(Map<JobType, JobStatus> status) {
        return status.values().parallelStream()
                     .filter(job -> job.isRunning())
                     .map(job -> job.id().type())
                     .filter(JobType::isProduction)
                     .collect(toList());
    }

    // ---------- Completion logic ----------

    /**
     * Returns whether the given change is complete for the given application for the given job.
     *
     * Any job is complete if the given change is already successful on that job.
     * A production job is also considered complete if its current change is strictly dominated by what
     * is already deployed in its zone, i.e., no parts of the change are upgrades, and the full current
     * change for the application downgrades the deployment, which is an acknowledgement that the deployed
     * version is broken somehow, such that the job may be locked in failure until a new version is released.
     *
     * Additionally, if the application is pinned to a Vespa version, and the given change has a (this) platform,
     * the deployment for the job must be on the pinned version.
     */
    public boolean isComplete(Change change, Change fullChange, Instance instance, JobType jobType,
                              JobStatus status) {
        Optional<Deployment> existingDeployment = deploymentFor(instance, jobType);
        if (     change.isPinned()
            &&   change.platform().isPresent()
            && ! existingDeployment.map(Deployment::version).equals(change.platform()))
            return false;

        return status.lastSuccess()
                     .map(run ->    change.platform().map(run.versions().targetPlatform()::equals).orElse(true)
                                 && change.application().map(run.versions().targetApplication()::equals).orElse(true))
                     .orElse(false)
               ||    jobType.isProduction()
                  && existingDeployment.map(deployment -> ! isUpgrade(change, deployment) && isDowngrade(fullChange, deployment))
                                          .orElse(false);
    }

    private static boolean isUpgrade(Change change, Deployment deployment) {
        return change.upgrades(deployment.version()) || change.upgrades(deployment.applicationVersion());
    }

    private static boolean isDowngrade(Change change, Deployment deployment) {
        return change.downgrades(deployment.version()) || change.downgrades(deployment.applicationVersion());
    }

    public boolean isTested(JobList jobs, Versions versions) {
        return    ! jobs.type(systemTest).successOn(versions).isEmpty()
               && ! jobs.type(stagingTest).successOn(versions).isEmpty()
               || ! jobs.production().triggeredOn(versions).isEmpty();
    }

    // ---------- Change management o_O ----------

    private boolean acceptNewApplicationVersion(Application application) {
        if ( ! application.deploymentSpec().instances().stream()
                          .allMatch(instance -> instance.canChangeRevisionAt(clock.instant()))) return false;
        if (application.change().application().isPresent()) return true; // Replacing a previous application change is ok.
        for (Instance instance : application.instances().values())
            if (instance.deploymentJobs().hasFailures()) return true; // Allow changes to fix upgrade problems.
        return application.change().platform().isEmpty();
    }

    private Change remainingChange(Application application) {
        Change change = application.change();
        if (application.deploymentSpec().instances().stream()
                       .allMatch(spec -> {
                           DeploymentSteps steps = new DeploymentSteps(spec, controller::system);
                           return (steps.productionJobs().isEmpty() ? steps.testJobs() : steps.productionJobs())
                                   .stream().allMatch(job -> isComplete(application.change().withoutApplication(), application.change(), application.require(spec.name()), job, jobs.jobStatus(new JobId(application.id().instance(spec.name()), job))));
                       }))
            change = change.withoutPlatform();

        if (application.deploymentSpec().instances().stream()
                       .allMatch(spec -> {
                           DeploymentSteps steps = new DeploymentSteps(spec, controller::system);
                           return (steps.productionJobs().isEmpty() ? steps.testJobs() : steps.productionJobs())
                                   .stream().allMatch(job -> isComplete(application.change().withoutPlatform(), application.change(), application.require(spec.name()), job, jobs.jobStatus(new JobId(application.id().instance(spec.name()), job))));
                       }))
            change = change.withoutApplication();

        return change;
    }

    // ---------- Version and job helpers ----------

    /**
     * Returns the list of test jobs that should run now, and that need to succeed on the given versions for it to be considered tested.
     */
    private List<Job> testJobs(DeploymentSpec deploymentSpec, Change change, Instance instance, JobList jobList, Versions versions,
                               String reason, Instant availableSince) {
        return testJobs(deploymentSpec, change, instance, jobList, versions, reason, availableSince,
                        jobType -> canTrigger(jobType, jobList, versions, instance, deploymentSpec));
    }

    /**
     * Returns the list of test jobs that need to succeed on the given versions for it to be considered tested, filtered by the given condition.
     */
    private List<Job> testJobs(DeploymentSpec deploymentSpec, Change change, Instance instance, JobList jobList, Versions versions,
                               String reason, Instant availableSince, Predicate<JobType> condition) {
        List<Job> jobs = new ArrayList<>();
        for (JobType jobType : new DeploymentSteps(deploymentSpec.requireInstance(instance.name()), controller::system).testJobs()) { // TODO jonmv: Allow cross-instance validation
            if (   jobList.type(jobType).successOn(versions).isEmpty()
                && condition.test(jobType))
                jobs.add(deploymentJob(instance, versions, change, jobType, jobList.type(jobType).first().get(), reason, availableSince));
        }
        return jobs;
    }

    private Job deploymentJob(Instance instance, Versions versions, Change change, JobType jobType, JobStatus jobStatus, String reason, Instant availableSince) {
        if (jobStatus.isOutOfCapacity()) reason += "; retrying on out of capacity";

        var triggering = JobRun.triggering(versions.targetPlatform(), versions.targetApplication(),
                                           versions.sourcePlatform(), versions.sourceApplication(),
                                           reason, clock.instant());
        return new Job(instance, triggering, jobType, availableSince, jobStatus.isOutOfCapacity(), change.application().isPresent());
    }

    // ---------- Data containers ----------


    private static class Job {

        private final ApplicationId instanceId;
        private final JobType jobType;
        private final JobRun triggering;
        private final Instant availableSince;
        private final boolean isRetry;
        private final boolean isApplicationUpgrade;

        private Job(Instance instance, JobRun triggering, JobType jobType, Instant availableSince,
                    boolean isRetry, boolean isApplicationUpgrade) {
            this.instanceId = instance.id();
            this.jobType = jobType;
            this.triggering = triggering;
            this.availableSince = availableSince;
            this.isRetry = isRetry;
            this.isApplicationUpgrade = isApplicationUpgrade;
        }

        ApplicationId applicationId() { return instanceId; }
        JobType jobType() { return jobType; }
        Instant availableSince() { return availableSince; } // TODO jvenstad: This is 95% broken now. Change.at() can restore it.
        boolean isRetry() { return isRetry; }
        boolean applicationUpgrade() { return isApplicationUpgrade; }

    }

}

