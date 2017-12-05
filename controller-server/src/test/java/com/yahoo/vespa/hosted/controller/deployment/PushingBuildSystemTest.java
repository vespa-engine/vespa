package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import org.junit.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;
import static org.junit.Assert.assertEquals;

/**
 * @author jvenstad
 */
public class PushingBuildSystemTest {

    @Test
    public void testTriggering() {
        DeploymentTester tester = new DeploymentTester();
        JobControl jobControl = new JobControl(tester.controller().curator());
        BuildSystem buildSystem = new PushingBuildSystem(tester.controller(),
                                                         Duration.ofDays(1),
                                                         jobControl,
                                                         new MockBuildService(),
                                                         Runnable::run);

        // Make sure the applications exist in the controller, as the build system uses this information.
        ApplicationId app1 = tester.createAndDeploy("app1", 1, "default-policy").id();
        ApplicationId app2 = tester.createAndDeploy("app2", 2, "default-policy").id();
        ApplicationId app3 = tester.createAndDeploy("app3", 3, "default-policy").id();

        // Trigger jobs in a capacity-constrained environment.
        buildSystem.addJob(app1, systemTest, false);
        buildSystem.addJob(app2, systemTest, false);
        buildSystem.addJob(app3, systemTest, false);

        // Trigger jobs in a non-constrained environment.
        buildSystem.addJob(app1, productionUsWest1, false);
        buildSystem.addJob(app2, productionUsWest1, false);
        buildSystem.addJob(app3, productionUsWest1, false);

        // A single capacity-constrained job is triggered each run.
        List<BuildService.BuildJob> nextJobs = buildSystem.takeJobsToRun();
        assertEquals(2, nextJobs.size());
        assertEquals(project1, nextJobs.get(0).projectId());
        assertEquals(project2, nextJobs.get(1).projectId());

        nextJobs = buildSystem.takeJobsToRun();
        assertEquals(1, nextJobs.size());
        assertEquals(project3, nextJobs.get(0).projectId());
    }


    private static class MockBuildService implements BuildService {

        private final Set<String> jobs = new HashSet<>();

        @Override
        public boolean trigger(BuildJob buildJob) {
            jobs.add(buildJob.jobName() + "@" + buildJob.projectId());
            return true;
        }

    }

}
