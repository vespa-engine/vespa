package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;

import java.util.Optional;

/**
 * Wraps a JobController as a BuildService.
 *
 * Shall be inlined when the {@link DelegatingBuildService} delegates all jobs to it.
 *
 * @author jonmv
 */
public class InternalBuildService implements BuildService {

    private final JobController jobs;

    public InternalBuildService(JobController jobs) {
        this.jobs = jobs;
    }

    @Override
    public void trigger(BuildJob buildJob) {
        jobs.run(buildJob.applicationId(), JobType.fromJobName(buildJob.jobName()));
    }

    @Override
    public JobState stateOf(BuildJob buildJob) {
        Optional<RunStatus> run = jobs.last(buildJob.applicationId(), JobType.fromJobName(buildJob.jobName()));
        return run.isPresent() && ! run.get().hasEnded() ? JobState.running : JobState.idle;
    }

    @Override
    public boolean builds(BuildJob buildJob) {
        return jobs.builds(buildJob.applicationId());
    }

}
