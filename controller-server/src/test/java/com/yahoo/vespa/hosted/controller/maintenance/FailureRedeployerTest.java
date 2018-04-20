// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.SystemName;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.component;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class FailureRedeployerTest {

    @Test
    public void testRetryingFailedJobsDuringDeployment() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                .environment(Environment.prod)
                .region("us-east-3")
                .build();
        Version version = Version.fromString("5.0");
        tester.updateVersionStatus(version);

        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.productionUsEast3);

        // New version is released
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.readyJobTrigger().maintain();

        // Test environments pass
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.stagingTest);

        // Production job fails and is retried
        tester.clock().advance(Duration.ofSeconds(1)); // Advance time so that we can detect jobs in progress
        tester.deployAndNotify(app, applicationPackage, false, DeploymentJobs.JobType.productionUsEast3);
        assertEquals("Production job is retried", 1, tester.buildService().jobs().size());
        assertEquals("Application has pending upgrade to " + version, version, tester.application(app.id()).change().platform().get());

        // Another version is released, which cancels any pending upgrades to lower versions
        version = Version.fromString("5.2");
        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        tester.jobCompletion(DeploymentJobs.JobType.productionUsEast3).application(app).unsuccessful().submit();
        tester.readyJobTrigger().maintain();
        assertEquals("Application starts upgrading to new version", 1, tester.buildService().jobs().size());
        assertEquals("Application has pending upgrade to " + version, version, tester.application(app.id()).change().platform().get());

        // Failure re-deployer did not retry failing job for prod.us-east-3, since it no longer had an available change
        assertFalse("Job is not retried", tester.buildService().jobs().stream()
                                                .anyMatch(j -> j.jobName().equals(DeploymentJobs.JobType.productionUsEast3.jobName())));

        // Test environments pass
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.stagingTest);

        // Production job fails again, and is retried
        tester.deployAndNotify(app, applicationPackage, false, DeploymentJobs.JobType.productionUsEast3);
        tester.readyJobTrigger().maintain();
        assertEquals("Job is retried", Collections.singletonList(new BuildService.BuildJob(app.deploymentJobs().projectId().getAsLong(), productionUsEast3.jobName())), tester.buildService().jobs());

        // Production job finally succeeds
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.productionUsEast3);
        assertTrue("All jobs consumed", tester.buildService().jobs().isEmpty());
        assertFalse("No failures", tester.application(app.id()).deploymentJobs().hasFailures());
    }
    @Test
    public void testRetriesJobsFailingForCurrentChange() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                .environment(Environment.prod)
                .region("us-east-3")
                .build();
        Version version = Version.fromString("5.0");
        tester.updateVersionStatus(version);

        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.productionUsEast3);

        // New version is released
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.readyJobTrigger().maintain();
        assertEquals("Application has pending upgrade to " + version, version, tester.application(app.id()).change().platform().get());

        // system-test fails and is left with a retry
        tester.deployAndNotify(app, applicationPackage, false, DeploymentJobs.JobType.systemTest);

        // Another version is released
        version = Version.fromString("5.2");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        tester.buildService().removeJob((long) 1, systemTest.jobName());
        tester.upgrader().maintain();
        tester.readyJobTrigger().maintain();
        assertEquals("Application has pending upgrade to " + version, version, tester.application(app.id()).change().platform().get());

        // Cancellation of outdated version and triggering on a new version is done by the upgrader.
        assertEquals(version, tester.application(app.id()).deploymentJobs().jobStatus().get(systemTest).lastTriggered().get().version());
    }

    @Test
    public void ignoresPullRequestInstances() throws Exception {
        DeploymentTester tester = new DeploymentTester();
        tester.controllerTester().zoneRegistry().setSystemName(SystemName.cd);

        // Current system version, matches version in test data
        Version version = Version.fromString("6.42.1");
        tester.configServer().setDefaultVersion(version);
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        // Load test data data
        byte[] json = Files.readAllBytes(Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/maintenance/testdata/pr-instance-with-dead-locked-job.json"));
        Slime slime = SlimeUtils.jsonToSlime(json);
        Application application = tester.controllerTester().createApplication(slime);

        // Failure redeployer does not restart deployment
        tester.readyJobTrigger().maintain();
        assertTrue("No jobs scheduled", tester.buildService().jobs().isEmpty());
    }

    @Test
    public void applicationWithoutProjectIdIsNotTriggered() throws Exception {
        DeploymentTester tester = new DeploymentTester();

        // Current system version, matches version in test data
        Version version = Version.fromString("6.42.1");
        tester.configServer().setDefaultVersion(version);
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        // Load test data data
        byte[] json = Files.readAllBytes(Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/maintenance/testdata/application-without-project-id.json"));
        Slime slime = SlimeUtils.jsonToSlime(json);
        tester.controllerTester().createApplication(slime);

        // Failure redeployer does not restart deployment
        tester.readyJobTrigger().maintain();
        assertTrue("No jobs scheduled", tester.buildService().jobs().isEmpty());
    }

}
