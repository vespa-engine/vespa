// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService.BuildJob;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author jvenstad
 */
public class DeploymentQueueTest {

    @Test
    public void testJobOffering() {
        DeploymentTester tester = new DeploymentTester();
        DeploymentQueue deploymentQueue = new DeploymentQueue(tester.controller(), tester.controller().curator());

        int project1 = 1;
        int project2 = 2;
        int project3 = 3;

        ApplicationId app1 = tester.createApplication("app1", "tenant", project1, null).id();
        ApplicationId app2 = tester.createApplication("app2", "tenant", project2, null).id();
        ApplicationId app3 = tester.createApplication("app3", "tenant", project3, null).id();

        // Trigger jobs in capacity constrained environment
        deploymentQueue.addJob(app1, JobType.systemTest, false);
        deploymentQueue.addJob(app2, JobType.systemTest, true);
        deploymentQueue.addJob(app3, JobType.stagingTest, false);

        // Trigger jobs in non-capacity constrained environment
        deploymentQueue.addJob(app1, JobType.productionUsWest1, false);
        deploymentQueue.addJob(app2, JobType.productionUsWest1, false);
        deploymentQueue.addJob(app3, JobType.productionUsWest1, false);

        assertEquals("Each offer contains a single job from each capacity constrained environment, and all other jobs.",
                     Arrays.asList(new BuildJob(project2, JobType.systemTest.jobName()),
                                   new BuildJob(project3, JobType.stagingTest.jobName()),
                                   new BuildJob(project1, JobType.productionUsWest1.jobName()),
                                   new BuildJob(project2, JobType.productionUsWest1.jobName()),
                                   new BuildJob(project3, JobType.productionUsWest1.jobName())),
                     deploymentQueue.takeJobsToRun());

        assertEquals("The system test job for project 1 was pushed back in the queue by that for project 2.",
                     Collections.singletonList(new BuildJob(project1, JobType.systemTest.jobName())),
                     deploymentQueue.takeJobsToRun());

        assertEquals("No jobs are left.",
                     Collections.emptyList(),
                     deploymentQueue.takeJobsToRun());
    }

}
