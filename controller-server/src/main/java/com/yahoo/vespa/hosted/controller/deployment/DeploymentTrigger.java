// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobReport;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Responsible for scheduling deployment jobs in a build system and keeping
 * Application.deploying() in sync with what is scheduled.
 * 
 * This class is multithread safe.
 * 
 * @author bratseth
 * @author mpolden
 */
public class DeploymentTrigger {

    /** The max duration a job may run before we consider it dead/hanging */
    private final Duration jobTimeout;

    private final static Logger log = Logger.getLogger(DeploymentTrigger.class.getName());

    private final Controller controller;
    private final Clock clock;
    private final BuildSystem buildSystem;
    private final DeploymentOrder order;

    public DeploymentTrigger(Controller controller, CuratorDb curator, Clock clock) {
        Objects.requireNonNull(controller,"controller cannot be null");
        Objects.requireNonNull(curator,"curator cannot be null");
        Objects.requireNonNull(clock,"clock cannot be null");
        this.controller = controller;
        this.clock = clock;
        this.buildSystem = new PolledBuildSystem(controller, curator);
        this.order = new DeploymentOrder(controller);
        this.jobTimeout = controller.system().equals(SystemName.main) ? Duration.ofHours(12) : Duration.ofHours(1);
    }
    
    /** Returns the time in the past before which jobs are at this moment considered unresponsive */
    public Instant jobTimeoutLimit() { return clock.instant().minus(jobTimeout); }
    
    //--- Start of methods which triggers deployment jobs -------------------------

    /** 
     * Called each time a job completes (successfully or not) to cause triggering of one or more follow-up jobs
     * (which may possibly the same job once over).
     * 
     * @param report information about the job that just completed
     */
    public void triggerFromCompletion(JobReport report) {
        try (Lock lock = applications().lock(report.applicationId())) {
            Application application = applications().require(report.applicationId());
            application = application.withJobCompletion(report, clock.instant(), controller);
            
            // Handle successful starting and ending
            if (report.success()) {
                if (order.isFirst(report.jobType())) { // the first job tells us that a change occurred
                    if (acceptNewRevisionNow(application)) {
                        // Set this as the change we are doing, unless we are already pushing a platform change
                        if ( ! ( application.deploying().isPresent() && 
                                 (application.deploying().get() instanceof Change.VersionChange)))
                            application = application.withDeploying(Optional.of(Change.ApplicationChange.unknown()));
                    }
                    else { // postpone
                        applications().store(application.withOutstandingChange(true), lock);
                        return;
                    }
                } 
                else if (order.isLast(report.jobType(), application) && application.deployingCompleted()) {
                    // change completed
                    application = application.withDeploying(Optional.empty());
                }
            }

            // Trigger next
            if (report.success())
                application = trigger(order.nextAfter(report.jobType(), application), application,
                                      String.format("%s completed successfully in build %d",
                                                    report.jobType(), report.buildNumber()), lock);
            else if (isCapacityConstrained(report.jobType()) && shouldRetryOnOutOfCapacity(application, report.jobType()))
                application = trigger(report.jobType(), application, true,
                                      String.format("Retrying due to out of capacity in build %d",
                                                    report.buildNumber()), lock);
            else if (shouldRetryNow(application))
                application = trigger(report.jobType(), application, false,
                                      String.format("Retrying as build %d just started failing",
                                                    report.buildNumber()), lock);

            applications().store(application, lock);
        }
    }

    /**
     * Find jobs that can and should run but are currently not.
     */
    public void triggerReadyJobs() {
        ApplicationList applications = ApplicationList.from(applications().asList());
        applications = applications.notPullRequest();
        for (Application application : applications.asList()) {
            try (Lock lock = applications().lock(application.id())) {
                triggerReadyJobs(application, lock);
            }
        }
    }
    
    private void triggerReadyJobs(Application application, Lock lock) {
        if ( ! application.deploying().isPresent()) return;
        for (JobType jobType : order.jobsFrom(application.deploymentSpec())) {
            // TODO: Do this for all jobs not just staging, and (with more work) remove triggerFailing and triggerDelayed
            if (jobType.environment().equals(Environment.staging)) {
                JobStatus jobStatus = application.deploymentJobs().jobStatus().get(jobType);
                if (jobStatus.isRunning(jobTimeoutLimit())) continue;

                for (JobType nextJobType : order.nextAfter(jobType, application)) {
                    JobStatus nextStatus = application.deploymentJobs().jobStatus().get(nextJobType);
                    
                    // Attempt to trigger if there are changes available - is rejected if the change is in progress,
                    // or is currently blocked
                    if (changesAvailable(jobStatus, nextStatus))
                        trigger(nextJobType, application, false, "Triggering previously blocked job", lock);
                }
                
            }
        }
    }

    /**
     * Returns true if the previous job has completed successfully with a revision and/or version which is
     * newer (different) than the one last completed successfully in next
     */
    private boolean changesAvailable(JobStatus previous, JobStatus next) {
        if ( ! previous.lastSuccess().isPresent()) return false;
        if (next == null) return true;
        if ( ! next.lastSuccess().isPresent()) return true;
        
        JobStatus.JobRun previousSuccess = previous.lastSuccess().get();
        JobStatus.JobRun nextSuccess = next.lastSuccess().get();
        if (previousSuccess.revision().isPresent() &&  ! previousSuccess.revision().get().equals(nextSuccess.revision().get()))
            return true;
        if (! previousSuccess.version().equals(nextSuccess.version()))
            return true;
        return false;
    }
    
    /**
     * Called periodically to cause triggering of jobs in the background
     */
    public void triggerFailing(ApplicationId applicationId) {
        try (Lock lock = applications().lock(applicationId)) {
            Application application = applications().require(applicationId);
            if ( ! application.deploying().isPresent()) return; // No ongoing change, no need to retry

            // Retry first failing job
            for (JobType jobType : order.jobsFrom(application.deploymentSpec())) {
                JobStatus jobStatus = application.deploymentJobs().jobStatus().get(jobType);
                if (isFailing(application.deploying().get(), jobStatus)) {
                    if (shouldRetryNow(jobStatus)) {
                        application = trigger(jobType, application, false, "Retrying failing job", lock);
                        applications().store(application, lock);
                    }
                    break;
                }
            }

            // Retry dead job
            Optional<JobStatus> firstDeadJob = firstDeadJob(application.deploymentJobs());
            if (firstDeadJob.isPresent()) {
                application = trigger(firstDeadJob.get().type(), application, false, "Retrying dead job",
                                      lock);
                applications().store(application, lock);
            }
        }
    }

    /** Triggers jobs that have been delayed according to deployment spec */
    public void triggerDelayed() {
        for (Application application : applications().asList()) {
            if ( ! application.deploying().isPresent() ) continue;
            if (application.deploymentJobs().hasFailures()) continue;
            if (application.deploymentJobs().isRunning(controller.applications().deploymentTrigger().jobTimeoutLimit())) continue;
            if (application.deploymentSpec().steps().stream().noneMatch(step -> step instanceof DeploymentSpec.Delay)) {
                continue; // Application does not have any delayed deployments
            }

            Optional<JobStatus> lastSuccessfulJob = application.deploymentJobs().jobStatus().values()
                    .stream()
                    .filter(j -> j.lastSuccess().isPresent())
                    .sorted(Comparator.<JobStatus, Instant>comparing(j -> j.lastSuccess().get().at()).reversed())
                    .findFirst();
            if ( ! lastSuccessfulJob.isPresent() ) continue;

            // Trigger next
            try (Lock lock = applications().lock(application.id())) {
                application = applications().require(application.id());
                application = trigger(order.nextAfter(lastSuccessfulJob.get().type(), application), application,
                                      "Resuming delayed deployment", lock);
                applications().store(application, lock);
            }
        }
    }
    
    /**
     * Triggers a change of this application
     * 
     * @param applicationId the application to trigger
     * @throws IllegalArgumentException if this application already have an ongoing change
     */
    public void triggerChange(ApplicationId applicationId, Change change) {
        try (Lock lock = applications().lock(applicationId)) {
            Application application = applications().require(applicationId);
            if (application.deploying().isPresent()  && ! application.deploymentJobs().hasFailures())
                throw new IllegalArgumentException("Could not upgrade " + application + ": A change is already in progress");
            application = application.withDeploying(Optional.of(change));
            if (change instanceof Change.ApplicationChange)
                application = application.withOutstandingChange(false);
            application = trigger(JobType.systemTest, application, false, "Deploying change", lock);
            applications().store(application, lock);
        }
    }

    /**
     * Cancels any ongoing upgrade of the given application
     *
     * @param applicationId the application to trigger
     */
    public void cancelChange(ApplicationId applicationId) {
        try (Lock lock = applications().lock(applicationId)) {
            Application application = applications().require(applicationId);
            buildSystem.removeJobs(application.id());
            application = application.withDeploying(Optional.empty());
            applications().store(application, lock);
        }
    }

    //--- End of methods which triggers deployment jobs ----------------------------

    private ApplicationController applications() { return controller.applications(); }

    /** Returns whether a job is failing for the current change in the given application */
    private boolean isFailing(Change change, JobStatus status) {
        return status != null &&
               !status.isSuccess() &&
               status.lastCompletedFor(change);
    }

    private boolean isCapacityConstrained(JobType jobType) {
        return jobType == JobType.stagingTest || jobType == JobType.systemTest;
    }

    /** Returns the first job that has been running for more than the given timeout */
    private Optional<JobStatus> firstDeadJob(DeploymentJobs jobs) {
        Optional<JobStatus> oldestRunningJob = jobs.jobStatus().values().stream()
                .filter(job -> job.isRunning(Instant.ofEpochMilli(0)))
                .sorted(Comparator.comparing(status -> status.lastTriggered().get().at()))
                .findFirst();
        return oldestRunningJob.filter(job -> job.lastTriggered().get().at().isBefore(jobTimeoutLimit()));
    }

    /** Decide whether the job should be triggered by the periodic trigger */
    private boolean shouldRetryNow(JobStatus job) {
        if (job.isSuccess()) return false;
        if (job.isRunning(jobTimeoutLimit())) return false;

        // Retry after 10% of the time since it started failing
        Duration aTenthOfFailTime = Duration.ofMillis( (clock.millis() - job.firstFailing().get().at().toEpochMilli()) / 10);
        if (job.lastCompleted().get().at().isBefore(clock.instant().minus(aTenthOfFailTime))) return true;

        // ... or retry anyway if we haven't tried in 4 hours
        if (job.lastCompleted().get().at().isBefore(clock.instant().minus(Duration.ofHours(4)))) return true;

        return false;
    }
    
    /** Retry immediately only if this just started failing. Otherwise retry periodically */
    private boolean shouldRetryNow(Application application) {
        return application.deploymentJobs().failingSince().isAfter(clock.instant().minus(Duration.ofSeconds(10)));
    }

    /** Decide whether to retry due to capacity restrictions */
    private boolean shouldRetryOnOutOfCapacity(Application application, JobType jobType) {
        Optional<JobError> outOfCapacityError = Optional.ofNullable(application.deploymentJobs().jobStatus().get(jobType))
                .flatMap(JobStatus::jobError)
                .filter(e -> e.equals(JobError.outOfCapacity));

        if ( ! outOfCapacityError.isPresent()) return false;

        // Retry the job if it failed recently
        return application.deploymentJobs().jobStatus().get(jobType).firstFailing().get().at()
                .isAfter(clock.instant().minus(Duration.ofMinutes(15)));
    }

    /** Returns whether the given job type should be triggered according to deployment spec */
    private boolean deploysTo(Application application, JobType jobType) {
        Optional<Zone> zone = jobType.zone(controller.system());
        if (zone.isPresent() && jobType.isProduction()) {
            // Skip triggering of jobs for zones where the application should not be deployed
            if ( ! application.deploymentSpec().includes(jobType.environment(), Optional.of(zone.get().region()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Trigger a job for an application 
     * 
     * @param jobType the type of the job to trigger, or null to trigger nothing
     * @param application the application to trigger the job for
     * @param first whether to trigger the job before other jobs
     * @param cause describes why the job is triggered
     * @return the application in the triggered state, which *must* be stored by the caller
     */
    private Application trigger(JobType jobType, Application application, boolean first, String cause, Lock lock) {
        if (jobType == null) { // previous was last job
            return application;
        }

        // Note: We could make a more fine-grained and more correct determination about whether to block 
        //       by instead basing the decision on what is currently deployed in the zone. However,
        //       this leads to some additional corner cases, and the possibility of blocking an application
        //       fix to a version upgrade, so not doing it now
        if (jobType.isProduction() && application.deployingBlocked(clock.instant())) {
            return application;
        }
        
        if (application.deploymentJobs().isRunning(jobType, jobTimeoutLimit())) {
            return application;
        }

        // TODO: Remove when we can determine why this occurs
        if (jobType != JobType.component && ! application.deploying().isPresent()) {
            log.warning(String.format("Want to trigger %s for %s with reason %s, but this application is not " +
                                              "currently deploying a change",
                                      jobType, application, cause));
            return application;
        }

        if  ( ! deploysTo(application, jobType)) {
            return application;
        }

        // Note that this allows a new change to catch up and prevent an older one from continuing
        if ( ! application.deploymentJobs().isDeployableTo(jobType.environment(), application.deploying())) {
            log.warning(String.format("Want to trigger %s for %s with reason %s, but change is untested", jobType,
                                      application, cause));
            return application;
        }

        // Ignore applications that are not associated with a project
        if ( ! application.deploymentJobs().projectId().isPresent()) {
            return application;
        }

        log.info(String.format("Triggering %s for %s, %s: %s", jobType, application,
                               application.deploying().map(d -> "deploying " + d).orElse("restarted deployment"),
                               cause));
        buildSystem.addJob(application.id(), jobType, first);

        return application.withJobTriggering(jobType, application.deploying(), clock.instant(), controller);
    }

    private Application trigger(List<JobType> jobs, Application application, String cause, Lock lock) {
        for (JobType job : jobs)
            application = trigger(job, application, false, cause, lock);
        return application;
    }

    private boolean acceptNewRevisionNow(Application application) {
        if ( ! application.deploying().isPresent()) return true;
        if ( application.deploying().get() instanceof Change.ApplicationChange) return true; // more changes are ok
        
        if ( application.deploymentJobs().hasFailures()) return true; // allow changes to fix upgrade problems
        if ( application.isBlocked(clock.instant())) return true; // allow testing changes while upgrade blocked (debatable)
        return false;
    }
    
    public BuildSystem buildSystem() { return buildSystem; }

    public DeploymentOrder deploymentOrder() { return order; }

}
