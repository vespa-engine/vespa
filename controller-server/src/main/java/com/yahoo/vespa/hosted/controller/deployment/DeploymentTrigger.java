// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.SystemName;
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
import com.yahoo.vespa.hosted.controller.application.JobList;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

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

    /**
     * The max duration a job may run before we consider it dead/hanging
     */
    private final Duration jobTimeout;

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
        this.jobTimeout = controller.system().equals(SystemName.main) ? Duration.ofHours(12) : Duration.ofHours(1);
    }

    /**
     * Returns the time in the past before which jobs are at this moment considered unresponsive
     */
    public Instant jobTimeoutLimit() {
        return clock.instant().minus(jobTimeout);
    }

    public DeploymentOrder deploymentOrder() {
        return order;
    }

    //--- Start of methods which triggers deployment jobs -------------------------

    /**
     * Called each time a job completes (successfully or not) to record information used when deciding what to trigger.
     */
    public void notifyOfCompletion(JobReport report) {
        if ( ! applications().get(report.applicationId()).isPresent()) {
            log.log(LogLevel.WARNING, "Ignoring completion of job of project '" + report.projectId() +
                                      "': Unknown application '" + report.applicationId() + "'");
            return;
        }

        applications().lockOrThrow(report.applicationId(), application -> {
            ApplicationVersion applicationVersion = report.sourceRevision().map(sr -> ApplicationVersion.from(sr, report.buildNumber()))
                                                          .orElse(ApplicationVersion.unknown);
            application = application.withJobCompletion(report, applicationVersion, clock.instant(), controller);
            application = application.withProjectId(report.projectId());

            if (report.jobType() == JobType.component && report.success()) {
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
     * Finds and triggers jobs that can and should run but are currently not.
     *
     * Only one job is triggered each run for test jobs, since those environments have limited capacity.
     */
    public void triggerReadyJobs() {
        computeReadyJobs().collect(partitioningBy(job -> job.jobType().isTest()))
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
                                         .collect(groupingBy(Job::id)))
                                  .values().stream()
                                  .map(jobs -> (Runnable) jobs.stream()
                                                              .filter(job -> canTrigger(job) && trigger(job))
                                                              .limit(entry.getKey() ? 1 : Long.MAX_VALUE)::count))
                          .parallel().forEach(Runnable::run);
    }

    /**
     * Triggers the given job for the given application.
     */
    public boolean trigger(Job job) {
        log.info(String.format("Attempting to trigger %s for %s, deploying %s: %s", job.jobType, job.id, job.change, job.reason));

        BuildService.BuildJob buildJob = new BuildService.BuildJob(job.projectId, job.jobType.jobName());
        if (buildService.trigger(buildJob)) {
            applications().lockOrThrow(job.id, application ->
                    applications().store(application.withJobTriggering(job.jobType,
                                                                       clock.instant(),
                                                                       application.deployVersionFor(job.jobType, controller),
                                                                       application.deployApplicationVersionFor(job.jobType, controller, false)
                                                                                  .orElse(ApplicationVersion.unknown),
                                                                       job.reason)));
            return true;
        }
        // TODO jvenstad: On 404: set empty projectId (and send ticket?). Throw and catch NoSuchElementException.
        // TODO jvensatd: On 401, 403 with crumb issues: refresh auth. Handle in client.
        // TODO jvenstad: On 403 with missing collaborator status: send deployment issue ticket? Throw and catch IllegalArumentException.
        log.log(LogLevel.WARNING, "Failed to trigger " + buildJob + " for " + job.id);
        return false;
    }

    /**
     * Triggers a change of this application
     *
     * @param applicationId the application to trigger
     * @throws IllegalArgumentException if this application already have an ongoing change
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

    /**
     * Cancels any ongoing upgrade of the given application
     *
     * @param applicationId the application to trigger
     */
    public void cancelChange(ApplicationId applicationId, boolean keepApplicationChange) {
        applications().lockOrThrow(applicationId, application -> {
            applications().store(application.withChange(application.change().application()
                                                                   .map(Change::of)
                                                                   .filter(change -> keepApplicationChange)
                                                                   .orElse(Change.empty())));
        });
    }

    /**
     * Finds the next step to trigger for the given application, if any, and triggers it
     */
    public List<Job> computeReadyJobs(ApplicationId id) {
        List<Job> jobs = new ArrayList<>();
        applications().lockIfPresent(id, application -> {
            List<DeploymentSpec.Step> steps = application.deploymentSpec().steps().isEmpty()
                    ? Collections.singletonList(new DeploymentSpec.DeclaredZone(Environment.test))
                    : application.deploymentSpec().steps();

            Optional<Instant> completedAt = Optional.of(clock.instant());
            String reason = "Deploying " + application.change();

            for (DeploymentSpec.Step step : steps) {
                Set<JobType> stepJobs = step.zones().stream().map(order::toJob).collect(toSet());
                Set<JobType> remainingJobs = stepJobs.stream().filter(job -> ! completedAt(application, job).isPresent()).collect(toSet());
                if (remainingJobs.isEmpty()) { // All jobs are complete -- find the time of completion of this step.
                    if (stepJobs.isEmpty()) { // No jobs means this is delay step.
                        Duration delay = ((DeploymentSpec.Delay) step).duration();
                        completedAt = completedAt.map(at -> at.plus(delay)).filter(at -> ! at.isAfter(clock.instant()));
                        reason += " after a delay of " + delay;
                    }
                    else {
                        completedAt = stepJobs.stream().map(job -> completedAt(application, job).get()).max(naturalOrder());
                        reason = "Available change in " + stepJobs.stream().map(JobType::jobName).collect(joining(", "));
                    }
                }
                else if (completedAt.isPresent()) { // Step not complete, because some jobs remain -- trigger these if the previous step was done.
                    for (JobType job : remainingJobs)
                        jobs.add(new Job(application, job, reason, completedAt.get(), stepJobs));
                    completedAt = Optional.empty();
                }
            }
            if (completedAt.isPresent())
                applications().store(application.withChange(Change.empty()));
        });
        return jobs;
    }

    /**
     * Returns the set of all jobs which have changes to propagate from the upstream steps, sorted by job.
     */
    public Stream<Job> computeReadyJobs() {
        return ApplicationList.from(applications().asList())
                              .notPullRequest()
                              .withProjectId()
                              .deploying()
                              .idList().stream()
                              .map(this::computeReadyJobs)
                              .flatMap(List::stream);
    }

    /**
     * Returns the instant when the given application's current change was completed for the given job.
     *
     * Any job is complete if its current change was already successful on that job.
     * A production job is also considered complete if its current change is strictly dominated by what
     * is already deployed in its zone, i.e., no parts of the change are upgrades, and at least one
     * part is a downgrade, regardless of the status of the job.
     */
    private Optional<Instant> completedAt(Application application, JobType jobType) {
        Optional<Instant> lastSuccess = application.deploymentJobs().successAt(application.change(), jobType);
        if (lastSuccess.isPresent() || ! jobType.isProduction())
            return lastSuccess;

        Deployment deployment = application.deployments().get(jobType.zone(controller.system()).get());
        return Optional.ofNullable(deployment).map(Deployment::at)
                       .filter(ignored ->    ! (   application.change().upgrades(deployment.version())
                                                || application.change().upgrades(deployment.applicationVersion()))
                                          &&   (   application.change().downgrades(deployment.version())
                                                || application.change().downgrades(deployment.applicationVersion())));
    }

    private boolean canTrigger(Job job) {
        Application application = applications().require(job.id);
        if ( ! application.deploymentJobs().isDeployableTo(job.jobType.environment(), application.change()))
            return false;

        if (application.deploymentJobs().isRunning(job.jobType, jobTimeoutLimit()))
            return false;

        if ( ! job.jobType.isProduction())
            return true;

        if ( ! job.concurrentlyWith.containsAll(JobList.from(application)
                                                       .production()
                                                       .running(jobTimeoutLimit())
                                                       .mapToList(JobStatus::type)))
            return false;

        // TODO jvenstad: This blocks all changes when dual, and in block window. Should rather remove the blocked component.
        // TODO jvenstad: If the above is implemented, take care not to deploy untested stuff?
        if (application.change().blockedBy(application.deploymentSpec(), clock.instant()))
            return false;

        return true;
    }

    private ApplicationController applications() {
        return controller.applications();
    }

    private boolean acceptNewApplicationVersion(LockedApplication application) {
        if ( ! application.change().isPresent()) return true;

        if (application.change().application().isPresent()) return true; // More application changes are ok.

        if (application.deploymentJobs().hasFailures()) return true; // Allow changes to fix upgrade problems.

        if (   ! application.deploymentSpec().canUpgradeAt(clock.instant())
            || ! application.deploymentSpec().canChangeRevisionAt(clock.instant()))
            return true; // Allow testing changes while upgrade blocked (debatable).

        // Otherwise, the application is currently upgrading, without failures, and we should wait with the new application version.
        return false;
    }


    public static class Job {

        private final ApplicationId id;
        private final JobType jobType;
        private final long projectId;
        private final Change change;
        private final String reason;
        private final Instant availableSince;
        private final boolean retry;
        private final Collection<JobType> concurrentlyWith;
        // TODO jvenstad: Store target versions here, and set in withJobTriggering(Job job, Instant at).
        // TODO jvenstad: Use trigger-versions during deployment!

        public Job(Application application, JobType jobType, String reason, Instant availableSince, Collection<JobType> concurrentlyWith) {
            this.id = application.id();
            this.jobType = jobType;
            this.projectId = application.deploymentJobs().projectId().get();
            this.change = application.change();
            this.availableSince = availableSince;
            this.concurrentlyWith = concurrentlyWith;

            JobStatus status = application.deploymentJobs().jobStatus().get(jobType);
            this.retry = status != null && status.jobError().filter(JobError.outOfCapacity::equals).isPresent();
            this.reason = retry ? "Retrying on out of capacity" : reason;
        }

        public ApplicationId id() { return id; }
        public JobType jobType() { return jobType; }
        public long projectId() { return projectId; }
        public Change change() { return change; }
        public String reason() { return reason; }
        public Instant availableSince() { return availableSince; }
        public boolean isRetry() { return retry; }
        public boolean applicationUpgrade() { return change.application().isPresent(); }

    }

}

