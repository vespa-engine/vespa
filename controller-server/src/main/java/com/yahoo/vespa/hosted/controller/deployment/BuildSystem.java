// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService.BuildJob;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;

import java.util.List;

/**
 * @author jvenstad
 * @author mpolden
 */
public interface BuildSystem {

    /**
     * Add a job for the given application to the build system
     *
     * @param application the application owning the job
     * @param jobType the job type to be queued
     * @param first whether the job should be added to the front of the queue
     */
    void addJob(ApplicationId application, JobType jobType, boolean first);

    /** Remove and return a list of jobs which should be run now */
    List<BuildJob> takeJobsToRun();

    /** Get a list of all jobs currently waiting to run */
    List<BuildJob> jobs();
    
    /** Removes all queued jobs for the given application */
    void removeJobs(ApplicationId applicationId);

}
