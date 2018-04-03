// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.LockedApplication;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobReport;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.JobList;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.JobStatus.JobRun;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
    private final DeploymentQueue deploymentQueue;
    private final DeploymentOrder order;

    public DeploymentTrigger(Controller controller, CuratorDb curator, Clock clock) {
        Objects.requireNonNull(controller, "controller cannot be null");
        Objects.requireNonNull(curator, "curator cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");
        this.controller = controller;
        this.clock = clock;
        this.deploymentQueue = new DeploymentQueue(controller, curator);
        this.order = new DeploymentOrder(controller);
        this.jobTimeout = controller.system().equals(SystemName.main) ? Duration.ofHours(12) : Duration.ofHours(1);
    }

    /**
     * Returns the time in the past before which jobs are at this moment considered unresponsive
     */
    public Instant jobTimeoutLimit() {
        return clock.instant().minus(jobTimeout);
    }

    public DeploymentQueue deploymentQueue() {
        return deploymentQueue;
    }

    public DeploymentOrder deploymentOrder() {
        return order;
    }

    //--- Start of methods which triggers deployment jobs -------------------------

    /**
     * Called each time a job completes (successfully or not) to cause triggering of one or more follow-up jobs
     * (which may possibly the same job once over).
     *
     * @param report information about the job that just completed
     */
    public void triggerFromCompletion(JobReport report) {
        applications().lockOrThrow(report.applicationId(), application -> {
            ApplicationVersion applicationVersion = applicationVersionFrom(report);
            application = application.withJobCompletion(report, applicationVersion, clock.instant(), controller);
            application = application.withProjectId(report.projectId());

            // Handle successful starting and ending
            if (report.jobType() == JobType.component) {
                if (report.success()) {
                    if (!acceptNewApplicationVersionNow(application))
                        application = application.withOutstandingChange(Change.of(applicationVersion));
                    else
                        // Note that in case of an ongoing upgrade this may result in both the upgrade and application
                        // change being deployed together
                        application = application.withChange(application.change().with(applicationVersion));
                }
            }
            else if (report.jobType().isProduction() && deploymentComplete(application)) {
                // change completed
                // TODO jvenstad: Check for and remove individual parts of Change.
                application = application.withChange(Change.empty());
            }

            applications().store(application);
        });
    }

    /**
     * Find jobs that can and should run but are currently not.
     */
    public void triggerReadyJobs() {
        ApplicationList applications = ApplicationList.from(applications().asList());
        applications = applications.notPullRequest()
                                   .withProjectId()
                                   .deploying();
        for (Application application : applications.asList())
            applications().lockIfPresent(application.id(), this::triggerReadyJobs);
    }

    /**
     * Trigger a job for an application, if allowed
     *
     * @param triggering the triggering to execute, i.e., application, job type and reason
     * @return the application in the triggered state, if actually triggered. This *must* be stored by the caller
     */
    public LockedApplication trigger(Triggering triggering, LockedApplication application) {
        // Never allow untested changes to go through
        // Note that this may happen because a new change catches up and prevents an older one from continuing
        if ( ! application.deploymentJobs().isDeployableTo(triggering.jobType.environment(), application.change())) {
            log.warning(String.format("Want to trigger %s for %s with reason %s, but change is untested", triggering.jobType,
                                      application, triggering.reason));
            return application;
        }

        log.info(triggering.toString());
        deploymentQueue.addJob(application.id(), triggering.jobType, triggering.retry);
        // TODO jvenstad: Let triggering set only time of triggering (and reason, for debugging?) when build system is polled for job status.
        return application.withJobTriggering(triggering.jobType,
                                                        clock.instant(),
                                                        application.deployVersionFor(triggering.jobType, controller),
                                                        application.deployApplicationVersionFor(triggering.jobType, controller, false)
                                                                              .orElse(ApplicationVersion.unknown),
                                                        triggering.reason);
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
            if (!applications().require(applicationId).change().isPresent())
                deploymentQueue.removeJobs(application.id());
        });
    }

    //--- End of methods which triggers deployment jobs ----------------------------

    /**
     * Finds the next step to trigger for the given application, if any, and triggers it
     */
    private void triggerReadyJobs(LockedApplication application) {
        List<Triggering> triggerings = new ArrayList<>();
        Change change = application.change();

        // Urgh. Empty spec means unknown spec. Should we write it at component completion?
        List<DeploymentSpec.Step> steps = application.deploymentSpec().steps();
        if (steps.isEmpty()) steps = Collections.singletonList(new DeploymentSpec.DeclaredZone(Environment.test));

        Optional<Instant> completedAt = Optional.of(clock.instant());
        String reason = "Deploying " + change.toString();
        for (DeploymentSpec.Step step : steps) {

            Set<JobType> stepJobs = step.zones().stream().map(order::toJob).collect(Collectors.toSet());
            if (completedAt.isPresent())
                for (JobType jobType : stepJobs) {
                    JobStatus status = application.deploymentJobs().jobStatus().get(jobType);

                    if (jobType.isTest()) {
                        if (application.deploymentJobs().isSuccessful(change, jobType))
                            continue;
                    }
                    else if (jobType.isProduction()) {
                        // TODO jvenstad: Change this to detect whether a deployment is pointless -- versions are decided at deployment.
                        // TODO jvenstad: Filter out non-controller-managed zones here and elsewhere AWS is filtered.
                        if (changeDeployed(application, jobType))
                            continue;
                    }
                    else
                        throw new IllegalStateException("Unclassified type of next job: " + jobType);

                    if ( ! application.deploymentJobs().isDeployableTo(jobType.environment(), change))
                        continue;

                    boolean isRetry = status != null && status.jobError().filter(JobError.outOfCapacity::equals).isPresent();

                    triggerings.add(new Triggering(application, jobType, isRetry, isRetry ? "Retrying on out of capacity" : reason, stepJobs));
                }

                // TODO jvenstad: Merge this with tests above for whether to do anything in this step -- exactly one step should trigger each time (counting deleting the Change as the last step.)
            if (step.deploysTo(Environment.test) || step.deploysTo(Environment.staging)) {
                completedAt = Optional.ofNullable(application.deploymentJobs().jobStatus().get(order.toJob(step.zones().get(0))))
                                      .flatMap(JobStatus::lastSuccess)
                                      .filter(run -> change.platform().map(run.version()::equals).orElse(true))
                                      .filter(run -> change.application().map(run.applicationVersion()::equals).orElse(true))
                                      .map(JobRun::at);
            }
            else if (step.deploysTo(Environment.prod)) {
                LockedApplication finalApplication = application;
                completedAt = stepJobs.stream().allMatch(job -> changeDeployed(finalApplication, job))
                        ? stepJobs.stream().map(job -> job.zone(controller.system()).get()).map(finalApplication.deployments()::get).map(Deployment::at).max(Comparator.naturalOrder())
                        : Optional.empty();
            }
            else
                completedAt = completedAt.map(at -> at.plus(((DeploymentSpec.Delay) step).duration()))
                                         .filter(at -> ! at.isAfter(clock.instant()));

            reason = "Available change in " + stepJobs.stream().map(JobType::jobName).collect(Collectors.joining(", "));
        }

        for (Triggering triggering : triggerings)
            if (allowedToTriggerNow(triggering, application))
                application = trigger(triggering, application);

        applications().store(application);
    }

    private boolean allowedToTriggerNow(Triggering triggering, Application application) {
        if (application.deploymentJobs().isRunning(triggering.jobType, jobTimeoutLimit()))
            return false;

        if ( ! triggering.jobType.isProduction())
            return true;

        if ( ! triggering.concurrentlyWith.containsAll(JobList.from(application)
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

    /**
     * Create application version from job report
     */
    private ApplicationVersion applicationVersionFrom(JobReport report) {
        return report.sourceRevision().map(sr -> ApplicationVersion.from(sr, report.buildNumber()))
                     .orElse(ApplicationVersion.unknown);
    }

    /**
     * Returns whether all production zones listed in deployment spec has this change (or a newer version, if upgrade)
     */
    private boolean deploymentComplete(LockedApplication application) {
        return order.jobsFrom(application.deploymentSpec()).stream()
                    .filter(JobType::isProduction)
                    .filter(job -> job.zone(controller.system()).isPresent())
                    .allMatch(job -> changeDeployed(application, job));
    }

    /**
     * Returns whether the given application should skip deployment of its current change to the given production job zone.
     * <p>
     * If the currently deployed application has a newer platform or application version than the application's
     * current change, the method returns {@code true}, to avoid a downgrade.
     * Otherwise, it returns whether the current change is redundant, i.e., all its components are already deployed.
     */
    private boolean changeDeployed(Application application, JobType job) {
        if (!job.isProduction())
            throw new IllegalArgumentException(job + " is not a production job!");

        Deployment deployment = application.deployments().get(job.zone(controller.system()).get());
        if (deployment == null)
            return false;

        int applicationComparison = application.change().application()
                                               .map(version -> version.compareTo(deployment.applicationVersion()))
                                               .orElse(0);

        int platformComparison = application.change().platform()
                                            .map(version -> version.compareTo(deployment.version()))
                                            .orElse(0);

        if (applicationComparison == -1 || platformComparison == -1)
            return true; // Avoid downgrades!

        return applicationComparison == 0 && platformComparison == 0;
    }

    private boolean acceptNewApplicationVersionNow(LockedApplication application) {
        if (!application.change().isPresent()) return true;

        if (application.change().application().isPresent()) return true; // more application changes are ok

        if (application.deploymentJobs().hasFailures()) return true; // allow changes to fix upgrade problems

        if (application.isBlocked(clock.instant()))
            return true; // allow testing changes while upgrade blocked (debatable)

        // Otherwise, the application is currently upgrading, without failures, and we should wait with the new
        // application version.
        return false;
    }

    public static class Triggering {

        private final LockedApplication application; // TODO jvenstad: Consider passing an ID instead.
        private final JobType jobType;
        private final boolean retry;
        private final String reason;
        private final Set<JobType> concurrentlyWith;

        public Triggering(LockedApplication application, JobType jobType, boolean retry, String reason, Set<JobType> concurrentlyWith) {
            this.application = application;
            this.jobType = jobType;
            this.retry = retry;
            this.reason = reason;
            this.concurrentlyWith = concurrentlyWith;
        }

        public Triggering(LockedApplication application, JobType jobType, boolean retry, String reason) {
            this(application, jobType, retry, reason, Collections.emptySet());
        }

        public LockedApplication application() {
            return application;
        }

        public JobType jobType() {
            return jobType;
        }

        public boolean isRetry() {
            return retry;
        }

        public String reason() {
            return reason;
        }

        public Set<JobType> getConcurrentlyWith() {
            return concurrentlyWith;
        }

        public String toString() {
            return String.format("Triggering %s for %s, %s: %s",
                                 jobType,
                                 application,
                                 application.change().isPresent() ? "deploying " + application.change() : "restarted deployment",
                                 reason);
        }

    }

}
//
//    /**
//     * Loop through the steps of the deployment spec for the given application and propagate state changes as prescribed.
//     *
//     * The intial state is determined by whether there are revision or version changes to roll out.
//     * For each following step, as given by the deployment spec, check whether the previous state is valid,
//     * different from the state of the jobs in the step and with differences that are currently permitted;
//     * if this is all true, determine the reason for triggering the current job, if any. If there is a reason
//     * to trigger, and if it is allowed to trigger that job right now, then do so.
//     * Finally, if the last step succeeded with a valid state, mark the changes of this state as complete.
//     */
//    // This method should be called both by the upgrade maintainer, and on job completion.
//    private void triggerJobsFor(LockedApplication application) {
//        State state = State.inital(application);
//        for (DeploymentSpec.Step step : application.deploymentSpec().steps()) {
//            // For the jobs of this step, trigger what should be triggered based on the state of the previous step:
//            List<JobType> stepJobs = step.zones().stream().map(this::jobFor).collect(Collectors.toList());
//            application.deploymentJobs().jobStatus();
//            for (JobType jobType : stepJobs) {
//                JobStatus jobStatus = application.deploymentJobs().jobStatus().get(jobType);
//                if (state.isSimilarTo(State.of(jobStatus))) continue; // If the two states are the same, do nothing.
//
//                State target = state;
//                if ( ! application.deploymentSpec().canChangeRevisionAt(clock.instant())) target = target.withoutApplication();
//                if ( ! application.deploymentSpec().canUpgradeAt(clock.instant())) target = target.withoutPlatform();
//                // Also peel away versions that are no longer desired: no longer the deploying(), or, perhaps, no longer have high enough confidence?
//                if (target.isInvalid()) continue; // If all possible changes were prohibited, do nothing.
//
//                Optional<String> reason = reasonForTriggering(jobStatus);
//                if ( ! reason.isPresent()) continue; // If we had no reason to trigger this job now: don't!
//
//                if ( ! canTriggerNow(jobType, target, application, stepJobs)) continue; // Go somewhere else to force.
//
//                buildSystem.addJob(application.id(), jobType, reason.get().equals("Retrying immediately, as the job failed on capacity constraints."));
//                application = application.withJobTriggering(jobType, application.deploying(), reason.get(), clock.instant(), controller);
//
//            }
//            // Finally, find the exit state of this step, to propagate further:
//            state = JobList.from(application).types(stepJobs).commonState()
//                           // A bit convoluted, perhaps; if empty stepJobs, this is a Delay, and we just delay the input state.
//                           .orElse(state.delay(((DeploymentSpec.Delay) step).duration(), clock.instant()));
//        }
//        if (state.platform().isPresent()) application = application.withDeploying(Optional.empty()); // TODO: Change this, obviously.
//        controller.applications().store(application);
//    }
//
//    private JobType jobFor(DeploymentSpec.DeclaredZone zone) {
//        return JobType.from(controller.system(), zone.environment(), zone.region().orElse(null))
//                      .orElseThrow(() -> new IllegalArgumentException("Invalid zone " + zone));
//    }
//
//    private Optional<String> reasonForTriggering(JobStatus jobStatus) {
//        if (jobStatus == null) return Optional.of("Job became available for triggering for the first time.");
//        if (jobStatus.isRunning(jobTimeoutLimit())) return Optional.empty();
//        if (jobStatus.isSuccess()) return Optional.of("A new change passed successfully through the upstream step.");
//        if (jobStatus.jobError().filter(JobError.outOfCapacity::equals).isPresent()) return Optional.of("Retrying immediately, as the job failed on capacity constraints.");
//        if (jobStatus.firstFailing().get().at().isAfter(clock.instant().minus(Duration.ofSeconds(10)))) return Optional.of("Immediate retry, as " + jobStatus.type() + " just started failing.");
//        if (   jobStatus.lastCompleted().get().at().isBefore(clock.instant().minus(Duration.between(jobStatus.firstFailing().get().at(), clock.instant()).dividedBy(10)))
//               || jobStatus.lastCompleted().get().at().isBefore(clock.instant().minus(Duration.ofHours(4)))) return Optional.of("Delayed retry, as " + jobStatus.type() + " hasn't been retried for a while.");
//        return Optional.empty();
//    }
//
//    private boolean canTriggerNow(JobType jobType, State target, Application application, List<JobType> concurrentJobs) {
//        if ( ! application.deploymentJobs().projectId().isPresent()) return false;
//        if (target.platform().isPresent() && jobType.isProduction() && isOnNewerVersionThan(target.platform().get(), jobType, application)) return false;
//        if ( ! concurrentJobs.containsAll(JobList.from(application).running(jobTimeoutLimit()).production().asList())) return false;
//        return true;
//    }
//
//    private boolean isOnNewerVersionThan(Version version, JobType jobType, Application application) {
//        return jobType.zone(controller.system())
//                      .map(zone -> application.deployments().get(zone))
//                      .filter(deployment -> deployment.version().isAfter(version))
//                      .isPresent();
//    }
//
//    /**
//     * Contains information about the last successful state of a Step.
//     *
//     * Two states can be merged by similarity, and delayed by a given delay. If the resulting state
//     * is invalid, it means some next step does not (yet) have a new state to upgrade to;
//     * otherwise, the resulting state provides target version and revision for some next step.
//     */
//    public static class State {
//
//        public static final State invalid = new State(null, null, null);
//
//        private final Version platform;
//        private final ApplicationVersion application;
//        private final Instant completion;
//
//        private State(Version platform, ApplicationVersion application, Instant completion) {
//            this.platform = platform;
//            this.application = application;
//            this.completion = completion;
//        }
//
//        public static State of(JobStatus jobStatus) {
//            if (jobStatus == null || ! jobStatus.lastSuccess().isPresent()) return State.invalid;
//            return new State(jobStatus.lastSuccess().get().version(),
//                             jobStatus.lastSuccess().get().applicationVersion(),
//                             jobStatus.lastSuccess().get().at());
//        }
//
//        public static State inital(Application application) {
//            return new State(application.change().platform().orElse(null),
//                             application.change().application().orElse(null),
//                             Instant.MIN);
//        }
//
//        /** Returns the state with the later completion if they are similar, and invalid otherwise. */
//        public State merge(State other) {
//            return ! isInvalid() && isSimilarTo(other)
//                    ? completion.isAfter(other.completion) ? this : other
//                    : invalid;
//        }
//
//        /** Returns a state with completion delayed by the given delay, or invalid if this is before the given now. */
//        public State delay(Duration delay, Instant now) {
//            return (isInvalid() || completion.plus(delay).isAfter(now))
//                    ? invalid
//                    : new State(platform, application, completion.plus(delay));
//        }
//
//        /** Returns whether the two states agree on version and revision. */
//        public boolean isSimilarTo(State other) {
//            return platform().equals(other.platform()) && application().equals(other.application());
//        }
//
//        /** Returns whether this state provides a valid upgrade target. */
//        public boolean isInvalid() {
//            return completion == null || (platform == null && application == null);
//        }
//
//        public State deployableTo(Version platform) {
//            return this.platform != null && this.platform.compareTo(platform) < 0
//                    ? this
//                    : withoutPlatform();
//        }
//        // TODO jvenstad: Inline these two?
//        public State deployableTo(ApplicationVersion application) {
//            return this.application != null && this.application.compareTo(application) < 0
//                    ? this
//                    : withoutApplication();
//        }
//
//        public Optional<Version> platform() {
//            return Optional.ofNullable(platform);
//        }
//
//        public Optional<ApplicationVersion> application() {
//            return Optional.ofNullable(application);
//        }
//
//        public State withoutPlatform() {
//            return new State(null, application, completion);
//        }
//
//        public State withoutApplication() {
//            return new State(platform, null, completion);
//        }
//
//    }
//
//
//}
