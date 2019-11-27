// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.UnaryOperator;

/**
 * Information about which deployment jobs an application should run and their current status.
 * This is immutable.
 *
 * @author bratseth
 * @author mpolden
 */
public class DeploymentJobs {

    private final ImmutableMap<JobType, JobStatus> status;

    public DeploymentJobs(Collection<JobStatus> jobStatusEntries) {
        this.status = ImmutableMap.copyOf((Iterable<Map.Entry<JobType, JobStatus>>)
                                                  jobStatusEntries.stream()
                                                                  .map(job -> Map.entry(job.type(), job))::iterator);
    }

    /** Return a new instance with the given job update applied. */
    public DeploymentJobs withUpdate(JobType jobType, UnaryOperator<JobStatus> update) {
        Map<JobType, JobStatus> status = new LinkedHashMap<>(this.status);
        status.compute(jobType, (type, job) -> {
            if (job == null) job = JobStatus.initial(jobType);
            return update.apply(job);
        });
        return new DeploymentJobs(status.values());
    }

    /** Return a new instance with the given completion */
    public DeploymentJobs withCompletion(JobType jobType, JobStatus.JobRun completion, Optional<JobError> jobError) {
        return withUpdate(jobType, job -> job.withCompletion(completion, jobError));
    }

    public DeploymentJobs withTriggering(JobType jobType, JobStatus.JobRun jobRun) {
        return withUpdate(jobType, job -> job.withTriggering(jobRun));
    }

    public DeploymentJobs withPause(JobType jobType, OptionalLong pausedUntil) {
        return withUpdate(jobType, job -> job.withPause(pausedUntil));
    }

    public DeploymentJobs without(JobType job) {
        Map<JobType, JobStatus> status = new LinkedHashMap<>(this.status);
        status.remove(job);
        return new DeploymentJobs(status.values());
    }

    /** Returns an immutable map of the status entries in this */
    public Map<JobType, JobStatus> jobStatus() { return status; }

    /** Returns the JobStatus of the given JobType, or empty. */
    public Optional<JobStatus> statusOf(JobType jobType) {
        return Optional.ofNullable(jobStatus().get(jobType));
    }

    /** A job report. This class is immutable. */
    public static class JobReport {

        private final ApplicationId applicationId;
        private final JobType jobType;
        private final long projectId;
        private final long buildNumber;
        private final Optional<ApplicationVersion> version;
        private final Optional<JobError> jobError;

        private JobReport(ApplicationId applicationId, JobType jobType, long projectId, long buildNumber,
                          Optional<JobError> jobError, Optional<ApplicationVersion> version) {
            Objects.requireNonNull(applicationId, "applicationId cannot be null");
            Objects.requireNonNull(jobType, "jobType cannot be null");
            Objects.requireNonNull(jobError, "jobError cannot be null");
            Objects.requireNonNull(version, "version cannot be null");
            if (version.isPresent() && version.get().buildNumber().isPresent() && version.get().buildNumber().getAsLong() != buildNumber)
                throw new IllegalArgumentException("Build number in application version must match the one given here.");

            this.applicationId = applicationId;
            this.projectId = projectId;
            this.buildNumber = buildNumber;
            this.jobType = jobType;
            this.jobError = jobError;
            this.version = version;
        }

        public static JobReport ofJob(ApplicationId applicationId, JobType jobType, long buildNumber, Optional<JobError> jobError) {
            return new JobReport(applicationId, jobType, -1, buildNumber, jobError, Optional.empty());
        }

        public ApplicationId applicationId() { return applicationId; }
        public JobType jobType() { return jobType; }
        public long projectId() { return projectId; }
        public long buildNumber() { return buildNumber; }
        public boolean success() { return ! jobError.isPresent(); }
        public Optional<ApplicationVersion> version() { return version; }
        public Optional<JobError> jobError() { return jobError; }

    }

    public enum JobError {
        unknown,
        outOfCapacity
    }

}
