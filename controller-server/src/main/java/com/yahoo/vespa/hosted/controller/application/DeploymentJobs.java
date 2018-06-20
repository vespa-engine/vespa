// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Information about which deployment jobs an application should run and their current status.
 * This is immutable.
 *
 * @author bratseth
 * @author mpolden
 */
public class DeploymentJobs {

    private final OptionalLong projectId;
    private final ImmutableMap<JobType, JobStatus> status;
    private final Optional<IssueId> issueId;

    public DeploymentJobs(OptionalLong projectId, Collection<JobStatus> jobStatusEntries,
                          Optional<IssueId> issueId) {
        this(projectId, asMap(jobStatusEntries), issueId);
    }

    private DeploymentJobs(OptionalLong projectId, Map<JobType, JobStatus> status, Optional<IssueId> issueId) {
        requireId(projectId, "projectId must be a positive integer");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(issueId, "issueId cannot be null");
        this.projectId = projectId;
        this.status = ImmutableMap.copyOf(status);
        this.issueId = issueId;
    }

    private static Map<JobType, JobStatus> asMap(Collection<JobStatus> jobStatusEntries) {
        ImmutableMap.Builder<JobType, JobStatus> b = new ImmutableMap.Builder<>();
        for (JobStatus jobStatusEntry : jobStatusEntries)
            b.put(jobStatusEntry.type(), jobStatusEntry);
        return b.build();
    }

    /** Return a new instance with the given completion */
    public DeploymentJobs withCompletion(long projectId, JobType jobType, JobStatus.JobRun completion, Optional<JobError> jobError) {
        Map<JobType, JobStatus> status = new LinkedHashMap<>(this.status);
        status.compute(jobType, (type, job) -> {
            if (job == null) job = JobStatus.initial(jobType);
            return job.withCompletion(completion, jobError);
        });
        return new DeploymentJobs(OptionalLong.of(projectId), status, issueId);
    }

    public DeploymentJobs withTriggering(JobType jobType, JobStatus.JobRun jobRun) {
        Map<JobType, JobStatus> status = new LinkedHashMap<>(this.status);
        status.compute(jobType, (__, job) -> {
            if (job == null) job = JobStatus.initial(jobType);
            return job.withTriggering(jobRun);
        });
        return new DeploymentJobs(projectId, status, issueId);
    }

    public DeploymentJobs withProjectId(OptionalLong projectId) {
        return new DeploymentJobs(projectId, status, issueId);
    }

    public DeploymentJobs with(IssueId issueId) {
        return new DeploymentJobs(projectId, status, Optional.ofNullable(issueId));
    }

    public DeploymentJobs without(JobType job) {
        Map<JobType, JobStatus> status = new HashMap<>(this.status);
        status.remove(job);
        return new DeploymentJobs(projectId, status, issueId);
    }

    /** Returns an immutable map of the status entries in this */
    public Map<JobType, JobStatus> jobStatus() { return status; }

    /** Returns whether this has some job status which is not a success */
    public boolean hasFailures() {
        return ! JobList.from(status.values())
                        .failing()
                        .not().failingBecause(JobError.outOfCapacity)
                        .isEmpty();
    }

    /** Returns the JobStatus of the given JobType, or empty. */
    public Optional<JobStatus> statusOf(JobType jobType) {
        return Optional.ofNullable(jobStatus().get(jobType));
    }

    /**
     * Returns the id of the Screwdriver project running these deployment jobs
     * - or empty when this is not known or does not exist.
     * It is not known until the jobs have run once and reported back to the controller.
     */
    public OptionalLong projectId() { return projectId; }

    public Optional<IssueId> issueId() { return issueId; }

    private static OptionalLong requireId(OptionalLong id, String message) {
        Objects.requireNonNull(id, message);
        if ( ! id.isPresent()) {
            return id;
        }
        if (id.getAsLong() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return id;
    }

    /** A job report. This class is immutable. */
    public static class JobReport {

        private final ApplicationId applicationId;
        private final JobType jobType;
        private final long projectId;
        private final long buildNumber;
        private final Optional<SourceRevision> sourceRevision;
        private final Optional<JobError> jobError;

        public JobReport(ApplicationId applicationId, JobType jobType, long projectId, long buildNumber,
                         Optional<SourceRevision> sourceRevision, Optional<JobError> jobError) {
            Objects.requireNonNull(applicationId, "applicationId cannot be null");
            Objects.requireNonNull(jobType, "jobType cannot be null");
            Objects.requireNonNull(sourceRevision, "sourceRevision cannot be null");
            Objects.requireNonNull(jobError, "jobError cannot be null");

            if (jobType == JobType.component && !sourceRevision.isPresent()) {
                throw new IllegalArgumentException("sourceRevision is required for job " + jobType);
            }

            this.applicationId = applicationId;
            this.projectId = projectId;
            this.buildNumber = buildNumber;
            this.jobType = jobType;
            this.sourceRevision = sourceRevision;
            this.jobError = jobError;
        }

        public ApplicationId applicationId() { return applicationId; }
        public JobType jobType() { return jobType; }
        public long projectId() { return projectId; }
        public long buildNumber() { return buildNumber; }
        public boolean success() { return ! jobError.isPresent(); }
        public Optional<SourceRevision> sourceRevision() { return sourceRevision; }
        public Optional<JobError> jobError() { return jobError; }
        public BuildService.BuildJob buildJob() { return BuildService.BuildJob.of(applicationId, projectId, jobType.jobName()); }

    }

    public enum JobError {
        unknown,
        outOfCapacity
    }

}
