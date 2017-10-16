// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.SystemName;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.ApplicationSerializer;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

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
        tester.notifyJobCompletion(DeploymentJobs.JobType.component, app, true);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.productionUsEast3);

        // New version is released
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        // Test environments pass
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.stagingTest);

        // Production job fails and is retried
        tester.clock().advance(Duration.ofSeconds(1)); // Advance time so that we can detect jobs in progress
        tester.deployAndNotify(app, applicationPackage, false, DeploymentJobs.JobType.productionUsEast3);
        assertEquals("Production job is retried", 1, tester.buildSystem().jobs().size());
        assertEquals("Application has pending upgrade to " + version, version, tester.versionChange(app.id()).get().version());

        // Another version is released, which cancels any pending upgrades to lower versions
        version = Version.fromString("5.2");
        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        assertEquals("Application starts upgrading to new version", 1, tester.buildSystem().jobs().size());
        assertEquals("Application has pending upgrade to " + version, version, tester.versionChange(app.id()).get().version());

        // Failure redeployer does not retry failing job for prod.us-east-3 as there's an ongoing deployment
        tester.clock().advance(Duration.ofMinutes(1));
        tester.failureRedeployer().maintain();
        assertFalse("Job is not retried", tester.buildSystem().jobs().stream()
                .anyMatch(j -> j.jobName().equals(DeploymentJobs.JobType.productionUsEast3.id())));

        // Test environments pass
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.stagingTest);

        // Production job fails again and exhausts all immediate retries
        tester.deployAndNotify(app, applicationPackage, false, DeploymentJobs.JobType.productionUsEast3);
        tester.buildSystem().takeJobsToRun();
        tester.clock().advance(Duration.ofMinutes(10));
        tester.notifyJobCompletion(DeploymentJobs.JobType.productionUsEast3, app, false);
        assertTrue("Retries exhausted", tester.buildSystem().jobs().isEmpty());
        assertTrue("Failure is recorded", tester.application(app.id()).deploymentJobs().hasFailures());

        // Failure redeployer retries job
        tester.clock().advance(Duration.ofMinutes(5));
        tester.failureRedeployer().maintain();
        assertEquals("Job is retried", 1, tester.buildSystem().jobs().size());

        // Production job finally succeeds
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.productionUsEast3);
        assertTrue("All jobs consumed", tester.buildSystem().jobs().isEmpty());
        assertFalse("No failures", tester.application(app.id()).deploymentJobs().hasFailures());
    }

    @Test
    public void testRetriesDeploymentWithStuckJobs() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                .environment(Environment.prod)
                .region("us-east-3")
                .build();

        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.notifyJobCompletion(DeploymentJobs.JobType.component, app, true);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);

        // staging-test starts, but does not complete
        assertEquals(DeploymentJobs.JobType.stagingTest.id(), tester.buildSystem().takeJobsToRun().get(0).jobName());
        tester.failureRedeployer().maintain();
        assertTrue("No jobs retried", tester.buildSystem().jobs().isEmpty());

        // Just over 12 hours pass, job is retried
        tester.clock().advance(Duration.ofHours(12).plus(Duration.ofSeconds(1)));
        tester.failureRedeployer().maintain();
        assertEquals(DeploymentJobs.JobType.stagingTest.id(), tester.buildSystem().takeJobsToRun().get(0).jobName());

        // Deployment completes
        tester.deploy(DeploymentJobs.JobType.stagingTest, app, applicationPackage, true);
        tester.notifyJobCompletion(DeploymentJobs.JobType.stagingTest, app, true);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.productionUsEast3);
        assertTrue("All jobs consumed", tester.buildSystem().jobs().isEmpty());
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
        tester.notifyJobCompletion(DeploymentJobs.JobType.component, app, true);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.productionUsEast3);

        // New version is released
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        assertEquals("Application has pending upgrade to " + version, version, tester.versionChange(app.id()).get().version());

        // system-test fails and exhausts all immediate retries
        tester.deployAndNotify(app, applicationPackage, false, DeploymentJobs.JobType.systemTest);
        tester.buildSystem().takeJobsToRun();
        tester.clock().advance(Duration.ofMinutes(10));
        tester.notifyJobCompletion(DeploymentJobs.JobType.systemTest, app, false);
        assertTrue("Retries exhausted", tester.buildSystem().jobs().isEmpty());

        // Another version is released
        version = Version.fromString("5.2");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        assertEquals("Application has pending upgrade to " + version, version, tester.versionChange(app.id()).get().version());

        // Consume system-test job for 5.2
        tester.buildSystem().takeJobsToRun();

        // Failure re-deployer does not retry failing system-test job as it failed for an older change
        tester.clock().advance(Duration.ofMinutes(5));
        tester.failureRedeployer().maintain();
        assertTrue("No jobs retried", tester.buildSystem().jobs().isEmpty());
    }

    @Test
    public void retryIgnoresStaleJobData() throws Exception {
        DeploymentTester tester = new DeploymentTester();
        tester.controllerTester().zoneRegistry().setSystem(SystemName.cd);

        // Current system version, matches version in test data
        Version version = Version.fromString("6.141.117");
        tester.configServer().setDefaultVersion(version);
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        // Load test data data
        ApplicationSerializer serializer = new ApplicationSerializer();
        byte[] json = Files.readAllBytes(Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/maintenance/testdata/canary-with-stale-data.json"));
        Slime slime = SlimeUtils.jsonToSlime(json);
        Application application = serializer.fromSlime(slime);
        try (Lock lock = tester.controller().applications().lock(application.id())) {
            tester.controller().applications().store(application, lock);
        }
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                .region("cd-us-central-1")
                .build();

        // New version is released
        version = Version.fromString("6.142.1");
        tester.configServer().setDefaultVersion(version);
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        // Test environments pass
        tester.deploy(DeploymentJobs.JobType.systemTest, application, applicationPackage);
        tester.buildSystem().takeJobsToRun();
        tester.clock().advance(Duration.ofMinutes(10));
        tester.notifyJobCompletion(DeploymentJobs.JobType.systemTest, application, true);

        tester.deploy(DeploymentJobs.JobType.stagingTest, application, applicationPackage);
        tester.buildSystem().takeJobsToRun();
        tester.clock().advance(Duration.ofMinutes(10));
        tester.notifyJobCompletion(DeploymentJobs.JobType.stagingTest, application, true);

        // Production job starts, but does not complete
        assertEquals(1, tester.buildSystem().jobs().size());
        assertEquals("Production job triggered", DeploymentJobs.JobType.productionCdUsCentral1.id(), tester.buildSystem().jobs().get(0).jobName());
        tester.buildSystem().takeJobsToRun();

        // Failure re-deployer runs
        tester.failureRedeployer().maintain();
        assertTrue("No jobs retried", tester.buildSystem().jobs().isEmpty());

        // Deployment completes
        tester.notifyJobCompletion(DeploymentJobs.JobType.productionCdUsCentral1, application, true);
        assertFalse("Change deployed", tester.application(application.id()).deploying().isPresent());
    }

    @Test
    public void ignoresPullRequestInstances() throws Exception {
        DeploymentTester tester = new DeploymentTester();
        tester.controllerTester().zoneRegistry().setSystem(SystemName.cd);

        // Current system version, matches version in test data
        Version version = Version.fromString("6.42.1");
        tester.configServer().setDefaultVersion(version);
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        // Load test data data
        ApplicationSerializer serializer = new ApplicationSerializer();
        byte[] json = Files.readAllBytes(Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/maintenance/testdata/pr-instance-with-dead-locked-job.json"));
        Slime slime = SlimeUtils.jsonToSlime(json);
        Application application = serializer.fromSlime(slime);

        try (Lock lock = tester.controller().applications().lock(application.id())) {
            tester.controller().applications().store(application, lock);
        }

        // Failure redeployer does not restart deployment
        tester.failureRedeployer().maintain();
        assertTrue("No jobs scheduled", tester.buildSystem().jobs().isEmpty());
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
        ApplicationSerializer serializer = new ApplicationSerializer();
        byte[] json = Files.readAllBytes(Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/maintenance/testdata/application-without-project-id.json"));
        Slime slime = SlimeUtils.jsonToSlime(json);
        Application application = serializer.fromSlime(slime);

        try (Lock lock = tester.controller().applications().lock(application.id())) {
            tester.controller().applications().store(application, lock);
        }

        // Failure redeployer does not restart deployment
        tester.failureRedeployer().maintain();
        assertTrue("No jobs scheduled", tester.buildSystem().jobs().isEmpty());
    }

}
