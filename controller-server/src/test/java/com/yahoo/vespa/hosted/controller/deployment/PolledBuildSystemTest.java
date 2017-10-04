// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService.BuildJob;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
@RunWith(Parameterized.class)
public class PolledBuildSystemTest {

    @Parameterized.Parameters(name = "jobType={0}")
    public static Iterable<? extends Object> capacityConstrainedJobs() {
        return Arrays.asList(JobType.systemTest, JobType.stagingTest);
    }

    private final JobType jobType;

    public PolledBuildSystemTest(JobType jobType) {
        this.jobType = jobType;
    }

    @Test
    public void throttle_capacity_constrained_jobs() {
        DeploymentTester tester = new DeploymentTester();
        BuildSystem buildSystem = new PolledBuildSystem(tester.controller(), new MockCuratorDb());

        int fooProjectId = 1;
        int barProjectId = 2;
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .build();
        ApplicationId foo = tester.createAndDeploy("app1", fooProjectId, applicationPackage).id();
        ApplicationId bar = tester.createAndDeploy("app2", barProjectId, applicationPackage).id();

        // Trigger jobs in capacity constrained environment
        buildSystem.addJob(foo, jobType, false);
        buildSystem.addJob(bar, jobType, false);

        // Capacity constrained jobs are returned one a at a time
        List<BuildJob> nextJobs = buildSystem.takeJobsToRun();
        assertEquals(1, nextJobs.size());
        assertEquals(fooProjectId, nextJobs.get(0).projectId());

        nextJobs = buildSystem.takeJobsToRun();
        assertEquals(1, nextJobs.size());
        assertEquals(barProjectId, nextJobs.get(0).projectId());
    }

}
