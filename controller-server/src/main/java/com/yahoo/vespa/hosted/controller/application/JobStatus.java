// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.Controller;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

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
        Objects.requireNonNull(type, "jobType cannot be null");
        Objects.requireNonNull(jobError, "jobError cannot be null");
        Objects.requireNonNull(lastTriggered, "lastTriggered cannot be null");
        Objects.requireNonNull(lastCompleted, "lastCompleted cannot be null");
        Objects.requireNonNull(firstFailing, "firstFailing cannot be null");
        Objects.requireNonNull(lastSuccess, "lastSuccess cannot be null");

        this.type = type;
        this.jobError = jobError;
        this.lastTriggered = lastTriggered;
        this.lastCompleted = lastCompleted;
        this.firstFailing = firstFailing;
        this.lastSuccess = lastSuccess;
    }

    /** Returns an empty job status */
    public static JobStatus initial(DeploymentJobs.JobType type) {
        return new JobStatus(type, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()); 
    }

    public JobStatus withTriggering(Version version, Optional<ApplicationRevision> revision, boolean upgrade,
                                    Instant triggerTime) {
        return new JobStatus(type, jobError, Optional.of(new JobRun(version, revision, upgrade, triggerTime)),
                             lastCompleted, firstFailing, lastSuccess);
    }

    public JobStatus withCompletion(Optional<DeploymentJobs.JobError> jobError, Instant completionTime, Controller controller) {
        Version version;
        Optional<ApplicationRevision> revision;
        boolean upgrade;
        if (type == DeploymentJobs.JobType.component) { // not triggered by us
            version = controller.systemVersion();
            revision = Optional.empty();
            upgrade = false;
        }
        else if (! lastTriggered.isPresent()) {
            throw new IllegalStateException("Got notified about completion of " + this +
                                            ", but that has not been triggered nor deployed");

        }
        else {
            version = lastTriggered.get().version();
            revision = lastTriggered.get().revision();
            upgrade = lastTriggered.get().upgrade();
        }

        JobRun thisCompletion = new JobRun(version, revision, upgrade, completionTime);

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
    public boolean isSuccess() { return ! jobError.isPresent(); }
    
    /** Returns true if last triggered is newer than last completed and was started after timeoutLimit */
    public boolean isRunning(Instant timeoutLimit) {
        if ( ! lastTriggered.isPresent()) return false;
        if (lastTriggered.get().at().isBefore(timeoutLimit)) return false;
        if ( ! lastCompleted.isPresent()) return true;
        return lastTriggered.get().at().isAfter(lastCompleted.get().at());
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

    /** Returns whether the job last completed for the given change */
    public boolean lastCompletedFor(Change change) {
        if (change instanceof Change.ApplicationChange) {
            Change.ApplicationChange applicationChange = (Change.ApplicationChange) change;
            return lastCompleted().isPresent() && lastCompleted().get().revision().equals(applicationChange.revision());
        } else if (change instanceof Change.VersionChange) {
            Change.VersionChange versionChange = (Change.VersionChange) change;
            return lastCompleted().isPresent() && lastCompleted().get().version().equals(versionChange.version());
        }
        throw new IllegalArgumentException("Unexpected change: " + change.getClass());
    }

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
        
        private final Version version;
        private final Optional<ApplicationRevision> revision;
        private final Instant at;
        private final boolean upgrade;
        
        public JobRun(Version version, Optional<ApplicationRevision> revision, boolean upgrade, Instant at) {
            Objects.requireNonNull(version, "version cannot be null");
            Objects.requireNonNull(revision, "revision cannot be null");
            Objects.requireNonNull(at, "at cannot be null");
            this.version = version;
            this.revision = revision;
            this.upgrade = upgrade;
            this.at = at;
        }

        /** Returns whether this job run was a Vespa upgrade */
        public boolean upgrade() { return upgrade; }
        
        /** The Vespa version used on this run */
        public Version version() { return version; }
        
        /** The application revision used for this run, or empty when not known */
        public Optional<ApplicationRevision> revision() { return revision; }
        
        /** The time if this triggering or completion */
        public Instant at() { return at; }

        @Override
        public int hashCode() {
            return Objects.hash(version, revision, upgrade, at);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof JobRun)) return false;
            JobRun jobRun = (JobRun) o;
            return upgrade == jobRun.upgrade &&
                   Objects.equals(version, jobRun.version) &&
                   Objects.equals(revision, jobRun.revision) &&
                   Objects.equals(at, jobRun.at);
        }

        @Override
        public String toString() { return "job run of version " + (upgrade() ? "upgrade " : "") + version + " "
                                          + revision + " at " + at; }
        
    }

}
