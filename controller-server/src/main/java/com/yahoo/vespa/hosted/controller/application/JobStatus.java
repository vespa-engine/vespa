// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.Controller;

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

    private final DeploymentJobs.JobType type;

    private final Optional<JobRun> lastTriggered;
    private final Optional<JobRun> lastCompleted;
    private final Optional<JobRun> firstFailing;
    private final Optional<JobRun> lastSuccess;

    private final Optional<DeploymentJobs.JobError> jobError;

    /**
     * Used by the persistence layer (only) to create a complete JobStatus instance.
     * Other creation should be by using initial- and with- methods.
     */
    public JobStatus(DeploymentJobs.JobType type, Optional<DeploymentJobs.JobError> jobError,
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
        this.lastTriggered = type == DeploymentJobs.JobType.component ? Optional.empty() : lastTriggered;
        this.lastCompleted = lastCompleted;
        this.firstFailing = firstFailing;
        this.lastSuccess = lastSuccess;
    }

    /** Returns an empty job status */
    public static JobStatus initial(DeploymentJobs.JobType type) {
        return new JobStatus(type, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public JobStatus withTriggering(Version platform, ApplicationVersion application, String reason, Instant triggeredAt) {
        return withTriggering(new JobRun(-1, platform, application, reason, triggeredAt));
    }

    public JobStatus withTriggering(JobRun jobRun) {
        return new JobStatus(type, jobError, Optional.of(jobRun), lastCompleted, firstFailing, lastSuccess);
    }

    public JobStatus withCompletion(long runId, Optional<DeploymentJobs.JobError> jobError, Instant completionTime,
                                    Controller controller) {
        return withCompletion(runId, ApplicationVersion.unknown, jobError, completionTime, controller);
    }

    public JobStatus withCompletion(long runId, ApplicationVersion applicationVersion,
                                    Optional<DeploymentJobs.JobError> jobError, Instant completionTime,
                                    Controller controller) {
        Version version;
        String reason;
        if (type == DeploymentJobs.JobType.component) { // not triggered by us
            version = controller.systemVersion();
            reason = "Application commit";
        } else if ( ! lastTriggered.isPresent()) {
            throw new IllegalStateException("Got notified about completion of " + this +
                                            ", but that has neither been triggered nor deployed");

        } else {
            version = lastTriggered.get().version();
            applicationVersion = lastTriggered.get().applicationVersion();
            reason = lastTriggered.get().reason();
        }

        JobRun thisCompletion = new JobRun(runId, version, applicationVersion, reason, completionTime);

        Optional<JobRun> firstFailing = this.firstFailing;
        if (jobError.isPresent() &&  ! this.firstFailing.isPresent())
            firstFailing = Optional.of(thisCompletion);

        Optional<JobRun> lastSuccess = this.lastSuccess;
        if ( ! jobError.isPresent()) {
            lastSuccess = Optional.of(thisCompletion);
            firstFailing = Optional.empty();
        }

        return new JobStatus(type, jobError, lastTriggered, Optional.of(thisCompletion), firstFailing, lastSuccess);
    }

    public DeploymentJobs.JobType type() { return type; }

    /** Returns true unless this job last completed with a failure */
    public boolean isSuccess() {
        return lastCompleted().isPresent() && ! jobError.isPresent();
    }

    /** The error of the last completion, or empty if the last run succeeded */
    public Optional<DeploymentJobs.JobError> jobError() { return jobError; }

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
        private final Version version;
        private final ApplicationVersion applicationVersion;
        private final String reason;
        private final Instant at;

        public JobRun(long id, Version version, ApplicationVersion applicationVersion, String reason, Instant at) {
            this.id = id;
            this.version = requireNonNull(version);
            this.applicationVersion = requireNonNull(applicationVersion);
            this.reason = requireNonNull(reason);
            this.at = requireNonNull(at);
        }

        /** Returns the id of this run of this job, or -1 if not known */
        public long id() { return id; }

        /** Returns the Vespa version used on this run */
        public Version version() { return version; }

        /** Returns the application version used in this run */
        public ApplicationVersion applicationVersion() { return applicationVersion; }

        /** Returns a human-readable reason for this particular job run */
        public String reason() { return reason; }

        /** Returns the time if this triggering or completion */
        public Instant at() { return at; }

        @Override
        public String toString() {
            return "job run " + id + " of version " + version + " " + applicationVersion + " at " + at;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof JobRun)) return false;

            JobRun run = (JobRun) o;

            if (id != run.id) return false;
            if (!version.equals(run.version)) return false;
            if (!applicationVersion.equals(run.applicationVersion)) return false;
            return at.equals(run.at);
        }

        @Override
        public int hashCode() {
            int result = (int) (id ^ (id >>> 32));
            result = 31 * result + version.hashCode();
            result = 31 * result + applicationVersion.hashCode();
            result = 31 * result + at.hashCode();
            return result;
        }

    }

}
