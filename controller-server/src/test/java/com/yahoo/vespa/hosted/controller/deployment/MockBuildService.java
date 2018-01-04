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
public class MockBuildService implements BuildService {

    @Override
    public boolean trigger(BuildJob buildJob) {
        return true;
    }

}
