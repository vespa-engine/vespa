// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.DeploymentSpec.Step;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService.JobState;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobReport;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.JobStatus.JobRun;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.controller.api.integration.BuildService.BuildJob;
import static com.yahoo.vespa.hosted.controller.api.integration.BuildService.JobState.idle;
import static com.yahoo.vespa.hosted.controller.api.integration.BuildService.JobState.queued;
import static com.yahoo.vespa.hosted.controller.api.integration.BuildService.JobState.running;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.component;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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
    private final BuildService buildService;
    private final JobController jobs;

    public DeploymentTrigger(Controller controller, BuildService buildService, Clock clock) {
        this.controller = Objects.requireNonNull(controller, "controller cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.buildService = Objects.requireNonNull(buildService, "buildService cannot be null");
        this.jobs = controller.jobController();
    }

    public DeploymentSteps steps(DeploymentSpec spec) {
        return new DeploymentSteps(spec, controller::system);
    }

    /**
     * Records information when a job completes (successfully or not). This information is used when deciding what to
     * trigger next.
     */
    public void notifyOfCompletion(JobReport report) {
        log.log(LogLevel.INFO, String.format("Notified of %s for %s of %s (%d)",
                                             report.jobError().map(e -> e.toString() + " error")
                                                   .orElse("success"),
                                             report.jobType(),
                                             report.applicationId(),
                                             report.projectId()));
        if ( ! applications().get(report.applicationId()).isPresent()) {
            log.log(LogLevel.WARNING, "Ignoring completion of job of project '" + report.projectId() +
                                      "': Unknown application '" + report.applicationId() + "'");
            return;
        }

        applications().lockOrThrow(report.applicationId(), application -> {
            JobRun triggering;
            if (report.jobType() == component) {
                ApplicationVersion applicationVersion = report.version().get();
                triggering = JobRun.triggering(applications().oldestInstalledPlatform(report.applicationId()), applicationVersion,
                                               Optional.empty(), Optional.empty(), "Application commit", clock.instant());
                if (report.success()) {
                    if (acceptNewApplicationVersion(application.get())) {
                        application = application.withChange(application.get().change().with(applicationVersion))
                                                 .withOutstandingChange(Change.empty());
                        if (application.get().deploymentJobs().deployedInternally())
                            for (Run run : jobs.active())
                                if (run.id().application().equals(report.applicationId()))
                                    jobs.abort(run.id());
                    }
                    else
                        application = application.withOutstandingChange(Change.of(applicationVersion));
                }
            }
            else {
                triggering = application.get().deploymentJobs().statusOf(report.jobType())
                                        .filter(job ->    job.lastTriggered().isPresent()
                                                       && job.lastCompleted()
                                                             .map(completion -> ! completion.at().isAfter(job.lastTriggered().get().at()))
                                                             .orElse(true))
                                        .orElseThrow(() -> new IllegalStateException("Notified of completion of " + report.jobType().jobName() + " for " +
                                                                                     report.applicationId() + ", but that has neither been triggered nor deployed"))
                                        .lastTriggered().get();
            }
            application = application.withJobCompletion(report.projectId(),
                                                        report.jobType(),
                                                        triggering.completion(report.buildNumber(), clock.instant()),
                                                        report.jobError());
            application = application.withChange(remainingChange(application.get()));
            applications().store(application);
        });
    }

    /** Returns a map of jobs that are scheduled to be run, grouped by the job type */
    public Map<JobType, ? extends List<? extends BuildJob>> jobsToRun() {
        return computeReadyJobs().stream().collect(groupingBy(Job::jobType));
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
        log.log(LogLevel.INFO, String.format("Triggering %s: %s", job, job.triggering));
        try {
            applications().lockOrThrow(job.applicationId(), application -> {
                if (application.get().deploymentJobs().deployedInternally())
                    jobs.start(job.applicationId(), job.jobType, new Versions(job.triggering.platform(),
                                                                              job.triggering.application(),
                                                                              job.triggering.sourcePlatform(),
                                                                              job.triggering.sourceApplication()));
                else
                    buildService.trigger(job);

                applications().store(application.withJobTriggering(job.jobType, job.triggering));
            });
            return true;
        }
        catch (RuntimeException e) {
            log.log(LogLevel.WARNING, "Exception triggering " + job + ": " + e);
            if (e instanceof NoSuchElementException || e instanceof IllegalArgumentException)
                applications().lockOrThrow(job.applicationId(), application ->
                        applications().store(application.withProjectId(OptionalLong.empty())));
            return false;
        }
    }

    /** Force triggering of a job for given application. */
    public List<JobType> forceTrigger(ApplicationId applicationId, JobType jobType, String user) {
        Application application = applications().require(applicationId);
        if (jobType == component) {
            if (application.deploymentJobs().deployedInternally())
                throw new IllegalArgumentException(applicationId + " has no component job we can trigger.");

            buildService.trigger(BuildJob.of(applicationId, application.deploymentJobs().projectId().getAsLong(), jobType.jobName()));
            return singletonList(component);
        }
        Versions versions = Versions.from(application.change(), application, deploymentFor(application, jobType),
                                          controller.systemVersion());
        String reason = "Job triggered manually by " + user;
        return (jobType.isProduction() && ! isTested(application, versions)
                ? testJobs(application, versions, reason, clock.instant(), __ -> true).stream()
                : Stream.of(deploymentJob(application, versions, application.change(), jobType, reason, clock.instant())))
                .peek(this::trigger)
                .map(Job::jobType).collect(toList());
    }

    /** Prevents jobs of the given type from starting, until the given time. */
    public void pauseJob(ApplicationId id, JobType jobType, Instant until) {
        if (until.isAfter(clock.instant().plus(maxPause)))
            throw new IllegalArgumentException("Pause only allowed for up to " + maxPause);

        applications().lockOrThrow(id, application ->
                applications().store(application.withJobPause(jobType, OptionalLong.of(until.toEpochMilli()))));
    }

    /** Triggers a change of this application, unless it already has a change. */
    public void triggerChange(ApplicationId applicationId, Change change) {
        applications().lockOrThrow(applicationId, application -> {
            if ( ! application.get().change().hasTargets())
                forceChange(applicationId, change);
        });
    }

    /** Overrides the given application's platform and application changes with any contained in the given change. */
    public void forceChange(ApplicationId applicationId, Change change) {
        applications().lockOrThrow(applicationId, application -> {
            if (change.application().isPresent())
                application = application.withOutstandingChange(Change.empty());
            applications().store(application.withChange(change.onTopOf(application.get().change())));
        });
    }

    /** Cancels the indicated part of the given application's change. */
    public void cancelChange(ApplicationId applicationId, ChangesToCancel cancellation) {
        applications().lockOrThrow(applicationId, application -> {
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

    private Optional<JobRun> successOn(Application application, JobType jobType, Versions versions) {
        return application.deploymentJobs().statusOf(jobType).flatMap(JobStatus::lastSuccess)
                          .filter(versions::targetsMatch);
    }

    private Optional<Deployment> deploymentFor(Application application, JobType jobType) {
        return Optional.ofNullable(application.deployments().get(jobType.zone(controller.system())));
    }

    private static <T extends Comparable<T>> Optional<T> max(Optional<T> o1, Optional<T> o2) {
        return ! o1.isPresent() ? o2 : ! o2.isPresent() ? o1 : o1.get().compareTo(o2.get()) >= 0 ? o1 : o2;
    }

    // ---------- Ready job computation ----------

    /** Returns the set of all jobs which have changes to propagate from the upstream steps. */
    private List<Job> computeReadyJobs() {
        return ApplicationList.from(applications().asList())
                              .withProjectId()
                              .withChanges()
                              .idList().stream()
                              .map(this::computeReadyJobs)
                              .flatMap(Collection::stream)
                              .collect(toList());
    }

    /**
     * Finds the next step to trigger for the given application, if any, and returns these as a list.
     */
    private List<Job> computeReadyJobs(ApplicationId id) {
        List<Job> jobs = new ArrayList<>();
        applications().get(id).ifPresent(application -> {
            Change change = application.change();
            Optional<Instant> completedAt = max(application.deploymentJobs().statusOf(systemTest)
                                                        .<Instant>flatMap(job -> job.lastSuccess().map(JobRun::at)),
                                                application.deploymentJobs().statusOf(stagingTest)
                                                        .<Instant>flatMap(job -> job.lastSuccess().map(JobRun::at)));
            String reason = "New change available";
            List<Job> testJobs = null; // null means "uninitialised", while empty means "don't run any jobs".
            DeploymentSteps steps = steps(application.deploymentSpec());

            if (change.hasTargets()) {
                for (Step step : steps.production()) {
                    List<JobType> stepJobs = steps.toJobs(step);
                    List<JobType> remainingJobs = stepJobs.stream().filter(job -> ! isComplete(change, application, job)).collect(toList());
                    if ( ! remainingJobs.isEmpty()) { // Change is incomplete; trigger remaining jobs if ready, or their test jobs if untested.
                        for (JobType job : remainingJobs) {
                            Versions versions = Versions.from(change, application, deploymentFor(application, job),
                                                              controller.systemVersion());
                            if (isTested(application, versions)) {
                                if (completedAt.isPresent() && canTrigger(job, versions, application, stepJobs)) {
                                    jobs.add(deploymentJob(application, versions, change, job, reason, completedAt.get()));
                                }
                                if ( ! alreadyTriggered(application, versions) && testJobs == null) {
                                    testJobs = emptyList();
                                }
                            }
                            else if (testJobs == null) {
                                testJobs = testJobs(application, versions,
                                                    String.format("Testing deployment for %s (%s)",
                                                                  job.jobName(), versions.toString()),
                                                    completedAt.orElseGet(clock::instant));
                            }
                        }
                        completedAt = Optional.empty();
                    }
                    else { // All jobs are complete; find the time of completion of this step.
                        if (stepJobs.isEmpty()) { // No jobs means this is a delay step.
                            Duration delay = ((DeploymentSpec.Delay) step).duration();
                            completedAt = completedAt.map(at -> at.plus(delay)).filter(at -> !at.isAfter(clock.instant()));
                            reason += " after a delay of " + delay;
                        }
                        else {
                            completedAt = stepJobs.stream().map(job -> application.deploymentJobs().statusOf(job).get().lastCompleted().get().at()).max(naturalOrder());
                            reason = "Available change in " + stepJobs.stream().map(JobType::jobName).collect(joining(", "));
                        }
                    }
                }
            }
            if (testJobs == null) { // If nothing to test, but outstanding commits, test those.
                testJobs = testJobs(application, Versions.from(application.outstandingChange().onTopOf(application.change()),
                                                               application,
                                                               steps.sortedDeployments(application.productionDeployments().values()).stream().findFirst(),
                                                               controller.systemVersion()),
                                    "Testing last changes outside prod", clock.instant());
            }
            jobs.addAll(testJobs);
        });
        return Collections.unmodifiableList(jobs);
    }

    /** Returns whether given job should be triggered */
    private boolean canTrigger(JobType job, Versions versions, Application application, List<JobType> parallelJobs) {
        if (jobStateOf(application, job) != idle) return false;

        // Are we already running jobs which are not in the set which can run in parallel with this?
        if (parallelJobs != null && ! parallelJobs.containsAll(runningProductionJobs(application))) return false;

        // Are there another suspended deployment such that we shouldn't simultaneously change this?
        if (job.isProduction() && isSuspendedInAnotherZone(application, job.zone(controller.system()))) return false;

        return triggerAt(clock.instant(), job, versions, application);
    }

    /** Returns whether given job should be triggered */
    private boolean canTrigger(JobType job, Versions versions, Application application) {
        return canTrigger(job, versions, application, null);
    }

    private boolean isSuspendedInAnotherZone(Application application, ZoneId zone) {
        for (Deployment deployment : application.productionDeployments().values()) {
            if (   ! deployment.zone().equals(zone)
                &&   controller.applications().isSuspended(new DeploymentId(application.id(), deployment.zone())))
                return true;
        }
        return false;
    }

    /** Returns whether the given job can trigger at the given instant */
    public boolean triggerAt(Instant instant, JobType job, Versions versions, Application application) {
        Optional<JobStatus> jobStatus = application.deploymentJobs().statusOf(job);
        if ( ! jobStatus.isPresent()) return true;
        if (jobStatus.get().pausedUntil().isPresent() && jobStatus.get().pausedUntil().getAsLong() > clock.instant().toEpochMilli()) return false;
        if (jobStatus.get().isSuccess()) return true; // Success
        if ( ! jobStatus.get().lastCompleted().isPresent()) return true; // Never completed
        if ( ! jobStatus.get().firstFailing().isPresent()) return true; // Should not happen as firstFailing should be set for an unsuccessful job
        if ( ! versions.targetsMatch(jobStatus.get().lastCompleted().get())) return true; // Always trigger as targets have changed
        if (application.deploymentSpec().upgradePolicy() == DeploymentSpec.UpgradePolicy.canary) return true; // Don't throttle canaries

        Instant firstFailing = jobStatus.get().firstFailing().get().at();
        Instant lastCompleted = jobStatus.get().lastCompleted().get().at();

        // Retry all errors immediately for 1 minute
        if (firstFailing.isAfter(instant.minus(Duration.ofMinutes(1)))) return true;

        // Retry out of capacity errors in test environments every minute
        if (job.isTest() && jobStatus.get().isOutOfCapacity()) {
            return lastCompleted.isBefore(instant.minus(Duration.ofMinutes(1)));
        }

        // Retry other errors
        if (firstFailing.isAfter(instant.minus(Duration.ofHours(1)))) { // If we failed within the last hour ...
            return lastCompleted.isBefore(instant.minus(Duration.ofMinutes(10))); // ... retry every 10 minutes
        }
        return lastCompleted.isBefore(instant.minus(Duration.ofHours(2))); // Retry at most every 2 hours
    }

    // ---------- Job state helpers ----------

    private List<JobType> runningProductionJobs(Application application) {
        return application.deploymentJobs().jobStatus().keySet().parallelStream()
                          .filter(JobType::isProduction)
                          .filter(job -> isRunning(application, job))
                          .collect(toList());
    }

    /** Returns whether the given job is currently running; false if completed since last triggered, asking the build service otherwise. */
    private boolean isRunning(Application application, JobType jobType) {
        return    ! application.deploymentJobs().statusOf(jobType)
                               .flatMap(job -> job.lastCompleted().map(run -> run.at().isAfter(job.lastTriggered().get().at())))
                               .orElse(false)
               &&   EnumSet.of(running, queued).contains(jobStateOf(application, jobType));
    }

    private JobState jobStateOf(Application application, JobType jobType) {
        if (application.deploymentJobs().deployedInternally()) {
            Optional<Run> run = controller.jobController().last(application.id(), jobType);
            return run.isPresent() && ! run.get().hasEnded() ? JobState.running : JobState.idle;
        }
        return buildService.stateOf(BuildJob.of(application.id(),
                                                application.deploymentJobs().projectId().getAsLong(),
                                                jobType.jobName()));
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
    public boolean isComplete(Change change, Application application, JobType jobType) {
        Optional<Deployment> existingDeployment = deploymentFor(application, jobType);
        if (     change.isPinned()
            &&   change.platform().isPresent()
            && ! existingDeployment.map(Deployment::version).equals(change.platform()))
            return false;

        return       application.deploymentJobs().statusOf(jobType).flatMap(JobStatus::lastSuccess)
                                .map(job ->    change.platform().map(job.platform()::equals).orElse(true)
                                            && change.application().map(job.application()::equals).orElse(true))
                                .orElse(false)
               ||    jobType.isProduction()
                  && existingDeployment.map(deployment -> ! isUpgrade(change, deployment) && isDowngrade(application.change(), deployment))
                                          .orElse(false);
    }

    private static boolean isUpgrade(Change change, Deployment deployment) {
        return change.upgrades(deployment.version()) || change.upgrades(deployment.applicationVersion());
    }

    private static boolean isDowngrade(Change change, Deployment deployment) {
        return change.downgrades(deployment.version()) || change.downgrades(deployment.applicationVersion());
    }

    private boolean isTested(Application application, Versions versions) {
        return       testedIn(application, systemTest, versions)
                  && testedIn(application, stagingTest, versions)
               || alreadyTriggered(application, versions);
    }

    public boolean testedIn(Application application, JobType testType, Versions versions) {
        if (testType == systemTest)
            return successOn(application, systemTest, versions).isPresent();
        if (testType == stagingTest)
            return successOn(application, stagingTest, versions).filter(versions::sourcesMatchIfPresent).isPresent();
        throw new IllegalArgumentException(testType + " is not a test job!");
    }

    public boolean alreadyTriggered(Application application, Versions versions) {
        return application.deploymentJobs().jobStatus().values().stream()
                          .filter(job -> job.type().isProduction())
                          .anyMatch(job -> job.lastTriggered()
                                              .filter(versions::targetsMatch)
                                              .filter(versions::sourcesMatchIfPresent)
                                              .isPresent());
    }

    // ---------- Change management o_O ----------

    private boolean acceptNewApplicationVersion(Application application) {
        if ( ! application.deploymentSpec().canChangeRevisionAt(clock.instant())) return false;
        if (application.change().application().isPresent()) return true; // Replacing a previous application change is ok.
        if (application.deploymentJobs().hasFailures()) return true; // Allow changes to fix upgrade problems.
        return ! application.change().platform().isPresent();
    }

    private Change remainingChange(Application application) {
        DeploymentSteps steps = steps(application.deploymentSpec());
        List<JobType> jobs = steps.production().isEmpty()
                ? steps.testJobs()
                : steps.productionJobs();

        Change change = application.change();
        if (jobs.stream().allMatch(job -> isComplete(application.change().withoutApplication(), application, job)))
            change = change.withoutPlatform();

        if (jobs.stream().allMatch(job -> isComplete(application.change().withoutPlatform(), application, job)))
            change = change.withoutApplication();

        return change;
    }

    // ---------- Version and job helpers ----------

    /**
     * Returns the list of test jobs that should run now, and that need to succeed on the given versions for it to be considered tested.
     */
    private List<Job> testJobs(Application application, Versions versions, String reason, Instant availableSince) {
        return testJobs(application, versions, reason, availableSince, jobType -> canTrigger(jobType, versions, application));
    }

    /**
     * Returns the list of test jobs that need to succeed on the given versions for it to be considered tested, filtered by the given condition.
     */
    private List<Job> testJobs(Application application, Versions versions, String reason, Instant availableSince, Predicate<JobType> condition) {
        List<Job> jobs = new ArrayList<>();
        for (JobType jobType : steps(application.deploymentSpec()).testJobs()) {
            Optional<JobRun> completion = successOn(application, jobType, versions)
                    .filter(run -> versions.sourcesMatchIfPresent(run) || jobType == systemTest);
            if ( ! completion.isPresent() && condition.test(jobType))
                jobs.add(deploymentJob(application, versions, application.change(), jobType, reason, availableSince));
        }
        return jobs;
    }

    private Job deploymentJob(Application application, Versions versions, Change change, JobType jobType, String reason, Instant availableSince) {
        boolean isRetry = application.deploymentJobs().statusOf(jobType)
                                     .map(JobStatus::isOutOfCapacity)
                                     .orElse(false);
        if (isRetry) reason += "; retrying on out of capacity";

        JobRun triggering = JobRun.triggering(versions.targetPlatform(), versions.targetApplication(),
                                              versions.sourcePlatform(), versions.sourceApplication(),
                                              reason, clock.instant());
        return new Job(application, triggering, jobType, availableSince, isRetry, change.application().isPresent());
    }

    // ---------- Data containers ----------


    private static class Job extends BuildJob {

        private final JobType jobType;
        private final JobRun triggering;
        private final Instant availableSince;
        private final boolean isRetry;
        private final boolean isApplicationUpgrade;

        private Job(Application application, JobRun triggering, JobType jobType, Instant availableSince,
                    boolean isRetry, boolean isApplicationUpgrade) {
            super(application.id(), application.deploymentJobs().projectId().getAsLong(), jobType.jobName());
            this.jobType = jobType;
            this.triggering = triggering;
            this.availableSince = availableSince;
            this.isRetry = isRetry;
            this.isApplicationUpgrade = isApplicationUpgrade;
        }

        JobType jobType() { return jobType; }
        Instant availableSince() { return availableSince; } // TODO jvenstad: This is 95% broken now. Change.at() can restore it.
        boolean isRetry() { return isRetry; }
        boolean applicationUpgrade() { return isApplicationUpgrade; }

    }

}

