package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;

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

    }

    @Override
    public JobState stateOf(BuildJob buildJob) {
        return null;
    }

    @Override
    public boolean builds(BuildJob buildJob) {
        return false;
    }

}
