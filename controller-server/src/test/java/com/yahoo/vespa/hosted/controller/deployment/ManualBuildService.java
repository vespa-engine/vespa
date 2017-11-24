package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobReport;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError.unknown;

/**
 * @author jvenstad
 */
public class ManualBuildService implements BuildService {

    private final Set<BuildJob> jobs;
    private final ApplicationController applications;

    public ManualBuildService(ApplicationController applications) {
        this.applications = applications;

        jobs = new HashSet<>();
    }

    @Override
    public boolean trigger(BuildJob buildJob) {
        if (jobs.add(buildJob))
            throw new IllegalStateException("Asked to trigger " + buildJob + ", but this was already queued, which we try to avoid.");
        return true;
    }

    /** Complete the given job, and notify of completion. */
    public void complete(ApplicationId applicationId, JobType jobType) {
        if ( ! jobs.remove(new BuildJob(applications.require(applicationId).deploymentJobs().projectId().get(), jobType.jobName())))
            throw new IllegalArgumentException("Asked to complete " + jobType + " for " + applicationId + ", but this was not running.");
    }

    JobReport jobReport(ApplicationId applicationId, JobType jobType, boolean success) {
        return jobReport(applicationId, jobType, Optional.ofNullable(success ? null : unknown));
    }

    JobReport jobReport(ApplicationId applicationId, JobType jobType, Optional<JobError> jobError) {
        return new JobReport(
                applicationId,
                jobType,
                applications.require(applicationId).deploymentJobs().projectId().get(),
                42,
                jobError
        );
    }

}
