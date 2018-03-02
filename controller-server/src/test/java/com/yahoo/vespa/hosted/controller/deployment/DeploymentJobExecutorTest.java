package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService.BuildJob;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * @author jvenstad
 */
public class DeploymentJobExecutorTest {

    @Test
    public void testMaintenance() {
        DeploymentTester tester = new DeploymentTester();
        JobControl jobControl = new JobControl(tester.controller().curator());

        int project1 = 1;
        int project2 = 2;
        int project3 = 3;

        ApplicationId app1 = tester.createApplication("app1", "tenant", project1, null).id();
        ApplicationId app2 = tester.createApplication("app2", "tenant", project2, null).id();
        ApplicationId app3 = tester.createApplication("app3", "tenant", project3, null).id();

        // Create a BuildService which always rejects jobs from project2, but accepts and runs all others.
        ArrayList<BuildJob> buildJobs = new ArrayList<>();
        BuildService buildService = buildJob -> buildJob.projectId() == project2 ? false : buildJobs.add(buildJob);

        DeploymentJobExecutor triggerer = new DeploymentJobExecutor(tester.controller(),
                                                                    Duration.ofDays(1),
                                                                    jobControl,
                                                                    buildService,
                                                                    Runnable::run);

        triggerer.maintain();
        assertEquals("No jobs are triggered initially.",
                     Collections.emptyList(),
                     buildJobs);

        // Trigger jobs in capacity constrained environment
        tester.deploymentQueue().addJob(app1, DeploymentJobs.JobType.systemTest, false, false, false);
        tester.deploymentQueue().addJob(app2, DeploymentJobs.JobType.systemTest, false, false, false);
        tester.deploymentQueue().addJob(app3, DeploymentJobs.JobType.systemTest, false, false, false);

        // Trigger jobs in non-capacity constrained environment
        tester.deploymentQueue().addJob(app1, DeploymentJobs.JobType.productionUsWest1, false, false, false);
        tester.deploymentQueue().addJob(app2, DeploymentJobs.JobType.productionUsWest1, false, false, false);
        tester.deploymentQueue().addJob(app3, DeploymentJobs.JobType.productionUsWest1, false, false, false);

        triggerer.maintain();
        assertEquals("One system test job and all production jobs not for app2 are triggered after one maintenance run.",
                     Arrays.asList(new BuildJob(project1, DeploymentJobs.JobType.systemTest.jobName()),
                                   new BuildJob(project1, DeploymentJobs.JobType.productionUsWest1.jobName()),
                                   new BuildJob(project3, DeploymentJobs.JobType.productionUsWest1.jobName())),
                     buildJobs);

        buildJobs.clear();
        triggerer.maintain();
        assertEquals("Next job in line fails to trigger in the build service.",
                     Collections.emptyList(),
                     buildJobs);

        buildJobs.clear();
        triggerer.maintain();
        assertEquals("Next job which was waiting for capacity is triggered on next run.",
                     Collections.singletonList(new BuildJob(project3, DeploymentJobs.JobType.systemTest.jobName())),
                     buildJobs);

        buildJobs.clear();
        triggerer.maintain();
        assertEquals("No jobs are left.",
                     Collections.emptyList(),
                     tester.deploymentQueue().takeJobsToRun());
    }

}
