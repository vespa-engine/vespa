// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.DeploymentSpec.Step;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService.JobState;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobReport;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.JobStatus.JobRun;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

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
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.yahoo.config.provision.Environment.prod;
import static com.yahoo.config.provision.Environment.staging;
import static com.yahoo.config.provision.Environment.test;
import static com.yahoo.vespa.hosted.controller.api.integration.BuildService.BuildJob;
import static com.yahoo.vespa.hosted.controller.api.integration.BuildService.JobState.disabled;
import static com.yahoo.vespa.hosted.controller.api.integration.BuildService.JobState.idle;
import static com.yahoo.vespa.hosted.controller.api.integration.BuildService.JobState.queued;
import static com.yahoo.vespa.hosted.controller.api.integration.BuildService.JobState.running;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.component;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Responsible for scheduling deployment jobs in a build system and keeping
 * {@link Application#change()} in sync with what is scheduled.
 *
 * This class is multi-thread safe.
 *
 * @author bratseth
 * @author mpolden
 * @author jvenstad
 */
public class DeploymentTrigger {

    private final static Logger log = Logger.getLogger(DeploymentTrigger.class.getName());

    private final Controller controller;
    private final Clock clock;
    private final DeploymentOrder order;
    private final BuildService buildService;

    public DeploymentTrigger(Controller controller, CuratorDb curator, BuildService buildService, Clock clock) {
        Objects.requireNonNull(controller, "controller cannot be null");
        Objects.requireNonNull(curator, "curator cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");
        this.controller = controller;
        this.clock = clock;
        this.order = new DeploymentOrder(controller::system);
        this.buildService = buildService;
    }

    public DeploymentOrder deploymentOrder() {
        return order;
    }

    /**
     * Called each time a job completes (successfully or not) to record information used when deciding what to trigger.
     */
    public void notifyOfCompletion(JobReport report) {
        log.log(LogLevel.INFO, String.format("Got notified of %s for %s of %s (%d).",
                                             report.jobError().map(JobError::toString).orElse("success"),
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
                ApplicationVersion applicationVersion = ApplicationVersion.from(report.sourceRevision().get(), report.buildNumber());
                triggering = JobRun.triggering(controller.systemVersion(), applicationVersion, empty(), empty(), "Application commit", clock.instant());
                if (report.success()) {
                    if (acceptNewApplicationVersion(application))
                        application = application.withChange(application.change().with(applicationVersion))
                                                 .withOutstandingChange(Change.empty());
                    else
                        application = application.withOutstandingChange(Change.of(applicationVersion));
                }
            }
            else triggering = application.deploymentJobs().statusOf(report.jobType()).flatMap(JobStatus::lastTriggered)
                    .orElseThrow(() -> new IllegalStateException("Got notified about completion of " + report.jobType().jobName() + " for " +
                                                                 report.applicationId() + ", but that has neither been triggered nor deployed"));
            application = application.withJobCompletion(report.projectId(),
                                                        report.jobType(),
                                                        triggering.completion(report.buildNumber(), clock.instant()),
                                                        report.jobError());

            application = application.withChange(remainingChange(application));

            applications().store(application);
        });
    }

    /**
     * Finds and triggers jobs that can and should run but are currently not, and returns the number of triggered jobs.
     *
     * Only one job is triggered each run for test jobs, since their environments have limited capacity.
     */
    public long triggerReadyJobs() {
        return computeReadyJobs().collect(partitioningBy(job -> job.jobType().isTest()))
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
     * the project id is removed from the application owning the job, to prevent further trigger attemps.
     */
    public boolean trigger(Job job) {
        log.log(LogLevel.INFO, String.format("Attempting to trigger %s: %s (%s)", job, job.reason, job.target));

        try {
            buildService.trigger(job);
            applications().lockOrThrow(job.applicationId(), application -> applications().store(application.withJobTriggering(
                    job.jobType, JobRun.triggering(job.target.targetPlatform, job.target.targetApplication,
                                                   job.target.sourcePlatform, job.target.sourceApplication,
                                                   job.reason, clock.instant()))));
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

    /**
     * Triggers a change of this application
     *
     * @param applicationId the application to trigger
     * @throws IllegalArgumentException if this application already has an ongoing change
     */
    public void triggerChange(ApplicationId applicationId, Change change) {
        applications().lockOrThrow(applicationId, application -> {
            if (application.change().isPresent() && ! application.deploymentJobs().hasFailures())
                throw new IllegalArgumentException("Could not start " + change + " on " + application + ": " +
                                                   application.change() + " is already in progress");
            application = application.withChange(change);
            if (change.application().isPresent())
                application = application.withOutstandingChange(Change.empty());

            applications().store(application);
        });
    }

    /** Cancels a platform upgrade of the given application, and an application upgrade as well if {@code keepApplicationChange}. */
    public void cancelChange(ApplicationId applicationId, boolean keepApplicationChange) {
        applications().lockOrThrow(applicationId, application -> {
            applications().store(application.withChange(application.change().application()
                                                                   .filter(__ -> keepApplicationChange)
                                                                   .map(Change::of)
                                                                   .orElse(Change.empty())));
        });
    }

    public Map<JobType, ? extends List<? extends BuildJob>> jobsToRun() {
        return computeReadyJobs().collect(groupingBy(Job::jobType));
    }

    public List<JobType> forceTrigger(ApplicationId applicationId, JobType jobType) {
        Application application = applications().require(applicationId);
        if (jobType == component) {
            buildService.trigger(BuildJob.of(applicationId, application.deploymentJobs().projectId().getAsLong(), jobType.jobName()));
            return singletonList(component);
        }
        State target = targetFor(application, application.change(), deploymentFor(application, jobType));
        String reason = ">:o:< Triggered by force! (-o-) |-o-| (=oo=)";
        return (jobType.isProduction() && ! isTested(application, target)
                ? testJobsFor(application, target, reason, clock.instant()).stream()
                : Stream.of(deploymentJob(application, target, application.change(), jobType, reason, clock.instant(), emptySet())))
                .peek(this::trigger)
                .map(Job::jobType).collect(toList());
    }

    // Optional<Instant> completedAt(application, step(s?), successcondition, deadlockcondition?)
    //

    /** Returns whether the given job is currently running; false if completed since last triggered, asking the build service otherwise. */
    private boolean isRunning(Application application, JobType jobType) {
        return    ! application.deploymentJobs().statusOf(jobType)
                               .flatMap(job -> job.lastCompleted().map(run -> run.at().isAfter(job.lastTriggered().get().at())))
                               .orElse(false)
               &&   jobStateIsAmong(application, jobType, running, queued);
    }

    private boolean jobStateIsAmong(Application application, JobType jobType, JobState state, JobState... states) {
        return EnumSet.of(state, states).contains(buildService.stateOf(BuildJob.of(application.id(),
                                                                                   application.deploymentJobs().projectId().getAsLong(),
                                                                                   jobType.jobName())));
    }

    private Job deploymentJob(Application application, State target, Change change, JobType jobType, String reason, Instant availableSince, Set<JobType> concurrentlyWith) {
        boolean isRetry = application.deploymentJobs().statusOf(jobType).flatMap(JobStatus::jobError)
                                     .filter(JobError.outOfCapacity::equals).isPresent();
        if (isRetry) reason += "; retrying on out of capacity";

        return new Job(application, target, jobType, reason, availableSince, concurrentlyWith, isRetry, change.application().isPresent());
    }

    private Version targetPlatform(Application application, Change change, Optional<Deployment> deployment) {
        return max(deployment.map(Deployment::version), change.platform())
                .orElse(application.oldestDeployedPlatform()
                                   .orElse(controller.systemVersion()));
    }

    private ApplicationVersion targetApplication(Application application, Change change, Optional<Deployment> deployment) {
        return max(deployment.map(Deployment::applicationVersion), change.application())
                .orElse(application.oldestDeployedApplication()
                                   .orElse(application.deploymentJobs().jobStatus().get(component).lastSuccess().get().application()));
    }

    private static <T extends Comparable<T>> Optional<T> max(Optional<T> o1, Optional<T> o2) {
        return ! o1.isPresent() ? o2 : ! o2.isPresent() ? o1 : o1.get().compareTo(o2.get()) >= 0 ? o1 : o2;
    }

    /** Returns the set of all jobs which have changes to propagate from the upstream steps. */
    private Stream<Job> computeReadyJobs() {
        return ApplicationList.from(applications().asList())
                              .notPullRequest()
                              .withProjectId()
                              .deploying()
                              .idList().stream()
                              .map(this::computeReadyJobs)
                              .flatMap(List::stream);
    }

    /**
     * Finds the next step to trigger for the given application, if any, and returns these as a list.
     */
    private List<Job> computeReadyJobs(ApplicationId id) {
        List<Job> jobs = new ArrayList<>();
        applications().get(id).ifPresent(application -> {
            List<Step> productionSteps = application.deploymentSpec().steps().stream()
                                                    .filter(step -> step.deploysTo(prod) || step.zones().isEmpty())
                                                    .collect(toList());

            Change change = application.changeAt(clock.instant());
            @SuppressWarnings("cast") // Bad compiler!
            Optional<Instant> completedAt = max((Optional<Instant>) application.deploymentJobs().statusOf(systemTest)
                                                                               .flatMap(job -> job.lastSuccess().map(JobRun::at)),
                                                (Optional<Instant>) application.deploymentJobs().statusOf(stagingTest)
                                                                               .flatMap(job -> job.lastSuccess().map(JobRun::at)));
            String reason = "New change available";
            List<Job> testJobs = null;

            if (change.isPresent())
                for (Step step : productionSteps) {
                    Set<JobType> stepJobs = step.zones().stream().map(order::toJob).collect(toSet());
                    Map<Optional<Instant>, List<JobType>> jobsByCompletion = stepJobs.stream().collect(groupingBy(job -> completedAt(change, application, job)));
                    if (jobsByCompletion.containsKey(empty())) { // Step not complete, because some jobs remain -- trigger these if the previous step was done, or theirs tests if needed.
                        for (JobType job : jobsByCompletion.get(empty())) {
                            State target = targetFor(application, change, deploymentFor(application, job));
                            if (isTested(application, target)) {
                                if (completedAt.isPresent())
                                    jobs.add(deploymentJob(application, target, change, job, reason, completedAt.get(), stepJobs));
                            }
                            else if (testJobs == null) {
                                if ( ! alreadyTriggered(application, target)) // TODO jvenstad: This is always true now ...
                                    testJobs = testJobsFor(application, target, "Testing deployment for " + job.jobName(), completedAt.orElse(clock.instant()));
                                else
                                    testJobs = emptyList();
                            }
                        }
                        completedAt = empty();
                    }
                    else { // All jobs are complete -- find the time of completion of this step.
                        if (stepJobs.isEmpty()) { // No jobs means this is delay step.
                            Duration delay = ((DeploymentSpec.Delay) step).duration();
                            completedAt = completedAt.map(at -> at.plus(delay)).filter(at -> ! at.isAfter(clock.instant()));
                            reason += " after a delay of " + delay;
                        }
                        else {
                            completedAt = jobsByCompletion.keySet().stream().map(Optional::get).max(naturalOrder());
                            reason = "Available change in " + stepJobs.stream().map(JobType::jobName).collect(joining(", "));
                        }
                    }
                }
            if (testJobs == null)
                testJobs = testJobsFor(application, targetFor(application, application.change(), empty()), "Testing last changes outside prod", clock.instant());
            jobs.addAll(testJobs);
            jobs.removeIf(job -> ! canTrigger(application, job.jobType, job.concurrentlyWith));
        });
        return jobs;
    }

    private Change remainingChange(Application application) {
        List<JobType> jobs = (application.deploymentSpec().steps().isEmpty()
                ? singletonList(new DeploymentSpec.DeclaredZone(test))
                : application.deploymentSpec().steps()).stream()
                                                       .flatMap(step -> step.zones().stream())
                                                       .map(order::toJob)
                                                       .collect(toList());

        boolean platformComplete = application.change().platform().map(Change::of)
                                              .map(change -> jobs.stream().allMatch(job -> completedAt(change, application, job).isPresent()))
                                              .orElse(false);

        boolean applicationComplete = application.change().application().map(Change::of)
                                                 .map(change -> jobs.stream().allMatch(job -> completedAt(change, application, job).isPresent()))
                                                 .orElse(false);

        Change change = application.change();
        if (platformComplete) change = change.withoutPlatform();
        if (applicationComplete) change = change.withoutApplication();
        return change;
    }

    /**
     * Returns the list of test jobs that should run now, and that need to succeed on the given target for it to be considered tested.
     */
    private List<Job> testJobsFor(Application application, State target, String reason, Instant availableSince) {
        List<Step> steps = application.deploymentSpec().steps();
        if (steps.isEmpty()) steps = singletonList(new DeploymentSpec.DeclaredZone(test));
        List<Job> jobs = new ArrayList<>();
        for (Step step : steps.stream().filter(step -> step.deploysTo(test) || step.deploysTo(staging)).collect(toList())) {
            for (JobType jobType : step.zones().stream().map(order::toJob).collect(toList())) {
                Optional<JobRun> completion = successOn(application, jobType, target)
                        .filter(run -> jobType != stagingTest || sourcesMatchIfPresent(target, run));
                if ( ! completion.isPresent())
                    jobs.add(deploymentJob(application, target, application.change(), jobType, reason, availableSince, emptySet()));
            }
        }
        return jobs;
    }

    private boolean isTested(Application application, State target) {
        return    testedAt(application, target).isPresent()
               || alreadyTriggered(application, target);
    }

    /** If the given state's sources are present and differ from its targets, returns whether they are equal to those of the given job run. */
    private static boolean sourcesMatchIfPresent(State target, JobRun jobRun) {
        return   (   ! target.sourcePlatform.filter(version -> ! version.equals(target.targetPlatform)).isPresent()
                  ||   target.sourcePlatform.equals(jobRun.sourcePlatform()))
              && (   ! target.sourceApplication.filter(version -> ! version.equals(target.targetApplication)).isPresent()
                  ||   target.sourceApplication.equals(jobRun.sourceApplication()));
    }

    private static boolean targetsMatch(State target, JobRun jobRun) {
        return target.targetPlatform.equals(jobRun.platform()) && target.targetApplication.equals(jobRun.application());
    }

    private Optional<Instant> testedAt(Application application, State target) {
        Optional<JobRun> testRun = successOn(application, systemTest, target);
        Optional<JobRun> stagingRun = successOn(application, stagingTest, target)
                .filter(run -> sourcesMatchIfPresent(target, run));
        return max(testRun.map(JobRun::at), stagingRun.map(JobRun::at))
                .filter(__ -> testRun.isPresent() && stagingRun.isPresent());
    }

    private boolean alreadyTriggered(Application application, State target) {
        return application.deploymentJobs().jobStatus().values().stream()
                          .filter(job -> job.type().isProduction())
                          .anyMatch(job -> job.lastTriggered()
                                              .filter(run -> targetsMatch(target, run))
                                              .filter(run -> sourcesMatchIfPresent(target, run))
                                              .isPresent());
    }

    /**
     * Returns the instant when the given change is complete for the given application for the given job.
     *
     * Any job is complete if the given change is already successful on that job.
     * A production job is also considered complete if its current change is strictly dominated by what
     * is already deployed in its zone, i.e., no parts of the change are upgrades, and the full current
     * change for the application downgrades the deployment, which is an acknowledgement that the deployed
     * version is broken somehow, such that the job may be locked in failure until a new version is released.
     */
    private Optional<Instant> completedAt(Change change, Application application, JobType jobType) {
        State target = targetFor(application, change, deploymentFor(application, jobType));
        Optional<JobRun> lastSuccess = successOn(application, jobType, target);
        if (lastSuccess.isPresent() || ! jobType.isProduction())
            return lastSuccess.map(JobRun::at);

        return deploymentFor(application, jobType)
                .filter(deployment ->    ! (   change.upgrades(deployment.version())
                                            || change.upgrades(deployment.applicationVersion()))
                                      &&   (   application.change().downgrades(deployment.version())
                                            || application.change().downgrades(deployment.applicationVersion())))
                .map(Deployment::at);
    }

    private Optional<JobRun> successOn(Application application, JobType jobType, State target) {
        return application.deploymentJobs().statusOf(jobType).flatMap(JobStatus::lastSuccess)
                          .filter(run -> targetsMatch(target, run));
    }

    private boolean canTrigger(Application application, JobType jobType, Set<JobType> concurrentlyWith) {
        return     jobStateIsAmong(application, jobType, idle)
               && (   ! jobType.isProduction()
                   ||   concurrentlyWith.containsAll(runningProductionJobsFor(application)));

    }

    private List<JobType> runningProductionJobsFor(Application application) {
        return application.deploymentJobs().jobStatus().keySet().parallelStream()
                          .filter(job -> job.isProduction())
                          .filter(job -> isRunning(application, job))
                          .collect(toList());
    }

    private ApplicationController applications() {
        return controller.applications();
    }

    private boolean acceptNewApplicationVersion(Application application) {
        if (application.change().application().isPresent()) return true; // More application changes are ok.
        if (application.deploymentJobs().hasFailures()) return true; // Allow changes to fix upgrade problems.
        // Otherwise, allow an application change if not currently upgrading.
        return ! application.changeAt(clock.instant()).platform().isPresent();
    }

    private Optional<Deployment> deploymentFor(Application application, JobType jobType) {
        return Optional.ofNullable(application.deployments().get(jobType.zone(controller.system()).get()));
    }

    private State targetFor(Application application, Change change, Optional<Deployment> deployment) {
        return new State(targetPlatform(application, change, deployment),
                         targetApplication(application, change, deployment),
                         deployment.map(Deployment::version),
                         deployment.map(Deployment::applicationVersion));
    }


    // TODO jvenstad: Trim down once canTrigger is inside computation.
    private static class Job extends BuildJob {

        private final JobType jobType;
        private final String reason;
        private final Instant availableSince;
        private final Set<JobType> concurrentlyWith;
        private final boolean isRetry;
        private final boolean isApplicationUpgrade;
        private final State target;

        private Job(Application application, State target, JobType jobType, String reason, Instant availableSince, Set<JobType> concurrentlyWith, boolean isRetry, boolean isApplicationUpgrade) {
            super(application.id(), application.deploymentJobs().projectId().getAsLong(), jobType.jobName());
            this.jobType = jobType;
            this.availableSince = availableSince;
            this.concurrentlyWith = concurrentlyWith;
            this.reason = reason;
            this.isRetry = isRetry;
            this.isApplicationUpgrade = isApplicationUpgrade;
            this.target = target;
        }

        JobType jobType() { return jobType; }
        Instant availableSince() { return availableSince; } // TODO jvenstad: This is 95% broken now. Change.at() can restore it.
        boolean isRetry() { return isRetry; }
        boolean applicationUpgrade() { return isApplicationUpgrade; }

    }


    private static class State {

        private final Version targetPlatform;
        private final ApplicationVersion targetApplication;
        private final Optional<Version> sourcePlatform;
        private final Optional<ApplicationVersion> sourceApplication;

        public State(Version targetPlatform, ApplicationVersion targetApplication, Optional<Version> sourcePlatform, Optional<ApplicationVersion> sourceApplication) {
            this.targetPlatform = targetPlatform;
            this.targetApplication = targetApplication;
            this.sourcePlatform = sourcePlatform;
            this.sourceApplication = sourceApplication;
        }

        @Override
        public String toString() {
            return String.format("platform %s%s, application %s%s",
                                 targetPlatform,
                                 sourcePlatform.filter(version -> ! version.equals(targetPlatform))
                                               .map(v -> " (from " + v + ")").orElse(""),
                                 targetApplication.id(),
                                 sourceApplication.filter(version -> ! version.equals(targetApplication))
                                                  .map(v -> " (from " + v.id() + ")").orElse(""));
        }

    }

}

