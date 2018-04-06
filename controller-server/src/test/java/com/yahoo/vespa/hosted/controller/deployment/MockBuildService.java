// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jvenstad
 */
public class MockBuildService implements BuildService {

    private final List<BuildJob> jobs = new ArrayList<>();

    @Override
    public boolean trigger(BuildJob buildJob) {
        return jobs.add(buildJob);
    }

    /** List all running jobs. */
    public List<BuildJob> jobs() {
        return new ArrayList<>(jobs);
    }

    /** List and remove all running jobs. */
    public List<BuildJob> takeJobsToRun() {
        List<BuildJob> jobsToRun = jobs();
        jobs.clear();
        return jobsToRun;
    }

    /** Remove all running jobs for the given project. */
    public boolean removeJob(long projectId, JobType jobType) {
        return jobs.remove(new BuildJob(projectId, jobType.jobName()));
    }

}
