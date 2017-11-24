package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;

import java.util.List;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;
import static org.junit.Assert.assertEquals;

/**
 * @author jvenstad
 */
public class PushingBuildSystemTest {

    public void testTriggering() {
        DeploymentTester tester = new DeploymentTester();
        MockTimeline timeline = new MockTimeline(tester.clock());
        JobControl jobControl = new JobControl(tester.controller().curator());
        BuildSystem buildSystem = new PushingBuildSystem(tester.controller(), jobControl, new MockBuildService(tester.controllerTester(), timeline));

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
        buildSystem.addJob(app1, systemTest, false);
        buildSystem.addJob(app2, systemTest, false);
        buildSystem.addJob(app3, systemTest, false);

        // A limited number of jobs are offered at a time:
        // First offer
        List<BuildService.BuildJob> nextJobs = buildSystem.takeJobsToRun();
        assertEquals(2, nextJobs.size());
        assertEquals(project1, nextJobs.get(0).projectId());
        assertEquals(project2, nextJobs.get(1).projectId());

        // Second offer
        nextJobs = buildSystem.takeJobsToRun();
        assertEquals(1, nextJobs.size());
        assertEquals(project3, nextJobs.get(0).projectId());
    }

}
