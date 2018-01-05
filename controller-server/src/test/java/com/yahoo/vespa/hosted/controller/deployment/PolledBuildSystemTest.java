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
    public static Iterable<?> capacityConstrainedJobs() {
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

        int project1 = 1;
        int project2 = 2;
        int project3 = 3;
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .build();
        ApplicationId app1 = tester.createAndDeploy("app1", project1, applicationPackage).id();
        ApplicationId app2 = tester.createAndDeploy("app2", project2, applicationPackage).id();
        ApplicationId app3 = tester.createAndDeploy("app3", project3, applicationPackage).id();

        // Trigger jobs in capacity constrained environment
        buildSystem.addJob(app1, jobType, false);
        buildSystem.addJob(app2, jobType, false);
        buildSystem.addJob(app3, jobType, false);

        // A limited number of jobs are offered at a time:
        // First offer
        List<BuildJob> nextJobs = buildSystem.takeJobsToRun();
        assertEquals(2, nextJobs.size());
        assertEquals(project1, nextJobs.get(0).projectId());
        assertEquals(project2, nextJobs.get(1).projectId());

        // Second offer
        nextJobs = buildSystem.takeJobsToRun();
        assertEquals(1, nextJobs.size());
        assertEquals(project3, nextJobs.get(0).projectId());
    }

}
