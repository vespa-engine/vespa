// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.google.common.collect.ImmutableMap;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Information about which deployment jobs an application should run and their current status.
 * This is immutable.
 *
 * @author bratseth
 * @author mpolden
 */
public class DeploymentJobs {

    private final Optional<Long> projectId;
    private final ImmutableMap<JobType, JobStatus> status;
    private final Optional<IssueId> issueId;

    public DeploymentJobs(Optional<Long> projectId, Collection<JobStatus> jobStatusEntries,
                          Optional<IssueId> issueId) {
        this(projectId, asMap(jobStatusEntries), issueId);
    }

    private DeploymentJobs(Optional<Long> projectId, Map<JobType, JobStatus> status, Optional<IssueId> issueId) {
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
    public DeploymentJobs withCompletion(JobReport report, ApplicationVersion applicationVersion,
                                         Instant notificationTime, Controller controller) {
        Map<JobType, JobStatus> status = new LinkedHashMap<>(this.status);
        status.compute(report.jobType(), (type, job) -> {
            if (job == null) job = JobStatus.initial(report.jobType());
            return job.withCompletion(report.buildNumber(), applicationVersion, report.jobError(), notificationTime,
                                      controller);
        });
        return new DeploymentJobs(Optional.of(report.projectId()), status, issueId);
    }

    public DeploymentJobs withTriggering(JobType jobType,
                                         Version version,
                                         ApplicationVersion applicationVersion,
                                         String reason,
                                         Instant triggerTime) {
        Map<JobType, JobStatus> status = new LinkedHashMap<>(this.status);
        status.compute(jobType, (type, job) -> {
            if (job == null) job = JobStatus.initial(jobType);
            return job.withTriggering(version,
                                      applicationVersion,
                                      reason,
                                      triggerTime);
        });
        return new DeploymentJobs(projectId, status, issueId);
    }

    public DeploymentJobs withProjectId(long projectId) {
        return new DeploymentJobs(Optional.of(projectId), status, issueId);
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
        return ! JobList.from(status.values()).failing().isEmpty();
    }

    /** Returns whether any job is currently in progress */
    public boolean isRunning(Instant timeoutLimit) {
        return ! JobList.from(status.values()).running(timeoutLimit).isEmpty();
    }

    /** Returns whether the given job type is currently running and was started after timeoutLimit */
    public boolean isRunning(JobType jobType, Instant timeoutLimit) {
        JobStatus jobStatus = status.get(jobType);
        if ( jobStatus == null) return false;
        return jobStatus.isRunning(timeoutLimit);
    }

    /** Returns whether change can be deployed to the given environment */
    public boolean isDeployableTo(Environment environment, Change change) {
        if (environment == null || ! change.isPresent()) {
            return true;
        }
        if (environment == Environment.staging) {
            return isSuccessful(change, JobType.systemTest);
        } else if (environment == Environment.prod) {
            return isSuccessful(change, JobType.stagingTest);
        }
        return true; // other environments do not have any preconditions
    }

    /** Returns the last successful application version for the given job */
    public Optional<ApplicationVersion> lastSuccessfulApplicationVersionFor(JobType jobType) {
        return Optional.ofNullable(jobStatus().get(jobType))
                       .flatMap(JobStatus::lastSuccess)
                       .map(JobStatus.JobRun::applicationVersion);
    }

    /**
     * Returns the id of the Screwdriver project running these deployment jobs
     * - or empty when this is not known or does not exist.
     * It is not known until the jobs have run once and reported back to the controller.
     */
    public Optional<Long> projectId() { return projectId; }

    public Optional<IssueId> issueId() { return issueId; }

    /** Returns whether the job of the given type has completed successfully for the given change */
    private boolean isSuccessful(Change change, JobType jobType) {
        return Optional.ofNullable(jobStatus().get(jobType))
                       .flatMap(JobStatus::lastSuccess)
                       .filter(status -> status.lastCompletedWas(change))
                       .isPresent();
    }

    private static Optional<Long> requireId(Optional<Long> id, String message) {
        Objects.requireNonNull(id, message);
        if ( ! id.isPresent()) {
            return id;
        }
        if (id.get() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return id;
    }

    /** Job types that exist in the build system */
    public enum JobType {
//     | enum name ------------| job name ------------------| Zone in main system ---------------------------------------| Zone in CD system -------------------------------------------
        component              ("component"                 , null                                                       , null                                                        ),
        systemTest             ("system-test"               , ZoneId.from("test"   , "us-east-1")      , ZoneId.from("test"   , "cd-us-central-1")),
        stagingTest            ("staging-test"              , ZoneId.from("staging", "us-east-3")      , ZoneId.from("staging", "cd-us-central-1")),
        productionCorpUsEast1  ("production-corp-us-east-1" , ZoneId.from("prod"   , "corp-us-east-1") , null                                                        ),
        productionUsEast3      ("production-us-east-3"      , ZoneId.from("prod"   , "us-east-3")      , null                                                        ),
        productionUsWest1      ("production-us-west-1"      , ZoneId.from("prod"   , "us-west-1")      , null                                                        ),
        productionUsCentral1   ("production-us-central-1"   , ZoneId.from("prod"   , "us-central-1")   , null                                                        ),
        productionApNortheast1 ("production-ap-northeast-1" , ZoneId.from("prod"   , "ap-northeast-1") , null                                                        ),
        productionApNortheast2 ("production-ap-northeast-2" , ZoneId.from("prod"   , "ap-northeast-2") , null                                                        ),
        productionApSoutheast1 ("production-ap-southeast-1" , ZoneId.from("prod"   , "ap-southeast-1") , null                                                        ),
        productionEuWest1      ("production-eu-west-1"      , ZoneId.from("prod"   , "eu-west-1")      , null                                                        ),
        productionAwsUsEast1a  ("production-aws-us-east-1a" , ZoneId.from("prod"   , "aws-us-east-1a") , null                                                        ),
        productionCdUsEast1a   ("production-cd-us-east-1a"  , null                                                       , ZoneId.from("prod"   , "cd-us-east-1a")   ),
        productionCdUsCentral1 ("production-cd-us-central-1", null                                                       , ZoneId.from("prod"    , "cd-us-central-1")),
        productionCdUsCentral2 ("production-cd-us-central-2", null                                                       , ZoneId.from("prod"    , "cd-us-central-2"));

        private final String jobName;
        private final ImmutableMap<SystemName, ZoneId> zones;

        JobType(String jobName, ZoneId mainZone, ZoneId cdZone) {
            this.jobName = jobName;
            ImmutableMap.Builder<SystemName, ZoneId> builder = ImmutableMap.builder();
            if (mainZone != null) builder.put(SystemName.main, mainZone);
            if (cdZone != null) builder.put(SystemName.cd, cdZone);
            this.zones = builder.build();
        }

        public String jobName() { return jobName; }

        /** Returns the zone for this job in the given system, or empty if this job does not have a zone */
        public Optional<ZoneId> zone(SystemName system) {
            return Optional.ofNullable(zones.get(system));
        }

        /** Returns whether this is a production job */
        public boolean isProduction() { return environment() == Environment.prod; }

        /** Returns whether this is an automated test job */
        public boolean isTest() { return environment() != null && environment().isTest(); }

        /** Returns the environment of this job type, or null if it does not have an environment */
        public Environment environment() {
            switch (this) {
                case component: return null;
                case systemTest: return Environment.test;
                case stagingTest: return Environment.staging;
                default: return Environment.prod;
            }
        }

        /** Returns the region of this job type, or null if it does not have a region */
        public Optional<RegionName> region(SystemName system) {
            return zone(system).map(ZoneId::region);
        }

        public static JobType fromJobName(String jobName) {
            return Stream.of(values())
                    .filter(jobType -> jobType.jobName.equals(jobName))
                    .findAny().orElseThrow(() -> new IllegalArgumentException("Unknown job name '" + jobName + "'"));
        }

        /** Returns the job type for the given zone */
        public static Optional<JobType> from(SystemName system, ZoneId zone) {
            return Stream.of(values())
                    .filter(job -> job.zone(system).filter(zone::equals).isPresent())
                    .findAny();
        }

        /** Returns the job job type for the given environment and region or null if none */
        public static Optional<JobType> from(SystemName system, Environment environment, RegionName region) {
            switch (environment) {
                case test: return Optional.of(systemTest);
                case staging: return Optional.of(stagingTest);
            }
            return from(system, ZoneId.from(environment, region));
        }

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

    }

    public enum JobError {
        unknown,
        outOfCapacity
    }

}
