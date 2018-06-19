package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;

/**
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
