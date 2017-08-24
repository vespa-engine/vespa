// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService.BuildJob;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
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
        ControllerTester tester = new ControllerTester();
        BuildSystem buildSystem = new PolledBuildSystem(tester.controller(), new MockCuratorDb());

        long fooProjectId = 1;
        long barProjectId = 2;
        ApplicationId foo = tester.createAndDeploy("tenant1", "domain1", "app1",
                                                   Environment.prod, fooProjectId).id();
        ApplicationId bar = tester.createAndDeploy("tenant2", "domain2", "app2",
                                                   Environment.prod, barProjectId).id();

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
