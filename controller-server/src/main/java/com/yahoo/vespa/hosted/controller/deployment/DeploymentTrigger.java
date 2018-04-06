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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
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
    public void triggerFromCompletion(JobReport report) {
        applications().lockOrThrow(report.applicationId(), application -> {
            log.log(LogLevel.INFO, String.format("%s %d completed for %s with %s.", report.jobType(), report.buildNumber(), report.applicationId(), report.jobError().map(JobError::toString).orElse("success")));
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
     */
    public void triggerReadyJobs() {
        computeReadyJobs().forEach((jobType, jobs) -> {
            for (Job job : jobs)
                if (canTrigger(job) && trigger(job) && jobType.isTest()) break;
        });
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
        // TODO jvenstad: On 404, set empty projectId (and send ticket?)
        // TODO jvensatd: On 401, 403 with crumb issues, refresh auth.
        // TODO jvenstad: On 403 with missing collaborator status send deployment issue ticket?
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
            if (application.change().isPresent() && !application.deploymentJobs().hasFailures())
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

    //--- End of methods which triggers deployment jobs ----------------------------

    /**
     * Finds the next step to trigger for the given application, if any, and triggers it
     */
    public List<Job> computeReadyJobs(ApplicationId id) {
        List<Job> jobs = new ArrayList<>();
        applications().lockIfPresent(id, application -> {
            Change change = application.change();

            // Urgh. Empty spec means unknown spec. Should we write it at component completion?
            List<DeploymentSpec.Step> steps = application.deploymentSpec().steps();
            if (steps.isEmpty()) steps = Collections.singletonList(new DeploymentSpec.DeclaredZone(Environment.test));

            Optional<Instant> completedAt = Optional.of(clock.instant());
            String reason = "Deploying " + change.toString();

            for (DeploymentSpec.Step step : steps) {
                LockedApplication app = application;
                Set<JobType> stepJobs = step.zones().stream().map(order::toJob).collect(toSet());
                Set<JobType> remainingJobs = stepJobs.stream().filter(job -> !completedAt(app, job).isPresent()).collect(toSet());
                if (remainingJobs.isEmpty()) { // All jobs are complete -- find the time of completion for this step.
                    if (stepJobs.isEmpty()) { // No jobs means this is delay step.
                        Duration delay = ((DeploymentSpec.Delay) step).duration();
                        completedAt = completedAt.map(at -> at.plus(delay)).filter(at -> !at.isAfter(clock.instant()));
                        reason += " after a delay of " + delay;
                    }
                    else {
                        completedAt = stepJobs.stream().map(job -> completedAt(app, job).get()).max(naturalOrder());
                        reason = "Available change in " + stepJobs.stream().map(JobType::jobName).collect(joining(", "));
                    }
                }
                else if (completedAt.isPresent()) { // Some jobs remain, and this step is not complete -- trigger those jobs if the previous step was done.
                    for (JobType job : remainingJobs)
                        jobs.add(new Job(app, job, reason, completedAt.get(), stepJobs));
                    completedAt = Optional.empty();
                }
            }
            if (completedAt.isPresent())
                application = application.withChange(Change.empty());
            applications().store(application);
        });
        return jobs;
    }

    public Map<JobType, List<Job>> computeReadyJobs() {
        return ApplicationList.from(applications().asList())
                              .notPullRequest()
                              .withProjectId()
                              .deploying()
                              .idList().stream()
                              .map(this::computeReadyJobs)
                              .flatMap(List::stream)
                              .sorted(comparing(Job::isRetry)
                                              .thenComparing(Job::applicationUpgrade)
                                              .reversed()
                                              .thenComparing(Job::availableSince))
                              .collect(groupingBy(Job::jobType));
    }

    private Optional<Instant> completedAt(Application application, JobType jobType) {
        return jobType.isProduction()
                ? changeCompletedAt(application, jobType)
                : application.deploymentJobs().successAt(application.change(), jobType);
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

    /** Returns the instant when the given application's current change was completed for the given job. */
    private Optional<Instant> changeCompletedAt(Application application, JobType job) {
        if ( ! job.isProduction())
            throw new IllegalArgumentException(job + " is not a production job!");

        Deployment deployment = application.deployments().get(job.zone(controller.system()).get());
        if (deployment == null)
            return Optional.empty();

        int applicationComparison = application.change().application()
                                               .map(version -> version.compareTo(deployment.applicationVersion()))
                                               .orElse(0);

        int platformComparison = application.change().platform()
                                            .map(version -> version.compareTo(deployment.version()))
                                            .orElse(0);

        // TODO jvenstad: Allow downgrades when considering whether to trigger -- stop them at choice of deployment version.
        // TODO jvenstad: This allows tests to be re-run, for instance, while keeping the deployment itself a no-op.
        return Optional.of(deployment.at())
                .filter(ignored ->     applicationComparison == -1 || platformComparison == -1
                                   || (applicationComparison ==  0 && platformComparison ==  0));
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

