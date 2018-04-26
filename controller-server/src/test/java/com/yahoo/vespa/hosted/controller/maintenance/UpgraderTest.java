// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.component;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionUsCentral1;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;
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

        Version version = Version.fromString("5.0");
        tester.upgradeSystem(version);

        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("No applications: Nothing to do", 0, tester.buildService().jobs().size());

        // Setup applications
        Application canary0 = tester.createAndDeploy("canary0", 1, "canary");
        Application canary1 = tester.createAndDeploy("canary1", 2, "canary");
        Application default0 = tester.createAndDeploy("default0", 3, "default");
        Application default1 = tester.createAndDeploy("default1", 4, "default");
        Application default2 = tester.createAndDeploy("default2", 5, "default");
        Application conservative0 = tester.createAndDeploy("conservative0", 6, "conservative");

        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("All already on the right version: Nothing to do", 0, tester.buildService().jobs().size());

        // --- 5.1 is released - everything goes smoothly
        version = Version.fromString("5.1");
        tester.upgradeSystem(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        assertEquals("New system version: Should upgrade Canaries", 4, tester.buildService().jobs().size());
        tester.completeUpgrade(canary0, version, "canary");
        assertEquals(version, tester.configServer().lastPrepareVersion().get());

        tester.upgradeSystem(version);
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("One canary pending; nothing else", 2, tester.buildService().jobs().size());

        tester.completeUpgrade(canary1, version, "canary");

        tester.upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("Canaries done: Should upgrade defaults", 6, tester.buildService().jobs().size());

        tester.completeUpgrade(default0, version, "default");
        tester.completeUpgrade(default1, version, "default");
        tester.completeUpgrade(default2, version, "default");

        tester.upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.high, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("Normals done: Should upgrade conservatives", 2, tester.buildService().jobs().size());
        tester.completeUpgrade(conservative0, version, "conservative");

        tester.upgradeSystem(version);
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("Nothing to do", 0, tester.buildService().jobs().size());

        // --- 5.2 is released - which fails a Canary
        version = Version.fromString("5.2");
        tester.upgradeSystem(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        assertEquals("New system version: Should upgrade Canaries", 4, tester.buildService().jobs().size());
        tester.completeUpgradeWithError(canary0, version, "canary", stagingTest);

        tester.upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.broken, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.jobCompletion(stagingTest).application(canary0).unsuccessful().submit();
        assertEquals("Version broken, but Canaries should keep trying", 3, tester.buildService().jobs().size());

        // --- A new version is released - which repairs the Canary app and fails a default
        version = Version.fromString("5.3");
        tester.upgradeSystem(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.buildService().remove(ControllerTester.buildJob(canary0, stagingTest));
        tester.buildService().remove(ControllerTester.buildJob(canary1, systemTest));
        tester.buildService().remove(ControllerTester.buildJob(canary1, stagingTest));
        tester.triggerUntilQuiescence();

        assertEquals("New system version: Should upgrade Canaries", 4, tester.buildService().jobs().size());
        tester.completeUpgrade(canary0, version, "canary");
        assertEquals(version, tester.configServer().lastPrepareVersion().get());

        tester.upgradeSystem(version);
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("One canary pending; nothing else", 2, tester.buildService().jobs().size());

        tester.completeUpgrade(canary1, version, "canary");

        tester.upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        assertEquals("Canaries done: Should upgrade defaults", 6, tester.buildService().jobs().size());

        tester.completeUpgradeWithError(default0, version, "default", stagingTest);
        tester.completeUpgrade(default1, version, "default");
        tester.completeUpgrade(default2, version, "default");

        tester.upgradeSystem(version);
        assertEquals("Not enough evidence to mark this as neither broken nor high",
                     VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());

        assertEquals("Upgrade with error should retry", 1, tester.buildService().jobs().size());


        // --- Failing application is repaired by changing the application, causing confidence to move above 'high' threshold
        // Deploy application change
        tester.jobCompletion(component).application(default0).nextBuildNumber().uploadArtifact(DeploymentTester.applicationPackage("default")).submit();
        tester.jobCompletion(stagingTest).application(default0).unsuccessful().submit();
        tester.deployAndNotify(default0, "default", true, systemTest);
        tester.deployAndNotify(default0, "default", true, stagingTest);
        tester.deployAndNotify(default0, "default", true, productionUsWest1);
        tester.deployAndNotify(default0, "default", true, productionUsEast3);

        tester.upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.high, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("Normals done: Should upgrade conservatives", 2, tester.buildService().jobs().size());
        tester.completeUpgrade(conservative0, version, "conservative");

        tester.upgradeSystem(version);
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("Applications are on 5.3 - nothing to do", 0, tester.buildService().jobs().size());

        // --- Starting upgrading to a new version which breaks, causing upgrades to commence on the previous version
        Version version54 = Version.fromString("5.4");
        Application default3 = tester.createAndDeploy("default3", 7, "default"); // need 4 to break a version
        Application default4 = tester.createAndDeploy("default4", 8, "default");
        tester.upgradeSystem(version54);
        tester.upgrader().maintain(); // cause canary upgrades to 5.4
        tester.triggerUntilQuiescence();
        tester.completeUpgrade(canary0, version54, "canary");
        tester.completeUpgrade(canary1, version54, "canary");
        tester.upgradeSystem(version54);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        assertEquals("Upgrade of defaults are scheduled", 10, tester.buildService().jobs().size());
        assertEquals(version54, tester.application(default0.id()).change().platform().get());
        assertEquals(version54, tester.application(default1.id()).change().platform().get());
        assertEquals(version54, tester.application(default2.id()).change().platform().get());
        assertEquals(version54, tester.application(default3.id()).change().platform().get());
        assertEquals(version54, tester.application(default4.id()).change().platform().get());

        tester.completeUpgrade(default0, version54, "default");
        // State: Default applications started upgrading to 5.4 (and one completed)
        Version version55 = Version.fromString("5.5");
        tester.upgradeSystem(version55);
        tester.upgrader().maintain(); // cause canary upgrades to 5.5
        tester.triggerUntilQuiescence();
        tester.completeUpgrade(canary0, version55, "canary");
        tester.completeUpgrade(canary1, version55, "canary");
        tester.upgradeSystem(version55);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        assertEquals("Upgrade of defaults are scheduled", 10, tester.buildService().jobs().size());
        assertEquals(version55, tester.application(default0.id()).change().platform().get());
        assertEquals(version54, tester.application(default1.id()).change().platform().get());
        assertEquals(version54, tester.application(default2.id()).change().platform().get());
        assertEquals(version54, tester.application(default3.id()).change().platform().get());
        assertEquals(version54, tester.application(default4.id()).change().platform().get());

        tester.completeUpgrade(default1, version54, "default");
        tester.completeUpgrade(default2, version54, "default");

        tester.completeUpgradeWithError(default3, version54, "default", stagingTest);

        tester.completeUpgradeWithError(default4, version54, "default", DeploymentJobs.JobType.productionUsWest1);
        // State: Default applications started upgrading to 5.5
        tester.clock().advance(Duration.ofHours(1));
        tester.upgrader().maintain();
        tester.jobCompletion(stagingTest).application(default3).unsuccessful().submit();
        tester.jobCompletion(productionUsWest1).application(default4).unsuccessful().submit();
        tester.completeUpgradeWithError(default0, version55, "default", stagingTest);
        tester.completeUpgradeWithError(default1, version55, "default", stagingTest);
        tester.completeUpgradeWithError(default2, version55, "default", stagingTest);
        tester.completeUpgradeWithError(default3, version55, "default", DeploymentJobs.JobType.productionUsWest1);
        tester.upgradeSystem(version55);
        assertEquals(VespaVersion.Confidence.broken, tester.controller().versionStatus().systemVersion().get().confidence());

        // Finish running job, without retry.
        tester.clock().advance(Duration.ofHours(1));
        tester.jobCompletion(DeploymentJobs.JobType.productionUsWest1).application(default3).unsuccessful().submit();

        tester.upgrader().maintain();
        tester.buildService().clear();
        tester.triggerUntilQuiescence();
        assertEquals("Upgrade of defaults are scheduled on 5.4 instead, since 5.5 broken: " +
                     "This is default3 since it failed upgrade on both 5.4 and 5.5",
                     2, tester.buildService().jobs().size());
        assertEquals("5.4", tester.application(default3.id()).change().platform().get().toString());
    }

    @Test
    public void testUpgradingToVersionWhichBreaksSomeNonCanaries() {
        // --- Setup
        DeploymentTester tester = new DeploymentTester();
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("No system version: Nothing to do", 0, tester.buildService().jobs().size());

        Version version = Version.fromString("5.0"); // (lower than the hardcoded version in the config server client)
        tester.upgradeSystem(version);

        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("No applications: Nothing to do", 0, tester.buildService().jobs().size());

        // Setup applications
        Application canary0  = tester.createAndDeploy("canary0",  1, "canary");
        Application canary1  = tester.createAndDeploy("canary1",  2, "canary");
        Application default0 = tester.createAndDeploy("default0", 3, "default");
        Application default1 = tester.createAndDeploy("default1", 4, "default");
        Application default2 = tester.createAndDeploy("default2", 5, "default");
        Application default3 = tester.createAndDeploy("default3", 6, "default");
        Application default4 = tester.createAndDeploy("default4", 7, "default");
        Application default5 = tester.createAndDeploy("default5", 8, "default");
        Application default6 = tester.createAndDeploy("default6", 9, "default");
        Application default7 = tester.createAndDeploy("default7", 10, "default");
        Application default8 = tester.createAndDeploy("default8", 11, "default");
        Application default9 = tester.createAndDeploy("default9", 12, "default");

        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("All already on the right version: Nothing to do", 0, tester.buildService().jobs().size());

        // --- A new version is released
        version = Version.fromString("5.1");
        tester.upgradeSystem(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        assertEquals("New system version: Should upgrade Canaries", 4, tester.buildService().jobs().size());
        tester.completeUpgrade(canary0, version, "canary");
        assertEquals(version, tester.configServer().lastPrepareVersion().get());

        tester.upgradeSystem(version);
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("One canary pending; nothing else", 2, tester.buildService().jobs().size());

        tester.completeUpgrade(canary1, version, "canary");

        tester.upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("Canaries done: Should upgrade defaults", 20, tester.buildService().jobs().size());

        tester.completeUpgrade(default0, version, "default");
        tester.completeUpgradeWithError(default1, version, "default", systemTest);
        tester.completeUpgradeWithError(default2, version, "default", systemTest);
        tester.completeUpgradeWithError(default3, version, "default", systemTest);
        tester.completeUpgradeWithError(default4, version, "default", systemTest);

        // > 40% and at least 4 failed - version is broken
        tester.upgradeSystem(version);
        tester.upgrader().maintain();
        tester.buildService().clear();
        tester.triggerUntilQuiescence();
        assertEquals(VespaVersion.Confidence.broken, tester.controller().versionStatus().systemVersion().get().confidence());
        assertEquals("Upgrades are cancelled", 0, tester.buildService().jobs().size());
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
        tester.upgradeSystem(version);

        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, applicationPackage, true, systemTest);
        tester.deployAndNotify(app, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, productionUsEast3);

        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("Application is on expected version: Nothing to do", 0,
                     tester.buildService().jobs().size());

        // New version is released
        version = Version.fromString("5.1");
        tester.upgradeSystem(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        // system-test completes successfully
        tester.deployAndNotify(app, applicationPackage, true, systemTest);

        // staging-test fails and failure is recorded
        tester.deployAndNotify(app, applicationPackage, false, stagingTest);
        assertTrue("Failure is recorded", tester.application(app.id()).deploymentJobs().hasFailures());
        assertTrue("Application has pending change", tester.application(app.id()).change().isPresent());

        // New version is released
        version = Version.fromString("5.2");
        tester.upgradeSystem(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        // Upgrade is scheduled. system-tests starts, but does not complete
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        tester.jobCompletion(stagingTest).application(app).unsuccessful().submit();
        assertTrue("Application still has failures", tester.application(app.id()).deploymentJobs().hasFailures());
        assertEquals(2, tester.buildService().jobs().size());

        // Upgrader runs again, nothing happens as test jobs are already running.
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals(2, tester.buildService().jobs().size());
    }

    @Test
    public void testUpgradeCancelledWithDeploymentInProgress() {
        DeploymentTester tester = new DeploymentTester();
        Version version = Version.fromString("5.0");
        tester.upgradeSystem(version);

        // Setup applications
        Application canary0 = tester.createAndDeploy("canary0", 1, "canary");
        Application canary1 = tester.createAndDeploy("canary1", 2, "canary");
        Application default0 = tester.createAndDeploy("default0", 3, "default");
        Application default1 = tester.createAndDeploy("default1", 4, "default");
        Application default2 = tester.createAndDeploy("default2", 5, "default");
        Application default3 = tester.createAndDeploy("default3", 6, "default");
        Application default4 = tester.createAndDeploy("default4", 7, "default");

        // New version is released
        version = Version.fromString("5.1");
        tester.upgradeSystem(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        // Canaries upgrade and raise confidence
        tester.completeUpgrade(canary0, version, "canary");
        tester.completeUpgrade(canary1, version, "canary");
        tester.upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());

        // Applications with default policy start upgrading
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("Upgrade scheduled for remaining apps", 10, tester.buildService().jobs().size());

        // 4/5 applications fail and lowers confidence
        tester.completeUpgradeWithError(default0, version, "default", systemTest);
        tester.completeUpgradeWithError(default1, version, "default", systemTest);
        tester.completeUpgradeWithError(default2, version, "default", systemTest);
        tester.completeUpgradeWithError(default3, version, "default", systemTest);
        tester.upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.broken, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        // apps pass system-test, but do not trigger next jobs as upgrade is cancelled
        assertFalse("No change present", tester.applications().require(default4.id()).change().isPresent());
        tester.jobCompletion(systemTest).application(default0).submit();
        tester.jobCompletion(systemTest).application(default1).submit();
        tester.jobCompletion(systemTest).application(default2).submit();
        tester.jobCompletion(systemTest).application(default3).submit();
        tester.jobCompletion(systemTest).application(default4).submit();
        tester.jobCompletion(stagingTest).application(default0).submit();
        tester.jobCompletion(stagingTest).application(default1).submit();
        tester.jobCompletion(stagingTest).application(default2).submit();
        tester.jobCompletion(stagingTest).application(default3).submit();
        tester.jobCompletion(stagingTest).application(default4).submit();
        assertTrue("All jobs consumed", tester.buildService().jobs().isEmpty());
    }

    /**
     * Scenario:
     *   An application A is on version V0
     *   Version V2 is released.
     *   A upgrades one production zone to V2.
     *   V2 is marked as broken and upgrade of A to V2 is cancelled.
     *   Upgrade of A to V1 is scheduled: Should skip the zone on V2 but upgrade the next zone to V1
     */
    @Test
    public void testVersionIsBrokenAfterAZoneIsLive() {
        DeploymentTester tester = new DeploymentTester();
        Version v0 = Version.fromString("5.0");
        tester.upgradeSystem(v0);

        // Setup applications on V0
        Application canary0 = tester.createAndDeploy("canary0", 1, "canary");
        Application canary1 = tester.createAndDeploy("canary1", 2, "canary");
        Application default0 = tester.createAndDeploy("default0", 3, "default");
        Application default1 = tester.createAndDeploy("default1", 4, "default");
        Application default2 = tester.createAndDeploy("default2", 5, "default");
        Application default3 = tester.createAndDeploy("default3", 6, "default");
        Application default4 = tester.createAndDeploy("default4", 7, "default");

        // V1 is released
        Version v1 = Version.fromString("5.1");
        tester.upgradeSystem(v1);
        assertEquals(v1, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        // Canaries upgrade and raise confidence of V+1 (other apps are not upgraded)
        tester.completeUpgrade(canary0, v1, "canary");
        tester.completeUpgrade(canary1, v1, "canary");
        tester.upgradeSystem(v1);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());

        // V2 is released
        Version v2 = Version.fromString("5.2");
        tester.upgradeSystem(v2);
        assertEquals(v2, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        // Canaries upgrade and raise confidence of V2
        tester.completeUpgrade(canary0, v2, "canary");
        tester.completeUpgrade(canary1, v2, "canary");
        tester.upgradeSystem(v2);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());

        // We "manually" cancel upgrades to V1 so that we can use the applications to make V2 fail instead
        // But we keep one (default4) to avoid V1 being garbage collected
        tester.deploymentTrigger().cancelChange(default0.id(), false);
        tester.deploymentTrigger().cancelChange(default1.id(), false);
        tester.deploymentTrigger().cancelChange(default2.id(), false);
        tester.deploymentTrigger().cancelChange(default3.id(), false);
        tester.buildService().clear();

        // Applications with default policy start upgrading to V2
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("Upgrade scheduled for remaining apps", 10, tester.buildService().jobs().size());
        assertEquals("default4 is still upgrading to 5.1", v1, tester.application(default4.id()).change().platform().get());

        // 4/5 applications fail (in the last prod zone) and lowers confidence
        tester.completeUpgradeWithError(default0, v2, "default", productionUsEast3);
        tester.completeUpgradeWithError(default1, v2, "default", productionUsEast3);
        tester.completeUpgradeWithError(default2, v2, "default", productionUsEast3);
        tester.completeUpgradeWithError(default3, v2, "default", productionUsEast3);
        tester.upgradeSystem(v2);
        assertEquals(VespaVersion.Confidence.broken, tester.controller().versionStatus().systemVersion().get().confidence());

        assertEquals(v2, tester.application("default0").deployments().get(ZoneId.from("prod.us-west-1")).version());
        assertEquals(v0, tester.application("default0").deployments().get(ZoneId.from("prod.us-east-3")).version());
        tester.upgrader().maintain();
        tester.buildService().clear();
        tester.triggerUntilQuiescence();

        assertEquals("Upgrade to 5.1 scheduled for apps not completely on 5.1 or 5.2", 10, tester.buildService().jobs().size());

        // The tester code for completing upgrades does not handle this scenario, so we trigger each step manually (for one app)
        tester.deployAndNotify(tester.application("default0"), "default", true, systemTest);
        tester.deployAndNotify(tester.application("default0"), "default", true, stagingTest);
        // prod zone on 5.2 (usWest1) is skipped, but we still trigger the next zone from triggerReadyJobs:
        tester.deployAndNotify(tester.application("default0"), "default", true, productionUsEast3);

        // Resulting state:
        assertEquals(v2, tester.application("default0").deployments().get(ZoneId.from("prod.us-west-1")).version());
        assertEquals("Last zone is upgraded to v1",
                     v1, tester.application("default0").deployments().get(ZoneId.from("prod.us-east-3")).version());
        assertFalse(tester.application("default0").change().isPresent());
    }

    @Test
    public void testConfidenceIgnoresFailingApplicationChanges() {
        DeploymentTester tester = new DeploymentTester();
        Version version = Version.fromString("5.0");
        tester.upgradeSystem(version);

        ApplicationPackage canaryPolicy = DeploymentTester.applicationPackage("canary");
        ApplicationPackage defaultPolicy = DeploymentTester.applicationPackage("default");

        // Setup applications
        Application canary0 = tester.createAndDeploy("canary0", 1, canaryPolicy);
        Application canary1 = tester.createAndDeploy("canary1", 2, canaryPolicy);
        Application default0 = tester.createAndDeploy("default0", 3, defaultPolicy);
        Application default1 = tester.createAndDeploy("default1", 4, defaultPolicy);
        Application default2 = tester.createAndDeploy("default2", 5, defaultPolicy);
        Application default3 = tester.createAndDeploy("default3", 6, defaultPolicy);
        Application default4 = tester.createAndDeploy("default4", 7, defaultPolicy);

        // New version is released
        version = Version.fromString("5.1");
        tester.upgradeSystem(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        // Canaries upgrade and raise confidence
        tester.completeUpgrade(canary0, version, "canary");
        tester.completeUpgrade(canary1, version, "canary");
        tester.upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());

        // All applications upgrade successfully
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        tester.completeUpgrade(default0, version, "default");
        tester.completeUpgrade(default1, version, "default");
        tester.completeUpgrade(default2, version, "default");
        tester.completeUpgrade(default3, version, "default");
        tester.completeUpgrade(default4, version, "default");
        tester.upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.high, tester.controller().versionStatus().systemVersion().get().confidence());

        // Multiple application changes are triggered and fail, but does not affect version confidence as upgrade has
        // completed successfully
        tester.jobCompletion(component).application(default0).nextBuildNumber().uploadArtifact(canaryPolicy).unsuccessful().submit();
        tester.jobCompletion(component).application(default1).nextBuildNumber().uploadArtifact(canaryPolicy).unsuccessful().submit();
        tester.jobCompletion(component).application(default2).nextBuildNumber().uploadArtifact(defaultPolicy).submit();
        tester.jobCompletion(component).application(default3).nextBuildNumber().uploadArtifact(defaultPolicy).submit();
        tester.jobCompletion(component).application(default2).nextBuildNumber().uploadArtifact(canaryPolicy).unsuccessful().submit();
        tester.jobCompletion(component).application(default3).nextBuildNumber(2).uploadArtifact(canaryPolicy).unsuccessful().submit();
        tester.upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());
    }

    @Test
    public void testBlockVersionChange() {
        ManualClock clock = new ManualClock(Instant.parse("2017-09-26T18:00:00.00Z")); // Tuesday, 18:00
        DeploymentTester tester = new DeploymentTester(new ControllerTester(clock));
        Version version = Version.fromString("5.0");
        tester.upgradeSystem(version);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                // Block upgrades on Tuesday in hours 18 and 19
                .blockChange(false, true, "tue", "18-19", "UTC")
                .region("us-west-1")
                .build();

        Application app = tester.createAndDeploy("app1", 1, applicationPackage);

        // New version is released
        version = Version.fromString("5.1");
        tester.upgradeSystem(version);

        // Application is not upgraded at this time
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertTrue("No jobs scheduled", tester.buildService().jobs().isEmpty());

        // One hour passes, time is 19:00, still no upgrade
        tester.clock().advance(Duration.ofHours(1));
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertTrue("No jobs scheduled", tester.buildService().jobs().isEmpty());

        // Two hours pass in total, time is 20:00 and application upgrades
        tester.clock().advance(Duration.ofHours(1));
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertFalse("Job is scheduled", tester.buildService().jobs().isEmpty());
        tester.completeUpgrade(app, version, applicationPackage);
        assertTrue("All jobs consumed", tester.buildService().jobs().isEmpty());
    }

    @Test
    public void testBlockVersionChangeHalfwayThough() {
        ManualClock clock = new ManualClock(Instant.parse("2017-09-26T17:00:00.00Z")); // Tuesday, 17:00
        DeploymentTester tester = new DeploymentTester(new ControllerTester(clock));
        ReadyJobsTrigger readyJobsTrigger = new ReadyJobsTrigger(tester.controller(),
                                                                 Duration.ofHours(1),
                                                                 new JobControl(tester.controllerTester().curator()));

        Version version = Version.fromString("5.0");
        tester.upgradeSystem(version);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                // Block upgrades on Tuesday in hours 18 and 19
                .blockChange(false, true, "tue", "18-19", "UTC")
                .region("us-west-1")
                .region("us-central-1")
                .region("us-east-3")
                .build();

        Application app = tester.createAndDeploy("app1", 1, applicationPackage);

        // New version is released
        version = Version.fromString("5.1");
        tester.upgradeSystem(version);

        // Application upgrade starts
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        tester.deployAndNotify(app, applicationPackage, true, systemTest);
        tester.deployAndNotify(app, applicationPackage, true, stagingTest);
        clock.advance(Duration.ofHours(1)); // Entering block window after prod job is triggered
        tester.deployAndNotify(app, applicationPackage, true, productionUsWest1);
        assertTrue(tester.buildService().jobs().isEmpty()); // Next job not triggered due to being in the block window

        // One hour passes, time is 19:00, still no upgrade
        tester.clock().advance(Duration.ofHours(1));
        tester.triggerUntilQuiescence();
        assertTrue("No jobs scheduled", tester.buildService().jobs().isEmpty());

        // Another hour pass, time is 20:00 and application upgrades
        tester.clock().advance(Duration.ofHours(1));
        tester.triggerUntilQuiescence();
        tester.deployAndNotify(app, applicationPackage, true, productionUsCentral1);
        tester.deployAndNotify(app, applicationPackage, true, productionUsEast3);
        assertTrue("All jobs consumed", tester.buildService().jobs().isEmpty());
    }

    /**
     * Tests the scenario where a release is deployed to 2 of 3 production zones, then blocked,
     * followed by timeout of the upgrade and a new release.
     * In this case, the blocked production zone should not progress with upgrading to the previous version,
     * and should not upgrade to the new version until the other production zones have it
     * (expected behavior; both requirements are debatable).
     */
    @Test
    public void testBlockVersionChangeHalfwayThoughThenNewVersion() {
        ManualClock clock = new ManualClock(Instant.parse("2017-09-29T16:00:00.00Z")); // Friday, 16:00
        DeploymentTester tester = new DeploymentTester(new ControllerTester(clock));

        Version version = Version.fromString("5.0");
        tester.upgradeSystem(version);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                // Block upgrades on weekends and ouside working hours
                .blockChange(false, true, "mon-fri", "00-09,17-23", "UTC")
                .blockChange(false, true, "sat-sun", "00-23", "UTC")
                .region("us-west-1")
                .region("us-central-1")
                .region("us-east-3")
                .build();

        Application app = tester.createAndDeploy("app1", 1, applicationPackage);

        // New version is released
        version = Version.fromString("5.1");
        tester.upgradeSystem(version);

        // Application upgrade starts
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        tester.deployAndNotify(app, applicationPackage, true, systemTest);
        tester.deployAndNotify(app, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, productionUsWest1);
        clock.advance(Duration.ofHours(1)); // Entering block window after prod job is triggered
        tester.deployAndNotify(app, applicationPackage, true, productionUsCentral1);
        assertTrue(tester.buildService().jobs().isEmpty()); // Next job not triggered due to being in the block window

        // A day passes and we get a new version
        tester.clock().advance(Duration.ofDays(1));
        version = Version.fromString("5.2");
        tester.upgradeSystem(version);
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertTrue("Nothing is scheduled", tester.buildService().jobs().isEmpty());

        // Monday morning: We are not blocked
        tester.clock().advance(Duration.ofDays(1)); // Sunday, 17:00
        tester.clock().advance(Duration.ofHours(17)); // Monday, 10:00
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        tester.deployAndNotify(app, applicationPackage, true, systemTest);
        tester.deployAndNotify(app, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, productionUsWest1);
        tester.deployAndNotify(app, applicationPackage, true, productionUsCentral1);
        // us-east-3 has an older version than the other zones, and needs a new staging test run.
        tester.deployAndNotify(app, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, productionUsEast3);
        assertTrue("All jobs consumed", tester.buildService().jobs().isEmpty());

        // App is completely upgraded to the latest version
        for (Deployment deployment : tester.applications().require(app.id()).deployments().values())
            assertEquals(version, deployment.version());
    }

    @Test
    public void testReschedulesUpgradeAfterTimeout() {
        DeploymentTester tester = new DeploymentTester();
        Version version = Version.fromString("5.0");
        tester.upgradeSystem(version);

        ApplicationPackage canaryApplicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        ApplicationPackage defaultApplicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        // Setup applications
        Application canary0 = tester.createAndDeploy("canary0", 1, canaryApplicationPackage);
        Application canary1 = tester.createAndDeploy("canary1", 2, canaryApplicationPackage);
        Application default0 = tester.createAndDeploy("default0", 3, defaultApplicationPackage);
        Application default1 = tester.createAndDeploy("default1", 4, defaultApplicationPackage);
        Application default2 = tester.createAndDeploy("default2", 5, defaultApplicationPackage);
        Application default3 = tester.createAndDeploy("default3", 6, defaultApplicationPackage);
        Application default4 = tester.createAndDeploy("default4", 7, defaultApplicationPackage);

        assertEquals(version, default0.oldestDeployedPlatform().get());

        // New version is released
        version = Version.fromString("5.1");
        tester.upgradeSystem(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        // Canaries upgrade and raise confidence
        tester.completeUpgrade(canary0, version, canaryApplicationPackage);
        tester.completeUpgrade(canary1, version, canaryApplicationPackage);
        tester.upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());

        // Applications with default policy start upgrading
        tester.clock().advance(Duration.ofMinutes(1));
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();
        assertEquals("Upgrade scheduled for remaining apps", 10, tester.buildService().jobs().size());

        // 4/5 applications fail, confidence is lowered and upgrade is cancelled
        tester.completeUpgradeWithError(default0, version, defaultApplicationPackage, systemTest);
        tester.completeUpgradeWithError(default1, version, defaultApplicationPackage, systemTest);
        tester.completeUpgradeWithError(default2, version, defaultApplicationPackage, systemTest);
        tester.completeUpgradeWithError(default3, version, defaultApplicationPackage, systemTest);
        tester.upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.broken, tester.controller().versionStatus().systemVersion().get().confidence());

        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        // Finish runs
        tester.jobCompletion(systemTest).application(default0).unsuccessful().submit();
        tester.jobCompletion(systemTest).application(default1).unsuccessful().submit();
        tester.jobCompletion(systemTest).application(default2).unsuccessful().submit();
        tester.jobCompletion(systemTest).application(default3).unsuccessful().submit();
        tester.jobCompletion(stagingTest).application(default0).unsuccessful().submit();
        tester.jobCompletion(stagingTest).application(default1).unsuccessful().submit();
        tester.jobCompletion(stagingTest).application(default2).unsuccessful().submit();
        tester.jobCompletion(stagingTest).application(default3).unsuccessful().submit();
        tester.jobCompletion(stagingTest).application(default4).unsuccessful().submit();

        // 5th app never reports back and has a dead job, but no ongoing change
        Application deadLocked = tester.applications().require(default4.id());
        tester.assertRunning(deadLocked.id(), systemTest);
        assertFalse("No change present", deadLocked.change().isPresent());

        // 4 out of 5 applications are repaired and confidence is restored
        ApplicationPackage defaultApplicationPackageV2 = new ApplicationPackageBuilder()
                .searchDefinition("search test { field test type string {} }")
                .upgradePolicy("default")
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        tester.deployCompletely(default0, defaultApplicationPackageV2, 43);
        tester.deployCompletely(default1, defaultApplicationPackageV2, 43);
        tester.deployCompletely(default2, defaultApplicationPackageV2, 43);
        tester.deployCompletely(default3, defaultApplicationPackageV2, 43);

        tester.upgradeSystem(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());

        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        assertEquals("Upgrade scheduled for previously failing apps, and hanging job still running", 9, tester.buildService().jobs().size());
        tester.completeUpgrade(default0, version, defaultApplicationPackageV2);
        tester.completeUpgrade(default1, version, defaultApplicationPackageV2);
        tester.completeUpgrade(default2, version, defaultApplicationPackageV2);
        tester.completeUpgrade(default3, version, defaultApplicationPackageV2);

        assertEquals(version, tester.application(default0.id()).oldestDeployedPlatform().get());
        assertEquals(version, tester.application(default1.id()).oldestDeployedPlatform().get());
        assertEquals(version, tester.application(default2.id()).oldestDeployedPlatform().get());
        assertEquals(version, tester.application(default3.id()).oldestDeployedPlatform().get());
    }

    @Test
    public void testThrottlesUpgrades() {
        DeploymentTester tester = new DeploymentTester();
        Version version = Version.fromString("5.0");
        tester.upgradeSystem(version);

        // Setup our own upgrader as we need to control the interval
        Upgrader upgrader = new Upgrader(tester.controller(), Duration.ofMinutes(10),
                                         new JobControl(tester.controllerTester().curator()),
                                         tester.controllerTester().curator());
        upgrader.setUpgradesPerMinute(0.2);

        // Setup applications
        Application canary0 = tester.createAndDeploy("canary0", 1, "canary");
        Application canary1 = tester.createAndDeploy("canary1", 2, "canary");
        Application default0 = tester.createAndDeploy("default0", 3, "default");
        Application default1 = tester.createAndDeploy("default1", 4, "default");
        Application default2 = tester.createAndDeploy("default2", 5, "default");
        Application default3 = tester.createAndDeploy("default3", 6, "default");

        // Dev deployment which should be ignored
        Application dev0 = tester.createApplication("dev0", "tenant1", 7, 1L);
        tester.controllerTester().deploy(dev0, ZoneId.from(Environment.dev, RegionName.from("dev-region")));

        // New version is released and canaries upgrade
        version = Version.fromString("5.1");
        tester.upgradeSystem(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.triggerUntilQuiescence();

        assertEquals(4, tester.buildService().jobs().size());
        tester.completeUpgrade(canary0, version, "canary");
        tester.completeUpgrade(canary1, version, "canary");
        tester.upgradeSystem(version);

        // Next run upgrades a subset
        tester.readyJobTrigger().maintain();
        assertEquals(4, tester.buildService().jobs().size());
        tester.completeUpgrade(default0, version, "default");
        tester.completeUpgrade(default1, version, "default");

        // Remaining applications upgraded
        upgrader.maintain();
        tester.triggerUntilQuiescence();
        assertEquals(4, tester.buildService().jobs().size());
        tester.completeUpgrade(default2, version, "default");
        tester.completeUpgrade(default3, version, "default");
        upgrader.maintain();
        tester.triggerUntilQuiescence();
        assertTrue("All jobs consumed", tester.buildService().jobs().isEmpty());
    }

    @Test
    public void testAllowApplicationChangeDuringFailingUpgrade() {
        DeploymentTester tester = new DeploymentTester();
        Version version = Version.fromString("5.0");
        tester.upgradeSystem(version);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        Application app = tester.createAndDeploy("app1", 1, applicationPackage);

        // New version is released
        version = Version.fromString("5.1");
        tester.upgradeSystem(version);
        tester.upgrader().maintain();
        tester.triggerUntilQuiescence();

        tester.deployAndNotify(app, applicationPackage, true, systemTest);
        tester.deployAndNotify(app, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app, applicationPackage, false, productionUsWest1);

        // New application change
        tester.jobCompletion(component).application(app).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        String applicationVersion = "1.0.43-commit1";

        // Application change recorded together with ongoing upgrade
        app = tester.application(app.id());
        assertTrue("Change contains both upgrade and application change",
                   app.change().platform().get().equals(version) &&
                   app.change().application().get().id().equals(applicationVersion));

        // Deployment completes
        tester.deployAndNotify(app, applicationPackage, true, systemTest);
        tester.deployAndNotify(app, applicationPackage, true, stagingTest);
        tester.jobCompletion(productionUsWest1).application(app).unsuccessful().submit();
        tester.deployAndNotify(app, applicationPackage, true, productionUsWest1);
        assertTrue("All jobs consumed", tester.buildService().jobs().isEmpty());

        app = tester.application(app.id());
        for (Deployment deployment : app.deployments().values()) {
            assertEquals(version, deployment.version());
            assertEquals(applicationVersion, deployment.applicationVersion().id());
        }
    }

}
