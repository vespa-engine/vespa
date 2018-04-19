// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.LockedApplication;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.controller.api.integration.BuildService.BuildJob;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.component;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.partitioningBy;
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
            ApplicationVersion applicationVersion = report.sourceRevision().map(sr -> ApplicationVersion.from(sr, report.buildNumber()))
                                                          .orElse(ApplicationVersion.unknown);
            application = application.withJobCompletion(report, applicationVersion, clock.instant(), controller);
            application = application.withProjectId(OptionalLong.of(report.projectId()));

            if (report.jobType() == component && report.success()) {
                if (acceptNewApplicationVersion(application))
                    // Note that in case of an ongoing upgrade this may result in both the upgrade and application
                    // change being deployed together
                    application = application.withChange(application.change().with(applicationVersion));
                else
                    application = application.withOutstandingChange(Change.of(applicationVersion));
            }
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
                                                                           .filter(job -> canTrigger(job) && trigger(job))
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
        log.log(LogLevel.INFO, String.format("Attempting to trigger %s, deploying %s: %s (platform: %s, application: %s)", job, job.change, job.reason, job.platformVersion, job.applicationVersion.id()));

        try {
            buildService.trigger(job);
            applications().lockOrThrow(job.applicationId(), application -> applications().store(application.withJobTriggering(
                    job.jobType, new JobRun(-1, job.platformVersion, job.applicationVersion, job.reason, clock.instant()))));
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
                                                                   .map(Change::of)
                                                                   .filter(change -> keepApplicationChange)
                                                                   .orElse(Change.empty())));
        });
    }

    public Map<JobType, ? extends List<? extends BuildJob>> jobsToRun() {
        return computeReadyJobs().collect(groupingBy(Job::jobType));
    }

    /** Returns the set of all jobs which have changes to propagate from the upstream steps. */
    public Stream<Job> computeReadyJobs() {
        return ApplicationList.from(applications().asList())
                              .notPullRequest()
                              .withProjectId()
                              .deploying()
                              .idList().stream()
                              .map(this::computeReadyJobs)
                              .flatMap(List::stream);
    }

    /** Returns whether the given job is currently running; false if completed since last triggered, asking the build service othewise. */
    public boolean isRunning(Application application, JobType jobType) {
        return    ! application.deploymentJobs().statusOf(jobType)
                               .flatMap(job -> job.lastCompleted().map(run -> run.at().isAfter(job.lastTriggered().get().at()))).orElse(false)
               &&   buildService.isRunning(BuildJob.of(application.id(), application.deploymentJobs().projectId().getAsLong(), jobType.jobName()));
    }

    public void forceTrigger(ApplicationId applicationId, JobType jobType) {
        Application application = applications().require(applicationId);
        if (jobType == component)
            buildService.trigger(BuildJob.of(applicationId, application.deploymentJobs().projectId().getAsLong(), jobType.jobName()));
        else
            trigger(deploymentJob(application, jobType, ">:o:< Triggered by force! (-o-) |-o-| (=oo=) ", clock.instant(), Collections.emptySet()));
    }

    private Job deploymentJob(Application application, JobType jobType, String reason, Instant availableSince, Collection<JobType> concurrentlyWith) {
        boolean isRetry = application.deploymentJobs().statusOf(jobType).flatMap(JobStatus::jobError)
                                     .filter(JobError.outOfCapacity::equals).isPresent();
        if (isRetry) reason += "; retrying on out of capacity";

        Change change = application.change();
        Optional<Deployment> deployment = deploymentFor(application, jobType);

        Version platform = max(deployment.map(Deployment::version), change.platform())
                .orElse(application.oldestDeployedPlatform()
                                   .orElse(controller.systemVersion()));

        ApplicationVersion applicationVersion = max(deployment.map(Deployment::applicationVersion), change.application())
                .orElse(application.oldestDeployedApplication()
                                   .orElse(application.deploymentJobs().jobStatus().get(component).lastSuccess().get().applicationVersion()));

        return new Job(application, jobType, reason, availableSince, concurrentlyWith, isRetry, change, platform, applicationVersion);
    }

    private static <T extends Comparable<T>> Optional<T> max(Optional<T> o1, Optional<T> o2) {
        return ! o1.isPresent() ? o2 : ! o2.isPresent() ? o1 : o1.get().compareTo(o2.get()) >= 0 ? o1 : o2;
    }

    /**
     * Finds the next step to trigger for the given application, if any, and returns these as a list.
     */
    private List<Job> computeReadyJobs(ApplicationId id) {
        List<Job> jobs = new ArrayList<>();
        applications().get(id).ifPresent(application -> {
            List<DeploymentSpec.Step> steps = application.deploymentSpec().steps().isEmpty()
                    ? Collections.singletonList(new DeploymentSpec.DeclaredZone(Environment.test))
                    : application.deploymentSpec().steps();

            Optional<Instant> completedAt = Optional.of(clock.instant());
            String reason = "Deploying " + application.change();

            for (DeploymentSpec.Step step : steps) {
                Set<JobType> stepJobs = step.zones().stream().map(order::toJob).collect(toSet());
                Set<JobType> remainingJobs = stepJobs.stream().filter(job -> ! completedAt(application.change(), application, job).isPresent()).collect(toSet());
                if (remainingJobs.isEmpty()) { // All jobs are complete -- find the time of completion of this step.
                    if (stepJobs.isEmpty()) { // No jobs means this is delay step.
                        Duration delay = ((DeploymentSpec.Delay) step).duration();
                        completedAt = completedAt.map(at -> at.plus(delay)).filter(at -> ! at.isAfter(clock.instant()));
                        reason += " after a delay of " + delay;
                    }
                    else {
                        completedAt = stepJobs.stream().map(job -> completedAt(application.change(), application, job).get()).max(naturalOrder());
                        reason = "Available change in " + stepJobs.stream().map(JobType::jobName).collect(joining(", "));
                    }
                }
                else if (completedAt.isPresent()) { // Step not complete, because some jobs remain -- trigger these if the previous step was done.
                    for (JobType job : remainingJobs)
                        jobs.add(deploymentJob(application, job, reason, completedAt.get(), stepJobs));
                    completedAt = Optional.empty();
                    break;
                }
            }
            // TODO jvenstad: Replace with completion of individual parts of Change.
            if (completedAt.isPresent())
                applications().lockIfPresent(id, lockedApplication -> applications().store(lockedApplication.withChange(Change.empty())));
        });
        return jobs;
    }

    /**
     * Returns the instant when the given change is complete for the given application for the given job.
     *
     * Any job is complete if the given change is already successful on that job.
     * A production job is also considered complete if its current change is strictly dominated by what
     * is already deployed in its zone, i.e., no parts of the change are upgrades, and at least one
     * part is a downgrade, regardless of the status of the job.
     */
    private Optional<Instant> completedAt(Change change, Application application, JobType jobType) {
        Optional<Instant> lastSuccess = application.deploymentJobs().successAt(change, jobType);
        if (lastSuccess.isPresent() || ! jobType.isProduction())
            return lastSuccess;

        return deploymentFor(application, jobType)
                .filter(deployment ->    ! (   change.upgrades(deployment.version())
                                            || change.upgrades(deployment.applicationVersion()))
                                      &&   (   change.downgrades(deployment.version())
                                            || change.downgrades(deployment.applicationVersion())))
                .map(Deployment::at);
    }

    private boolean canTrigger(Job job) {
        Application application = applications().require(job.applicationId());
        // TODO jvenstad: Check versions, not change.
        if ( ! application.deploymentJobs().isDeployableTo(job.jobType.environment(), application.change()))
            return false;

        if (isRunning(application, job.jobType))
            return false;

        if ( ! job.jobType.isProduction())
            return true;

        if ( ! job.concurrentlyWith.containsAll(runningProductionJobsFor(application)))
            return false;

        if ( ! application.changeAt(clock.instant()).isPresent())
            return false;

        return true;
    }

    private List<JobType> runningProductionJobsFor(Application application) {
        return application.deploymentJobs().jobStatus().keySet().parallelStream()
                          .filter(job -> job.isProduction())
                          .filter(job -> isRunning(application, job))
                          .collect(Collectors.toList());
    }

    private ApplicationController applications() {
        return controller.applications();
    }

    private boolean acceptNewApplicationVersion(LockedApplication application) {
        if (application.change().application().isPresent()) return true; // More application changes are ok.
        if (application.deploymentJobs().hasFailures()) return true; // Allow changes to fix upgrade problems.
        // Otherwise, allow an application change if not currently upgrading.
        return ! application.changeAt(clock.instant()).platform().isPresent();
    }

    private Optional<Deployment> deploymentFor(Application application, JobType jobType) {
        return Optional.ofNullable(application.deployments().get(jobType.zone(controller.system()).get()));
    }


    private static class Job extends BuildJob {

        private final JobType jobType;
        private final String reason;
        private final Instant availableSince;
        private final Collection<JobType> concurrentlyWith;
        private final boolean isRetry;
        private final boolean isApplicationUpgrade;
        private final Change change;
        private final Version platformVersion;
        private final ApplicationVersion applicationVersion;

        private Job(Application application, JobType jobType, String reason, Instant availableSince, Collection<JobType> concurrentlyWith, boolean isRetry, Change change, Version platformVersion, ApplicationVersion applicationVersion) {
            super(application.id(), application.deploymentJobs().projectId().getAsLong(), jobType.jobName());
            this.jobType = jobType;
            this.availableSince = availableSince;
            this.concurrentlyWith = concurrentlyWith;
            this.reason = reason;
            this.isRetry = isRetry;
            this.isApplicationUpgrade = change.application().isPresent();
            this.change = change;
            this.platformVersion = platformVersion;
            this.applicationVersion = applicationVersion;
        }

        JobType jobType() { return jobType; }
        Instant availableSince() { return availableSince; }
        boolean isRetry() { return isRetry; }
        boolean applicationUpgrade() { return isApplicationUpgrade; }

    }


}

