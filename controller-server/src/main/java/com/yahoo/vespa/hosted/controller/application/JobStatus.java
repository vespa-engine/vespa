// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * The last known build status of a particular deployment job for a particular application.
 * This is immutable.
 *
 * @author bratseth
 * @author mpolden
 */
public class JobStatus {

    private final JobType type;

    private final Optional<JobRun> lastTriggered;
    private final Optional<JobRun> lastCompleted;
    private final Optional<JobRun> firstFailing;
    private final Optional<JobRun> lastSuccess;

    private final Optional<DeploymentJobs.JobError> jobError;

    /**
     * Used by the persistence layer (only) to create a complete JobStatus instance.
     * Other creation should be by using initial- and with- methods.
     */
    public JobStatus(JobType type, Optional<DeploymentJobs.JobError> jobError,
                     Optional<JobRun> lastTriggered, Optional<JobRun> lastCompleted,
                     Optional<JobRun> firstFailing, Optional<JobRun> lastSuccess) {
        requireNonNull(type, "jobType cannot be null");
        requireNonNull(jobError, "jobError cannot be null");
        requireNonNull(lastTriggered, "lastTriggered cannot be null");
        requireNonNull(lastCompleted, "lastCompleted cannot be null");
        requireNonNull(firstFailing, "firstFailing cannot be null");
        requireNonNull(lastSuccess, "lastSuccess cannot be null");

        this.type = type;
        this.jobError = jobError;

        // Never say we triggered component because we don't:
        this.lastTriggered = type == JobType.component ? Optional.empty() : lastTriggered;
        this.lastCompleted = lastCompleted;
        this.firstFailing = firstFailing;
        this.lastSuccess = lastSuccess;
    }

    /** Returns an empty job status */
    public static JobStatus initial(JobType type) {
        return new JobStatus(type, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public JobStatus withTriggering(Version platform, ApplicationVersion application, Optional<Deployment> deployment, String reason, Instant triggeredAt) {
        return withTriggering(JobRun.triggering(platform, application, deployment.map(Deployment::version), deployment.map(Deployment::applicationVersion), reason, triggeredAt));
    }

    public JobStatus withTriggering(JobRun jobRun) {
        return new JobStatus(type, jobError, Optional.of(jobRun), lastCompleted, firstFailing, lastSuccess);
    }

    public JobStatus withCompletion(long runId, Optional<DeploymentJobs.JobError> jobError, Instant completion) {
        return withCompletion(lastTriggered.get().completion(runId, completion), jobError);
    }

    public JobStatus withCompletion(JobRun completion, Optional<DeploymentJobs.JobError> jobError) {
        Optional<JobRun> firstFailing = this.firstFailing;
        if (jobError.isPresent() && ! this.firstFailing.isPresent())
            firstFailing = Optional.of(completion);

        Optional<JobRun> lastSuccess = this.lastSuccess;
        if ( ! jobError.isPresent()) {
            lastSuccess = Optional.of(completion);
            firstFailing = Optional.empty();
        }

        return new JobStatus(type, jobError, lastTriggered, Optional.of(completion), firstFailing, lastSuccess);
    }

    public JobType type() { return type; }

    /** Returns true unless this job last completed with a failure */
    public boolean isSuccess() {
        return lastCompleted().isPresent() && ! jobError.isPresent();
    }

    /** The error of the last completion, or empty if the last run succeeded */
    public Optional<DeploymentJobs.JobError> jobError() { return jobError; }

    /** Returns whether this last failed on out of capacity */
    public boolean isOutOfCapacity() {
        return jobError.filter(error -> error == DeploymentJobs.JobError.outOfCapacity).isPresent();
    }

    /**
     * Returns the last triggering of this job, or empty if the controller has never triggered it
     * and not seen a deployment for it
     */
    public Optional<JobRun> lastTriggered() { return lastTriggered; }

    /** Returns the last completion of this job (whether failing or succeeding), or empty if it never completed */
    public Optional<JobRun> lastCompleted() { return lastCompleted; }

    /** Returns the run when this started failing, or empty if it is not currently failing */
    public Optional<JobRun> firstFailing() { return firstFailing; }

    /** Returns the run when this last succeeded, or empty if it has never succeeded */
    public Optional<JobRun> lastSuccess() { return lastSuccess; }

    @Override
    public String toString() {
        return "job status of " + type + "[ " +
               "last triggered: " + lastTriggered.map(JobRun::toString).orElse("(never)") +
               ", last completed: " + lastCompleted.map(JobRun::toString).orElse("(never)") +
               ", first failing: " + firstFailing.map(JobRun::toString).orElse("(not failing)") +
               ", lastSuccess: " + lastSuccess.map(JobRun::toString).orElse("(never)") + "]";
    }

    @Override
    public int hashCode() { return Objects.hash(type, jobError, lastTriggered, lastCompleted, firstFailing, lastSuccess); }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! ( o instanceof JobStatus)) return false;
        JobStatus other = (JobStatus)o;
        return Objects.equals(type, other.type) &&
               Objects.equals(jobError, other.jobError) &&
               Objects.equals(lastTriggered, other.lastTriggered) &&
               Objects.equals(lastCompleted, other.lastCompleted) &&
               Objects.equals(firstFailing, other.firstFailing) &&
               Objects.equals(lastSuccess, other.lastSuccess);
    }

    /** Information about a particular triggering or completion of a run of a job. This is immutable. */
    public static class JobRun {

        private final long id;
        private final Version platform;
        private final ApplicationVersion application;
        private final Optional<Version> sourcePlatform;
        private final Optional<ApplicationVersion> sourceApplication;
        private final String reason;
        private final Instant at;

        public JobRun(long id, Version platform, ApplicationVersion application, Optional<Version> sourcePlatform,
                      Optional<ApplicationVersion> sourceApplication, String reason, Instant at) {
            this.id = id;
            this.platform = requireNonNull(platform);
            this.application = requireNonNull(application);
            this.sourcePlatform = sourcePlatform;
            this.sourceApplication = sourceApplication;
            this.reason = requireNonNull(reason);
            this.at = requireNonNull(at);
        }

        public static JobRun triggering(Version platform, ApplicationVersion application, Optional<Version> sourcePlatform,
                                        Optional<ApplicationVersion> sourceApplication, String reason, Instant at) {
            return new JobRun(-1, platform, application, sourcePlatform, sourceApplication, reason, at);
        }

        public JobRun completion(long id, Instant at) {
            return new JobRun(id, platform, application, sourcePlatform, sourceApplication, reason, at);
        }

        /** Returns the id of this run of this job, or -1 if not known */
        public long id() { return id; }

        /** Returns the Vespa version used on this run */
        public Version platform() { return platform; }

        /** Returns the Vespa version this run upgraded from, if already deployed */
        public Optional<Version> sourcePlatform() { return sourcePlatform; }

        /** Returns the application version used in this run */
        public ApplicationVersion application() { return application; }

        /** Returns the application version this run upgraded from, if already deployed */
        public Optional<ApplicationVersion> sourceApplication() { return sourceApplication; }

        /** Returns a human-readable reason for this particular job run */
        public String reason() { return reason; }

        /** Returns the time if this triggering or completion */
        public Instant at() { return at; }

        @Override
        public String toString() {
            return "job run " + id + " of version " + platform +
                   (sourcePlatform.map(version -> " (" + version + ")").orElse("")) +
                   " " + application.id() +
                   (sourceApplication.map(version -> " (" + version.id() + ")").orElse("")) +
                   " at " + at;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof JobRun)) return false;

            JobRun run = (JobRun) o;

            if (id != run.id) return false;
            if (!platform.equals(run.platform)) return false;
            if (!application.equals(run.application)) return false;
            if (!sourcePlatform.equals(run.sourcePlatform)) return false;
            if (!sourceApplication.equals(run.sourceApplication)) return false;
            return at.equals(run.at);
        }

        @Override
        public int hashCode() {
            int result = (int) (id ^ (id >>> 32));
            result = 31 * result + platform.hashCode();
            result = 31 * result + application.hashCode();
            result = 31 * result + sourcePlatform.hashCode();
            result = 31 * result + sourceApplication.hashCode();
            result = 31 * result + at.hashCode();
            return result;
        }
    }

}
