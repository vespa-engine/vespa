// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class UpgraderTest {

    @Test
    public void testUpgrading() {
        // --- Setup
        DeploymentTester tester = new DeploymentTester();
        tester.upgrader().maintain();
        assertEquals("No system version: Nothing to do", 0, tester.buildSystem().jobs().size());

        Version version = Version.fromString("5.0"); // (lower than the hardcoded version in the config server client)
        tester.updateVersionStatus(version);

        tester.upgrader().maintain();
        assertEquals("No applications: Nothing to do", 0, tester.buildSystem().jobs().size());

        // Setup applications
        Application canary0 = tester.createAndDeploy("canary0", 0, "canary");
        Application canary1 = tester.createAndDeploy("canary1", 1, "canary");
        Application default0 = tester.createAndDeploy("default0", 2, "default");
        Application default1 = tester.createAndDeploy("default1", 3, "default");
        Application default2 = tester.createAndDeploy("default2", 4, "default");
        Application conservative0 = tester.createAndDeploy("conservative0", 5, "conservative");

        tester.upgrader().maintain();
        assertEquals("All already on the right version: Nothing to do", 0, tester.buildSystem().jobs().size());

        // --- A new version is released - everything goes smoothly
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        assertEquals("New system version: Should upgrade Canaries", 2, tester.buildSystem().jobs().size());
        tester.completeUpgrade(canary0, version, "canary");
        assertEquals(version, tester.configServer().lastPrepareVersion.get());

        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        assertEquals("One canary pending; nothing else", 1, tester.buildSystem().jobs().size());

        tester.completeUpgrade(canary1, version, "canary");

        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        assertEquals("Canaries done: Should upgrade defaults", 3, tester.buildSystem().jobs().size());

        tester.completeUpgrade(default0, version, "default");
        tester.completeUpgrade(default1, version, "default");
        tester.completeUpgrade(default2, version, "default");

        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.high, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        assertEquals("Normals done: Should upgrade conservatives", 1, tester.buildSystem().jobs().size());
        tester.completeUpgrade(conservative0, version, "conservative");

        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        assertEquals("Nothing to do", 0, tester.buildSystem().jobs().size());

        // --- A new version is released - which fails a Canary
        version = Version.fromString("5.2");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        assertEquals("New system version: Should upgrade Canaries", 2, tester.buildSystem().jobs().size());
        tester.completeUpgradeWithError(canary0, version, "canary", DeploymentJobs.JobType.stagingTest);
        assertEquals("Other Canary was cancelled", 2, tester.buildSystem().jobs().size());

        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.broken, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        assertEquals("Version broken, but Canaries should keep trying", 2, tester.buildSystem().jobs().size());

        // --- A new version is released - which repairs the Canary app and fails a default
        version = Version.fromString("5.3");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        assertEquals("New system version: Should upgrade Canaries", 2, tester.buildSystem().jobs().size());
        tester.completeUpgrade(canary0, version, "canary");
        assertEquals(version, tester.configServer().lastPrepareVersion.get());

        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        assertEquals("One canary pending; nothing else", 1, tester.buildSystem().jobs().size());

        tester.completeUpgrade(canary1, version, "canary");

        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();

        assertEquals("Canaries done: Should upgrade defaults", 3, tester.buildSystem().jobs().size());

        tester.completeUpgradeWithError(default0, version, "default", DeploymentJobs.JobType.stagingTest);
        tester.completeUpgrade(default1, version, "default");
        tester.completeUpgrade(default2, version, "default");

        tester.updateVersionStatus(version);
        assertEquals("Not enough evidence to mark this neither broken nor high",
                     VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        assertEquals("Upgrade with error should retry", 1, tester.buildSystem().jobs().size());

        // --- Failing application is repaired by changing the application, causing confidence to move above 'high' threshold
        // Deploy application change
        tester.deployCompletely("default0");
        // Complete upgrade
        tester.upgrader().maintain();
        tester.completeUpgrade(default0, version, "default");

        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.high, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        assertEquals("Normals done: Should upgrade conservatives", 1, tester.buildSystem().jobs().size());
        tester.completeUpgrade(conservative0, version, "conservative");

        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        assertEquals("Nothing to do", 0, tester.buildSystem().jobs().size());
    }

    @Test
    public void testUpgradingToVersionWhichBreaksSomeNonCanaries() {
        // --- Setup
        DeploymentTester tester = new DeploymentTester();
        tester.upgrader().maintain();
        assertEquals("No system version: Nothing to do", 0, tester.buildSystem().jobs().size());

        Version version = Version.fromString("5.0"); // (lower than the hardcoded version in the config server client)
        tester.updateVersionStatus(version);

        tester.upgrader().maintain();
        assertEquals("No applications: Nothing to do", 0, tester.buildSystem().jobs().size());

        // Setup applications
        Application canary0 = tester.createAndDeploy("canary0", 0, "canary");
        Application canary1 = tester.createAndDeploy("canary1", 1, "canary");
        Application default0 = tester.createAndDeploy("default0",  2, "default");
        Application default1 = tester.createAndDeploy("default1",  3, "default");
        Application default2 = tester.createAndDeploy("default2",  4, "default");
        Application default3 = tester.createAndDeploy("default3",  5, "default");
        Application default4 = tester.createAndDeploy("default4",  6, "default");
        Application default5 = tester.createAndDeploy("default5",  7, "default");
        Application default6 = tester.createAndDeploy("default6",  8, "default");
        Application default7 = tester.createAndDeploy("default7",  9, "default");
        Application default8 = tester.createAndDeploy("default8", 10, "default");
        Application default9 = tester.createAndDeploy("default9", 11, "default");

        tester.upgrader().maintain();
        assertEquals("All already on the right version: Nothing to do", 0, tester.buildSystem().jobs().size());

        // --- A new version is released
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        assertEquals("New system version: Should upgrade Canaries", 2, tester.buildSystem().jobs().size());
        tester.completeUpgrade(canary0, version, "canary");
        assertEquals(version, tester.configServer().lastPrepareVersion.get());

        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        assertEquals("One canary pending; nothing else", 1, tester.buildSystem().jobs().size());

        tester.completeUpgrade(canary1, version, "canary");

        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        assertEquals("Canaries done: Should upgrade defaults", 10, tester.buildSystem().jobs().size());

        tester.completeUpgrade(default0, version, "default");
        tester.completeUpgradeWithError(default1, version, "default", DeploymentJobs.JobType.systemTest);
        tester.completeUpgradeWithError(default2, version, "default", DeploymentJobs.JobType.systemTest);
        tester.completeUpgradeWithError(default3, version, "default", DeploymentJobs.JobType.systemTest);
        tester.completeUpgradeWithError(default4, version, "default", DeploymentJobs.JobType.systemTest);

        // > 40% and at least 4 failed - version is broken
        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        assertEquals(VespaVersion.Confidence.broken, tester.controller().versionStatus().systemVersion().get().confidence());
        assertEquals("Upgrades are cancelled", 0, tester.buildSystem().jobs().size());
    }

    @Test
    public void testDeploymentAlreadyInProgressForUpgrade() {
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

        tester.upgrader().maintain();
        assertEquals("Application is on expected version: Nothing to do", 0,
                     tester.buildSystem().jobs().size());

        // New version is released
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        // system-test completes successfully
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);

        // staging-test fails multiple times, exhausts retries and failure is recorded
        tester.deployAndNotify(app, applicationPackage, false, DeploymentJobs.JobType.stagingTest);
        tester.buildSystem().takeJobsToRun();
        tester.clock().advance(Duration.ofMinutes(10));
        tester.notifyJobCompletion(DeploymentJobs.JobType.stagingTest, app, false);
        assertTrue("Retries exhausted", tester.buildSystem().jobs().isEmpty());
        assertTrue("Failure is recorded", tester.application(app.id()).deploymentJobs().hasFailures());
        assertTrue("Application has pending change", tester.application(app.id()).deploying().isPresent());

        // New version is released
        version = Version.fromString("5.2");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        // Upgrade is scheduled. system-tests starts, but does not complete
        tester.upgrader().maintain();
        assertTrue("Application still has failures", tester.application(app.id()).deploymentJobs().hasFailures());
        assertEquals(1, tester.buildSystem().jobs().size());
        tester.buildSystem().takeJobsToRun();

        // Upgrader runs again, nothing happens as there's already a job in progress for this change
        tester.upgrader().maintain();
        assertTrue("No more jobs triggered at this time", tester.buildSystem().jobs().isEmpty());
    }

    @Test
    public void testUpgradeCancelledWithDeploymentInProgress() {
        DeploymentTester tester = new DeploymentTester();
        Version version = Version.fromString("5.0");
        tester.updateVersionStatus(version);

        // Setup applications
        Application canary0 = tester.createAndDeploy("canary0", 0, "canary");
        Application canary1 = tester.createAndDeploy("canary1", 1, "canary");
        Application default0 = tester.createAndDeploy("default0", 2, "default");
        Application default1 = tester.createAndDeploy("default1", 3, "default");
        Application default2 = tester.createAndDeploy("default2", 4, "default");
        Application default3 = tester.createAndDeploy("default3", 5, "default");
        Application default4 = tester.createAndDeploy("default4", 6, "default");

        // New version is released
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        // Canaries upgrade and raise confidence
        tester.completeUpgrade(canary0, version, "canary");
        tester.completeUpgrade(canary1, version, "canary");
        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());

        // Applications with default policy start upgrading
        tester.upgrader().maintain();
        assertEquals("Upgrade scheduled for remaining apps", 5, tester.buildSystem().jobs().size());

        // 4/5 applications fail and lowers confidence
        tester.completeUpgradeWithError(default0, version, "default", DeploymentJobs.JobType.systemTest);
        tester.completeUpgradeWithError(default1, version, "default", DeploymentJobs.JobType.systemTest);
        tester.completeUpgradeWithError(default2, version, "default", DeploymentJobs.JobType.systemTest);
        tester.completeUpgradeWithError(default3, version, "default", DeploymentJobs.JobType.systemTest);
        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.broken, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();

        // 5th app passes system-test, but does not trigger next job as upgrade is cancelled
        assertFalse("No change present", tester.applications().require(default4.id()).deploying().isPresent());
        tester.notifyJobCompletion(DeploymentJobs.JobType.systemTest, default4, true);
        assertTrue("All jobs consumed", tester.buildSystem().jobs().isEmpty());
    }

    @Test
    public void testConfidenceIgnoresFailingApplicationChanges() {
        DeploymentTester tester = new DeploymentTester();
        Version version = Version.fromString("5.0");
        tester.updateVersionStatus(version);

        // Setup applications
        Application canary0 = tester.createAndDeploy("canary0", 0, "canary");
        Application canary1 = tester.createAndDeploy("canary1", 1, "canary");
        Application default0 = tester.createAndDeploy("default0", 2, "default");
        Application default1 = tester.createAndDeploy("default1", 3, "default");
        Application default2 = tester.createAndDeploy("default2", 4, "default");
        Application default3 = tester.createAndDeploy("default3", 5, "default");
        Application default4 = tester.createAndDeploy("default4", 5, "default");

        // New version is released
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        // Canaries upgrade and raise confidence
        tester.completeUpgrade(canary0, version, "canary");
        tester.completeUpgrade(canary1, version, "canary");
        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());

        // All applications upgrade successfully
        tester.upgrader().maintain();
        tester.completeUpgrade(default0, version, "default");
        tester.completeUpgrade(default1, version, "default");
        tester.completeUpgrade(default2, version, "default");
        tester.completeUpgrade(default3, version, "default");
        tester.completeUpgrade(default4, version, "default");
        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.high, tester.controller().versionStatus().systemVersion().get().confidence());

        // Multiple application changes are triggered and fail, but does not affect version confidence as upgrade has
        // completed successfully
        tester.notifyJobCompletion(DeploymentJobs.JobType.component, default0, false);
        tester.notifyJobCompletion(DeploymentJobs.JobType.component, default1, false);
        tester.notifyJobCompletion(DeploymentJobs.JobType.component, default2, true);
        tester.notifyJobCompletion(DeploymentJobs.JobType.component, default3, true);
        tester.notifyJobCompletion(DeploymentJobs.JobType.systemTest, default2, false);
        tester.notifyJobCompletion(DeploymentJobs.JobType.systemTest, default3, false);
        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());
    }

    @Test
    public void testConsidersBlockUpgradeWindow() {
        ManualClock clock = new ManualClock(Instant.parse("2017-09-26T18:00:00.00Z")); // A tuesday
        DeploymentTester tester = new DeploymentTester(new ControllerTester(clock));
        Version version = Version.fromString("5.0");
        tester.updateVersionStatus(version);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                // Block upgrades on tuesday in hours 18 and 19
                .blockUpgrade("tue", "18-19", "UTC")
                .region("us-west-1")
                .build();

        Application app = tester.createAndDeploy("app1", 1, applicationPackage);

        // New version is released
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);

        // Application is not upgraded at this time
        tester.upgrader().maintain();
        assertTrue("No jobs scheduled", tester.buildSystem().jobs().isEmpty());

        // One hour passes, time is 19:00, still no upgrade
        tester.clock().advance(Duration.ofHours(1));
        tester.upgrader().maintain();
        assertTrue("No jobs scheduled", tester.buildSystem().jobs().isEmpty());

        // Two hours pass in total, time is 20:00 and application upgrades
        tester.clock().advance(Duration.ofHours(1));
        tester.upgrader().maintain();
        assertFalse("Job is scheduled", tester.buildSystem().jobs().isEmpty());
        tester.completeUpgrade(app, version, "canary");
        assertTrue("All jobs consumed", tester.buildSystem().jobs().isEmpty());
    }

}
