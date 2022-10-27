// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.devUsEast1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionUsCentral1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.stagingTest;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.systemTest;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger.ChangesToCancel.ALL;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger.ChangesToCancel.PIN;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger.ChangesToCancel.PLATFORM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class UpgraderTest {

    private final DeploymentTester tester = new DeploymentTester().atMondayMorning();

    @Test
    void testUpgrading() {
        // --- Setup
        Version version0 = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version0);
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(0, tester.jobs().active().size(), "No applications: Nothing to do");

        // Setup applications
        var canary0 = createAndDeploy("canary0", "canary");
        var canary1 = createAndDeploy("canary1", "canary");
        var default0 = createAndDeploy("default0", "default");
        var default1 = createAndDeploy("default1", "default");
        var default2 = createAndDeploy("default2", "default");
        var conservative0 = createAndDeploy("conservative0", "conservative");

        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(0, tester.jobs().active().size(), "All already on the right version: Nothing to do");

        // --- Next version released - everything goes smoothly
        Version version1 = Version.fromString("6.3");
        tester.controllerTester().upgradeSystem(version1);
        assertEquals(version1, tester.controller().readVersionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.triggerJobs();

        assertEquals(4, tester.jobs().active().size(), "New system version: Should upgrade Canaries");
        canary0.deployPlatform(version1);
        assertEquals(version1, tester.configServer().lastPrepareVersion().get());

        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size(), "One canary pending; nothing else");

        canary1.deployPlatform(version1);

        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.normal, tester.controller().readVersionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(6, tester.jobs().active().size(), "Canaries done: Should upgrade defaults");

        default0.deployPlatform(version1);
        default1.deployPlatform(version1);
        default2.deployPlatform(version1);

        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.high, tester.controller().readVersionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size(), "Normals done: Should upgrade conservatives");
        conservative0.deployPlatform(version1);

        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(0, tester.jobs().active().size(), "Nothing to do");

        // --- Next version released - which fails a Canary
        Version version2 = Version.fromString("6.4");
        tester.controllerTester().upgradeSystem(version2);
        assertEquals(version2, tester.controller().readVersionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.triggerJobs();

        assertEquals(4, tester.jobs().active().size(), "New system version: Should upgrade Canaries");
        canary0.runJob(systemTest);
        canary0.failDeployment(stagingTest);

        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.broken, tester.controller().readVersionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(3, tester.jobs().active().size(), "Version broken, but Canaries should keep trying");

        // --- Next version released - which repairs the Canary app and fails a default
        Version version3 = Version.fromString("6.5");
        tester.controllerTester().upgradeSystem(version3);
        assertEquals(version3, tester.controller().readVersionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        canary0.abortJob(stagingTest);
        canary1.abortJob(systemTest);
        canary1.abortJob(stagingTest);
        tester.triggerJobs();

        assertEquals(4, tester.jobs().active().size(), "New system version: Should upgrade Canaries");
        canary0.deployPlatform(version3);
        assertEquals(version3, tester.configServer().lastPrepareVersion().get());

        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size(), "One canary pending; nothing else");

        canary1.deployPlatform(version3);

        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.normal, tester.controller().readVersionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerJobs();

        assertEquals(6, tester.jobs().active().size(), "Canaries done: Should upgrade defaults");

        default0.runJob(systemTest);
        default0.failDeployment(stagingTest);
        default1.deployPlatform(version3);
        default2.deployPlatform(version3);

        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.normal, tester.controller().readVersionStatus().systemVersion().get().confidence(), "Not enough evidence to mark this as neither broken nor high");

        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size(), "Upgrade with error should retry");

        // --- Failing application is repaired by changing the application, causing confidence to move above 'high' threshold
        // Deploy application change
        default0.submit(applicationPackage("default"));
        default0.deploy();

        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.high, tester.controller().readVersionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size(), "Normals done: Should upgrade conservatives");
        conservative0.deployPlatform(version3);

        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(0, tester.jobs().active().size(), "Applications are on " + version3 + " - nothing to do");

        // --- Starting upgrading to a new version which breaks, causing upgrades to commence on the previous version
        var default3 = createAndDeploy("default3", "default");
        var default4 = createAndDeploy("default4", "default");
        var default5 = createAndDeploy("default5", "default");
        Version version4 = Version.fromString("6.6");
        tester.controllerTester().upgradeSystem(version4);
        tester.upgrader().maintain(); // cause canary upgrades to new version
        tester.triggerJobs();
        canary0.deployPlatform(version4);
        canary1.deployPlatform(version4);
        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.normal, tester.controller().readVersionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerJobs();

        assertEquals(12, tester.jobs().active().size(), "Upgrade of defaults are scheduled");
        for (var context : List.of(default0, default1, default2, default3, default4, default5))
            assertEquals(version4, context.instance().change().platform().get());

        default0.deployPlatform(version4);

        // State: Default applications started upgrading to version4 (and one completed)
        Version version5 = Version.fromString("6.7");
        tester.controllerTester().upgradeSystem(version5);
        tester.upgrader().maintain(); // cause canary upgrades to version5
        tester.triggerJobs();
        canary0.deployPlatform(version5);
        canary1.deployPlatform(version5);
        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.normal, tester.controller().readVersionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerJobs();

        assertEquals(12, tester.jobs().active().size(), "Upgrade of defaults are scheduled");
        assertEquals(version5, default0.instance().change().platform().get());
        for (var context : List.of(default1, default2, default3, default4, default5))
            assertEquals(version4, context.instance().change().platform().get());

        default1.deployPlatform(version4);
        default2.deployPlatform(version4);

        default3.runJob(systemTest)
                .failDeployment(stagingTest);

        default4.runJob(systemTest)
                .runJob(stagingTest)
                .failDeployment(productionUsWest1);

        default5.runJob(systemTest)
                .runJob(stagingTest)
                .failDeployment(productionUsWest1);

        // State: Default applications started upgrading to version5
        tester.clock().advance(Duration.ofHours(1));
        tester.upgrader().maintain();
        default3.triggerJobs().jobAborted(stagingTest);
        default0.runJob(systemTest)
                .failDeployment(stagingTest);
        default1.runJob(systemTest)
                .failDeployment(stagingTest);
        default2.runJob(systemTest)
                .failDeployment(stagingTest);

        default3.runJob(systemTest)
                .runJob(stagingTest)
                .failDeployment(productionUsWest1);
        default4.failDeployment(systemTest);
        default5.failDeployment(systemTest);

        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.broken, tester.controller().readVersionStatus().systemVersion().get().confidence());

        tester.upgrader().maintain();
        assertEquals(version4, default3.instance().change().platform().get());
    }

    @Test
    void testUpgradingToVersionWhichBreaksSomeNonCanaries() {
        // --- Setup
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(0, tester.jobs().active().size(), "No system version: Nothing to do");

        Version version = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version);
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(0, tester.jobs().active().size(), "No applications: Nothing to do");

        // Setup applications
        var canary0  = createAndDeploy("canary0", "canary");
        var canary1  = createAndDeploy("canary1", "canary");
        var default0 = createAndDeploy("default0", "default");
        var default1 = createAndDeploy("default1", "default");
        var default2 = createAndDeploy("default2", "default");
        var default3 = createAndDeploy("default3", "default");
        var default4 = createAndDeploy("default4", "default");
        var default5 = createAndDeploy("default5", "default");
        var default6 = createAndDeploy("default6", "default");
        var default7 = createAndDeploy("default7", "default");
        var default8 = createAndDeploy("default8", "default");
        var default9 = createAndDeploy("default9", "default");

        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(0, tester.jobs().active().size(), "All already on the right version: Nothing to do");

        // --- A new version is released
        version = Version.fromString("6.3");
        tester.controllerTester().upgradeSystem(version);
        assertEquals(version, tester.controller().readVersionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.triggerJobs();

        assertEquals(4, tester.jobs().active().size(), "New system version: Should upgrade Canaries");
        canary0.deployPlatform(version);
        assertEquals(version, tester.configServer().lastPrepareVersion().get());

        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size(), "One canary pending; nothing else");

        canary1.deployPlatform(version);

        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.normal, tester.controller().readVersionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(20, tester.jobs().active().size(), "Canaries done: Should upgrade defaults");

        default0.deployPlatform(version);
        for (var context : List.of(default1, default2, default3, default4, default5, default6))
            context.failDeployment(systemTest);

        // > 60% and at least 6 failed - version is broken
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        tester.abortAll();
        tester.triggerJobs();
        assertEquals(VespaVersion.Confidence.broken, tester.controller().readVersionStatus().systemVersion().get().confidence());
        assertEquals(0, tester.jobs().active().size(), "Upgrades are cancelled");
    }

    //  Scenario:
    //   An application A is on version V0
    //   Version V2 is released.
    //   A upgrades one production zone to V2.
    //   V2 is marked as broken and upgrade of A to V2 is cancelled.
    //   Upgrade of A to V1 is scheduled: Should skip the zone on V2 but upgrade the next zone to V1
    @Test
    void testVersionIsBrokenAfterAZoneIsLive() {
        Version v0 = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(v0);

        // Setup applications on V0
        var canary0 = createAndDeploy("canary0", "canary");
        var canary1 = createAndDeploy("canary1", "canary");
        var default0 = createAndDeploy("default0", "default");
        var default1 = createAndDeploy("default1", "default");
        var default2 = createAndDeploy("default2", "default");
        var default3 = createAndDeploy("default3", "default");
        var default4 = createAndDeploy("default4", "default");
        var default5 = createAndDeploy("default5", "default");
        var default6 = createAndDeploy("default6", "default");

        // V1 is released
        Version v1 = Version.fromString("6.3");
        tester.controllerTester().upgradeSystem(v1);
        assertEquals(v1, tester.controller().readVersionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.triggerJobs();

        // Canaries upgrade and raise confidence of V1 (other apps are not upgraded)
        canary0.deployPlatform(v1);
        canary1.deployPlatform(v1);
        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.normal, tester.controller().readVersionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        tester.triggerJobs();

        // V2 is released
        Version v2 = Version.fromString("6.4");
        tester.controllerTester().upgradeSystem(v2);
        assertEquals(v2, tester.controller().readVersionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.triggerJobs();

        // Canaries upgrade and raise confidence of V2
        canary0.deployPlatform(v2);
        canary1.deployPlatform(v2);
        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.normal, tester.controller().readVersionStatus().systemVersion().get().confidence());

        // We "manually" cancel upgrades to V1 so that we can use the applications to make V2 fail instead
        // But we keep one (default6) to avoid V1 being garbage collected
        tester.deploymentTrigger().cancelChange(default0.instanceId(), ALL);
        tester.deploymentTrigger().cancelChange(default1.instanceId(), ALL);
        tester.deploymentTrigger().cancelChange(default2.instanceId(), ALL);
        tester.deploymentTrigger().cancelChange(default3.instanceId(), ALL);
        tester.deploymentTrigger().cancelChange(default4.instanceId(), ALL);
        tester.deploymentTrigger().cancelChange(default5.instanceId(), ALL);
        default0.abortJob(systemTest).abortJob(stagingTest);
        default1.abortJob(systemTest).abortJob(stagingTest);
        default2.abortJob(systemTest).abortJob(stagingTest);
        default3.abortJob(systemTest).abortJob(stagingTest);
        default4.abortJob(systemTest).abortJob(stagingTest);
        default5.abortJob(systemTest).abortJob(stagingTest);

        // Applications with default policy start upgrading to V2
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(14, tester.jobs().active().size(), "Upgrade scheduled for remaining apps");
        assertEquals(v1, default6.instance().change().platform().get(), "default6 is still upgrading to 6.3");

        // 4/5 applications fail (in the last prod zone) and lowers confidence
        default0.runJob(systemTest).runJob(stagingTest).runJob(productionUsWest1).failDeployment(productionUsEast3);
        default1.runJob(systemTest).runJob(stagingTest).runJob(productionUsWest1).failDeployment(productionUsEast3);
        default2.runJob(systemTest).runJob(stagingTest).runJob(productionUsWest1).failDeployment(productionUsEast3);
        default3.runJob(systemTest).runJob(stagingTest).runJob(productionUsWest1).failDeployment(productionUsEast3);
        default4.runJob(systemTest).runJob(stagingTest).runJob(productionUsWest1).failDeployment(productionUsEast3);
        default5.runJob(systemTest).runJob(stagingTest).runJob(productionUsWest1).failDeployment(productionUsEast3);
        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.broken, tester.controller().readVersionStatus().systemVersion().get().confidence());

        assertEquals(v2, default0.deployment(ZoneId.from("prod.us-west-1")).version());
        assertEquals(v0, default0.deployment(ZoneId.from("prod.us-east-3")).version());
        tester.upgrader().maintain();
        tester.abortAll();
        tester.triggerJobs();

        assertEquals(14, tester.jobs().active().size(), "Upgrade to 5.1 scheduled for apps not completely on 5.1 or 5.2");

        // prod zone on 5.2 (usWest1) is skipped, but we still trigger the next zone from triggerReadyJobs:
        default0.runJob(systemTest).runJob(stagingTest).runJob(productionUsEast3);

        // Resulting state:
        assertEquals(v2, default0.deployment(ZoneId.from("prod.us-west-1")).version());
        assertEquals(v1, default0.deployment(ZoneId.from("prod.us-east-3")).version(), "Last zone is upgraded to v1");
        assertFalse(default0.instance().change().hasTargets());
    }

    @Test
    void testConfidenceIgnoresFailingApplicationChanges() {
        Version version = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version);

        // Setup applications
        var canary0 = createAndDeploy("canary0", "canary");
        var canary1 = createAndDeploy("canary1", "canary");
        var default0 = createAndDeploy("default0", "default");
        var default1 = createAndDeploy("default1", "default");
        var default2 = createAndDeploy("default2", "default");
        var default3 = createAndDeploy("default3", "default");
        var default4 = createAndDeploy("default4", "default");

        // New version is released
        version = Version.fromString("6.3");
        tester.controllerTester().upgradeSystem(version);
        assertEquals(version, tester.controller().readVersionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.triggerJobs();

        // Canaries upgrade and raise confidence
        canary0.deployPlatform(version);
        canary1.deployPlatform(version);
        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.normal, tester.controller().readVersionStatus().systemVersion().get().confidence());

        // All applications upgrade successfully
        tester.upgrader().maintain();
        tester.triggerJobs();
        for (var context : List.of(default0, default1, default2, default3, default4))
            context.deployPlatform(version);
        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.high, tester.controller().readVersionStatus().systemVersion().get().confidence());

        // Multiple application changes are triggered and fail, but does not affect version confidence as upgrade has
        // completed successfully
        ApplicationPackage applicationPackage = applicationPackage("default");
        default0.submit(applicationPackage).failDeployment(systemTest);
        default1.submit(applicationPackage).failDeployment(stagingTest);
        default2.submit(applicationPackage).failDeployment(systemTest);
        default3.submit(applicationPackage).failDeployment(stagingTest);
        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.high, tester.controller().readVersionStatus().systemVersion().get().confidence());
    }

    @Test
    void testBlockVersionChange() {
        // Tuesday, 18:00
        tester.at(Instant.parse("2017-09-26T18:00:00.00Z"));
        Version version = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                // Block upgrades on Tuesday in hours 18 and 19
                .blockChange(false, true, "tue", "18-19", "UTC")
                .region("us-west-1")
                .build();

        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();

        // New version is released
        version = Version.fromString("6.3");
        tester.controllerTester().upgradeSystem(version);

        // Application is not upgraded at this time
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertTrue(tester.jobs().active().isEmpty(), "No jobs scheduled");

        // One hour passes, time is 19:00, still no upgrade
        tester.clock().advance(Duration.ofHours(1));
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertTrue(tester.jobs().active().isEmpty(), "No jobs scheduled");

        // Two hours pass in total, time is 20:00 and application upgrades
        tester.clock().advance(Duration.ofHours(1));
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertFalse(tester.jobs().active().isEmpty(), "Job is scheduled");
        app.deployPlatform(version);
        assertTrue(tester.jobs().active().isEmpty(), "All jobs consumed");
    }

    @Test
    void testBlockVersionChangeHalfwayThough() {
        // Tuesday, 17:00
        tester.at(Instant.parse("2017-09-26T17:00:00.00Z"));

        Version version = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                // Block upgrades on Tuesday in hours 18 and 19
                .blockChange(false, true, "tue", "18-19", "UTC")
                .region("us-west-1")
                .region("us-central-1")
                .region("us-east-3")
                .build();

        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();

        // New version is released
        version = Version.fromString("6.3");
        tester.controllerTester().upgradeSystem(version);

        // Application upgrade starts
        tester.upgrader().maintain();
        tester.triggerJobs();
        app.runJob(systemTest).runJob(stagingTest);
        tester.triggerJobs();
        tester.clock().advance(Duration.ofHours(1)); // Entering block window after prod job is triggered
        app.runJob(productionUsWest1);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size()); // Next job triggered because upgrade is already rolling out.

        app.runJob(productionUsCentral1).runJob(productionUsEast3);
        assertTrue(tester.jobs().active().isEmpty(), "All jobs consumed");
    }

    @Test
    void testBlockVersionChangeHalfwayThroughThenNewRevision() {
        // Friday, 16:00
        tester.at(Instant.parse("2017-09-29T16:00:00.00Z"));

        Version version = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                // Block upgrades on weekends and outside working hours
                .blockChange(false, true, "mon-fri", "00-09,17-23", "UTC")
                .blockChange(false, true, "sat-sun", "00-23", "UTC")
                .region("us-west-1")
                .region("us-central-1")
                .region("us-east-3")
                .build();

        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();

        // New version is released
        version = Version.fromString("6.3");
        tester.controllerTester().upgradeSystem(version);

        // Application upgrade starts
        tester.upgrader().maintain();
        tester.triggerJobs();
        app.runJob(systemTest).runJob(stagingTest);
        tester.clock().advance(Duration.ofHours(1)); // Entering block window after prod job is triggered
        app.runJob(productionUsWest1);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size()); // Next job triggered, as upgrade is already in progress.
        // us-central-1 fails, permitting a new revision.
        app.failDeployment(productionUsCentral1);

        // A new revision is submitted and starts rolling out.
        app.submit(applicationPackage);

        // production-us-central-1 is re-triggered with upgrade until revision catches up.
        tester.triggerJobs();
        assertEquals(3, tester.jobs().active().size());
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsWest1);
        // us-central-1 has an older version, and needs a new staging test to begin.
        app.runJob(stagingTest).triggerJobs().jobAborted(productionUsCentral1); // Retry will include revision.
        tester.triggerJobs(); // Triggers us-central-1 before platform upgrade is cancelled.

        // A new version is also released, and someone cancels the upgrade, suspecting it is faulty.
        tester.clock().advance(Duration.ofHours(17));
        version = Version.fromString("6.4");
        tester.controllerTester().upgradeSystem(version);
        tester.upgrader().maintain();
        tester.deploymentTrigger().cancelChange(app.instanceId(), PLATFORM);

        // us-central-1 succeeds upgrade to 6.3, with the revision, but us-east-3 wants to proceed with only the revision change.
        app.runJob(productionUsCentral1);
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsEast3);
        assertEquals(List.of(), tester.jobs().active());

        assertEquals(new Version("6.3"), app.deployment(ZoneId.from("prod", "us-central-1")).version());
        assertEquals(new Version("6.2"), app.deployment(ZoneId.from("prod", "us-east-3")).version());

        // Monday morning: We are not blocked, and the new version rolls out to all zones.
        tester.clock().advance(Duration.ofDays(2)); // Monday, 10:00
        tester.upgrader().maintain();
        tester.triggerJobs();
        app.runJob(systemTest)
                .runJob(stagingTest)
                .runJob(productionUsWest1)
                .runJob(productionUsCentral1)
                .runJob(stagingTest)
                .runJob(productionUsEast3);
        assertTrue(tester.jobs().active().isEmpty(), "All jobs consumed");

        // App is completely upgraded to the latest version
        for (Deployment deployment : app.instance().deployments().values())
            assertEquals(version, deployment.version());
    }

    @Test
    void testThrottlesUpgrades() {
        Version version = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version);

        // Setup our own upgrader as we need to control the interval
        Upgrader upgrader = new Upgrader(tester.controller(), Duration.ofMinutes(10));
        upgrader.setUpgradesPerMinute(0.2);

        // Setup applications
        var canary0 = createAndDeploy("canary0", "canary");
        var canary1 = createAndDeploy("canary1", "canary");
        var canary2 = createAndDeploy("canary2", "canary");
        var default0 = createAndDeploy("default0", "default");
        var default1 = createAndDeploy("default1", "default");
        var default2 = createAndDeploy("default2", "default");
        var default3 = createAndDeploy("default3", "default");

        // Dev deployment which should be ignored
        var dev0 = tester.newDeploymentContext("tenant1", "dev0", "default")
                .runJob(devUsEast1, DeploymentContext.applicationPackage());

        // New version is released and canaries upgrade
        version = Version.fromString("6.3");
        tester.controllerTester().upgradeSystem(version);
        assertEquals(version, tester.controller().readVersionStatus().systemVersion().get().versionNumber());
        upgrader.maintain();
        tester.triggerJobs();

        // canaries are not throttled
        assertEquals(6, tester.jobs().active().size());
        canary0.deployPlatform(version);
        canary1.deployPlatform(version);
        canary2.deployPlatform(version);
        assertEquals(0, tester.jobs().active().size());
        tester.controllerTester().computeVersionStatus();

        // Next run upgrades a subset
        upgrader.maintain();
        tester.triggerJobs();
        assertEquals(4, tester.jobs().active().size());

        // Remaining applications upgraded
        upgrader.maintain();
        tester.triggerJobs();
        assertEquals(8, tester.jobs().active().size());
    }

    @Test
    void testPinningMajorVersionInDeploymentXml() {
        Version version0 = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version0);

        ApplicationPackageBuilder builder = new ApplicationPackageBuilder().region("us-west-1").majorVersion(7);
        ApplicationPackage defaultPackage = new ApplicationPackageBuilder().region("us-west-1").build();

        // Setup applications
        var canaryApp = tester.newDeploymentContext("canary", "app", "default").submit(builder.upgradePolicy("canary").build()).deploy();
        var defaultApp = tester.newDeploymentContext("normal", "app", "default").submit(builder.upgradePolicy("default").build()).deploy();
        var conservativeApp = tester.newDeploymentContext("conservative", "app", "default").submit(builder.upgradePolicy("conservative").build()).deploy();
        var lazyApp = tester.newDeploymentContext().submit(defaultPackage).deploy();

        // New major version is released; more apps upgrade with increasing confidence.
        Version version = Version.fromString("7.0");
        tester.controllerTester().upgradeSystem(version);
        tester.upgrader().overrideConfidence(version, Confidence.broken);
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        assertEquals(Change.of(version), canaryApp.instance().change());
        assertEquals(Change.empty(), defaultApp.instance().change());
        assertEquals(Change.empty(), conservativeApp.instance().change());
        assertEquals(Change.empty(), lazyApp.instance().change());

        tester.upgrader().overrideConfidence(version, Confidence.low);
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        assertEquals(Change.of(version), canaryApp.instance().change());
        assertEquals(Change.empty(), defaultApp.instance().change());
        assertEquals(Change.empty(), conservativeApp.instance().change());
        assertEquals(Change.empty(), lazyApp.instance().change());

        tester.upgrader().overrideConfidence(version, Confidence.normal);
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        assertEquals(Change.of(version), canaryApp.instance().change());
        assertEquals(Change.of(version), defaultApp.instance().change());
        assertEquals(Change.empty(), conservativeApp.instance().change());
        assertEquals(Change.empty(), lazyApp.instance().change());

        tester.upgrader().overrideConfidence(version, Confidence.high);
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        assertEquals(Change.of(version), canaryApp.instance().change());
        assertEquals(Change.of(version), defaultApp.instance().change());
        assertEquals(Change.of(version), conservativeApp.instance().change());
        assertEquals(Change.empty(), lazyApp.instance().change());
    }

    @Test
    void testAllowApplicationChangeDuringFailingUpgrade() {
        Version version = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version);

        var app = createAndDeploy("app", "default");

        // New version is released
        version = Version.fromString("6.3");
        tester.controllerTester().upgradeSystem(version);
        tester.upgrader().maintain();
        tester.triggerJobs();

        app.runJob(systemTest).runJob(stagingTest).failDeployment(productionUsWest1);

        // New application change
        app.submit(applicationPackage("default"));
        RevisionId revision = app.lastSubmission().get();

        // Application change recorded together with ongoing upgrade
        assertTrue(app.instance().change().platform().get().equals(version) &&
                   app.instance().change().revision().get().equals(revision),
                   "Change contains both upgrade and application change");

        // Deployment completes
        app.runJob(systemTest).runJob(stagingTest)
           .runJob(productionUsWest1)
           .runJob(productionUsEast3);
        assertEquals(List.of(), tester.jobs().active(), "All jobs consumed");

        for (Deployment deployment : app.instance().deployments().values()) {
            assertEquals(version, deployment.version());
            assertEquals(revision, deployment.revision());
        }
    }

    @Test
    void testBlockRevisionChangeHalfwayThoughThenUpgrade() {
        // Tuesday, 17:00.
        tester.at(Instant.parse("2017-09-26T17:00:00.00Z"));

        Version version = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                // Block revisions on Tuesday in hours 18 and 19.
                .blockChange(true, false, "tue", "18-19", "UTC")
                .region("us-west-1")
                .region("us-central-1")
                .region("us-east-3")
                .build();

        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();

        // Application upgrade starts.
        app.submit(applicationPackage);
        tester.clock().advance(Duration.ofHours(1)); // Entering block window after submission accepted.
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsWest1);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size()); // Next job triggered in spite of block, because it is already rolling out.

        // New version is released, but upgrades won't start since there's already a revision rolling out.
        version = Version.fromString("6.3");
        tester.controllerTester().upgradeSystem(version);
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size()); // Still just the revision upgrade.

        app.runJob(productionUsCentral1).runJob(productionUsEast3);
        assertEquals(List.of(), tester.jobs().active()); // No jobs left.

        // Upgrade may start, now that revision is rolled out.
        tester.upgrader().maintain();
        app.deployPlatform(version);
        assertTrue(tester.jobs().active().isEmpty(), "All jobs consumed");
    }

    @Test
    void testBlockRevisionChangeHalfwayThoughThenNewRevision() {
        // Tuesday, 17:00.
        tester.at(Instant.parse("2017-09-26T17:00:00.00Z"));

        Version version = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                // Block revision on Tuesday in hours 18 and 19.
                .blockChange(true, false, "tue", "18-19", "UTC")
                .region("us-west-1")
                .region("us-central-1")
                .region("us-east-3")
                .build();

        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();


        // Application revision starts rolling out.
        app.submit(applicationPackage);
        tester.clock().advance(Duration.ofHours(1)); // Entering block window after submission is accepted.
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsWest1);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());

        // New revision is submitted, but is stored as outstanding, since the upgrade is proceeding in good fashion.
        app.submit(applicationPackage);
        tester.triggerJobs();
        assertEquals(3, tester.jobs().active().size()); // Just the running upgrade, and tests for the new revision.

        app.runJob(productionUsCentral1).runJob(productionUsEast3).runJob(systemTest).runJob(stagingTest);
        assertEquals(List.of(), tester.jobs().active()); // No jobs left.

        tester.outstandingChangeDeployer().run();
        assertFalse(app.instance().change().hasTargets());
        tester.clock().advance(Duration.ofHours(2));

        tester.outstandingChangeDeployer().run();
        assertTrue(app.instance().change().hasTargets());
        app.runJob(productionUsWest1).runJob(productionUsCentral1).runJob(productionUsEast3);
        assertFalse(app.instance().change().hasTargets());
    }

    @Test
    void testPinning() {
        Version version0 = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version0);

        // Create an application with pinned platform version.
        var context = tester.newDeploymentContext().submit().deploy();
        tester.deploymentTrigger().forceChange(context.instanceId(), Change.empty().withPin());

        assertFalse(context.instance().change().hasTargets());
        assertTrue(context.instance().change().isPinned());
        assertEquals(3, context.instance().deployments().size());

        // Application does not upgrade.
        Version version1 = Version.fromString("6.3");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        assertFalse(context.instance().change().hasTargets());
        assertTrue(context.instance().change().isPinned());

        // New application package is deployed.
        context.submit().deploy();
        assertFalse(context.instance().change().hasTargets());
        assertTrue(context.instance().change().isPinned());

        // Application upgrades to new version when pin is removed.
        tester.deploymentTrigger().cancelChange(context.instanceId(), PIN);
        tester.upgrader().maintain();
        assertTrue(context.instance().change().hasTargets());
        assertFalse(context.instance().change().isPinned());

        // Application is pinned to new version, and upgrade is therefore not cancelled, even though confidence is broken.
        tester.deploymentTrigger().forceChange(context.instanceId(), Change.empty().withPin());
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals(version1, context.instance().change().platform().get());

        // Application fails upgrade after one zone is complete, and is pinned again to the old version.
        context.runJob(systemTest).runJob(stagingTest).runJob(productionUsCentral1)
                .timeOutUpgrade(productionUsWest1);
        tester.deploymentTrigger().cancelChange(context.instanceId(), ALL);
        tester.deploymentTrigger().forceChange(context.instanceId(), Change.of(version0).withPin());
        assertEquals(version0, context.instance().change().platform().get());

        // Application downgrades to pinned version.
        tester.abortAll();
        context.runJob(stagingTest).runJob(productionUsCentral1).runJob(productionUsWest1);
        assertTrue(context.instance().change().hasTargets());
        context.runJob(productionUsEast3);
        assertFalse(context.instance().change().hasTargets());
    }

    @Test
    void upgradesToLatestAllowedMajor() {
        Version version0 = Version.fromString("6.1");
        tester.controllerTester().upgradeSystem(version0);

        // All applications deploy on current version
        var app1 = createAndDeploy("app1", "default");
        var app2 = createAndDeploy("app2", "default");

        // Keep app 1 on current version
        tester.controller().applications().lockApplicationIfPresent(app1.application().id(), app ->
                tester.controller().applications().store(app.with(app1.instance().name(),
                        instance -> instance.withChange(instance.change().withPin()))));

        // New version is released
        Version version1 = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();

        // App 2 upgrades
        app2.deployPlatform(version1);

        // New major version is released
        Version version2 = Version.fromString("7.1");
        tester.controllerTester().upgradeSystem(version2);

        // App 2 has upgrade to new platform triggered
        tester.deploymentTrigger().forceChange(app2.instanceId(), Change.of(version2));
        tester.upgrader().maintain();
        assertEquals(Change.of(version2), app2.instance().change());

        // App 1 is unpinned and upgrades to latest 6
        tester.controller().applications().lockApplicationIfPresent(app1.application().id(), app ->
                tester.controller().applications().store(app.with(app1.instance().name(),
                        instance -> instance.withChange(instance.change().withoutPin()))));
        tester.upgrader().maintain();
        assertEquals(version1,
                     app1.instance().change().platform().orElseThrow(),
                     "Application upgrades to latest allowed major");

        // Version on old major becomes legacy, so app upgrades once it does not specify the old major in deployment spec.
        app1.runJob(systemTest).runJob(stagingTest).runJob(productionUsWest1).runJob(productionUsEast3);
        app1.submit(new ApplicationPackageBuilder().majorVersion(6).region("us-east-3").region("us-west-1").build()).deploy();
        tester.upgrader().maintain();
        assertEquals(Change.empty(), app1.instance().change());

        app1.submit(new ApplicationPackageBuilder().region("us-east-3").region("us-west-1").build()).deploy();
        tester.upgrader().overrideConfidence(version1, Confidence.legacy);
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        assertEquals(Change.of(version2), app1.instance().change());
    }

    @Test
    void testSettingFailingRevisionAside() {
        DeploymentContext app = tester.newDeploymentContext().submit().deploy();

        // New revision fails.
        app.submit();
        Optional<RevisionId> revision1 = app.lastSubmission();
        app.failDeployment(systemTest);

        // New version is not targeted.
        Version version1 = new Version("6.2");
        tester.controllerTester().upgradeSystem(version1);
        assertEquals(Change.of(revision1.get()), app.instance().change());

        tester.upgrader().run();
        assertEquals(Change.of(revision1.get()), app.instance().change());

        // Broken revision is replaced by a new attempt, which also fails, and cancellation is not yet triggered.
        tester.clock().advance(DeploymentTrigger.maxFailingRevisionTime.plusSeconds(1));
        app.submit();
        Optional<RevisionId> revision2 = app.lastSubmission();
        app.failDeployment(systemTest);
        tester.upgrader().run();
        assertEquals(Change.of(revision2.get()), app.instance().change());

        // Broken revision is cancelled, and new version targeted, after some time.
        tester.clock().advance(DeploymentTrigger.maxFailingRevisionTime.plusSeconds(1));
        tester.upgrader().run();
        assertEquals(Change.of(version1), app.instance().change());

        // Broken revision is not targeted again.
        app.triggerJobs();
        tester.upgrader().run();
        tester.outstandingChangeDeployer().run();
        assertEquals(Change.of(version1), app.instance().change());

        app.failDeployment(systemTest);
        tester.upgrader().run();
        tester.outstandingChangeDeployer().run();
        assertEquals(Change.of(version1), app.instance().change());

        // Revision gets a second change when upgrade fixes the failing job.
        tester.clock().advance(Duration.ofDays(12)); // Time for retries.
        app.runJob(systemTest).jobAborted(stagingTest).runJob(stagingTest).runJob(productionUsCentral1);
        tester.upgrader().run();
        tester.outstandingChangeDeployer().run();

        assertEquals(Change.of(version1).with(revision2.get()), app.instance().change());
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsCentral1); // Revision rolls.
        app.runJob(productionUsEast3).runJob(productionUsWest1); // Upgrade completes.
        app.runJob(productionUsEast3).runJob(productionUsWest1); // Revision completes.
        assertEquals(Change.empty(), app.instance().change());
    }

    @Test
    void testsEachUpgradeCombinationWithFailingDeployments() {
        Version v1 = Version.fromString("6.1");
        tester.controllerTester().upgradeSystem(v1);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-central-1")
                .region("us-west-1")
                .region("us-east-3")
                .build();
        var application = tester.newDeploymentContext().submit(applicationPackage).deploy();

        // Next version is released and 2/3 deployments upgrade
        Version v2 = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(v2);
        tester.upgrader().maintain();
        assertEquals(Change.of(v2), application.instance().change());
        application.runJob(systemTest).runJob(stagingTest).runJob(productionUsCentral1).timeOutConvergence(productionUsWest1);
        tester.triggerJobs();

        // While second deployment completes upgrade, version confidence becomes broken and upgrade is cancelled
        tester.upgrader().overrideConfidence(v2, VespaVersion.Confidence.broken);
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        application.runJob(productionUsWest1);
        assertTrue(application.instance().change().isEmpty());

        // Next version is released
        Version v3 = Version.fromString("6.3");
        tester.controllerTester().upgradeSystem(v3);
        tester.upgrader().maintain();
        assertEquals(Change.of(v3), application.instance().change());
        application.runJob(systemTest).runJob(stagingTest);

        // First deployment starts upgrading
        // Before deployment completes, v1->v3 combination is tested as us-east-3 is still on v1
        tester.triggerJobs();

        application.runJob(stagingTest);
        assertEquals(v1, application.instanceJobs().get(stagingTest).lastSuccess().get().versions().sourcePlatform().get());
        assertEquals(v3, application.instanceJobs().get(stagingTest).lastSuccess().get().versions().targetPlatform());

        // First deployment fails and then successfully upgrades to v3
        application.failDeployment(productionUsCentral1);
        application.runJob(productionUsCentral1);

        // Deployments are now on 3 versions
        assertEquals(v3, application.deployment(ZoneId.from("prod", "us-central-1")).version());
        assertEquals(v2, application.deployment(ZoneId.from("prod", "us-west-1")).version());
        assertEquals(v1, application.deployment(ZoneId.from("prod", "us-east-3")).version());

        // Second deployment upgrades
        application.runJob(productionUsWest1);

        // Upgrade completes
        application.runJob(productionUsEast3);
        assertTrue(application.instance().change().isEmpty(), "Upgrade complete");
    }

    @Test
    void testUpgradesPerMinute() {
        assertEquals(0, Upgrader.numberOfApplicationsToUpgrade(10, 0, 0));

        for (long now = 0; now < 60_000; now++)
            assertEquals(7, Upgrader.numberOfApplicationsToUpgrade(60_000, now, 7));

        // Upgrade an app after 8s, 16s, ..., 120s.
        assertEquals(3, Upgrader.numberOfApplicationsToUpgrade(30_000,       0, 7.5));
        assertEquals(4, Upgrader.numberOfApplicationsToUpgrade(30_000,  30_000, 7.5));
        assertEquals(4, Upgrader.numberOfApplicationsToUpgrade(30_000,  60_000, 7.5));
        assertEquals(4, Upgrader.numberOfApplicationsToUpgrade(30_000,  90_000, 7.5));
        assertEquals(3, Upgrader.numberOfApplicationsToUpgrade(30_000, 120_000, 7.5));

        // Run upgrades for 20 minutes.
        int upgrades = 0;
        for (int i = 0, now = 0; i < 30; i++, now += 40_000)
            upgrades += Upgrader.numberOfApplicationsToUpgrade(40_000, now, 8.7);
        assertEquals(174, upgrades);
    }

    @Test
    void testUpgradeShuffling() {
        // Deploy applications on initial version
        var default0 = createAndDeploy("default0", "default");
        var default1 = createAndDeploy("default1", "default");
        var default2 = createAndDeploy("default2", "default");
        var applications = Map.of(default0.instanceId(), default0,
                default1.instanceId(), default1,
                default2.instanceId(), default2);

        // Throttle upgrades per run
        ((ManualClock) tester.controller().clock()).setInstant(Instant.ofEpochMilli(1589787107000L)); // Fixed random seed
        Upgrader upgrader = new Upgrader(tester.controller(), Duration.ofMinutes(10));
        upgrader.setUpgradesPerMinute(0.1);

        // Trigger some upgrades
        List<Version> versions = List.of(Version.fromString("6.2"), Version.fromString("6.3"));
        Set<List<ApplicationId>> upgradeOrders = new HashSet<>(versions.size());
        for (var version : versions) {
            // Upgrade system
            tester.controllerTester().upgradeSystem(version);
            List<ApplicationId> upgraderOrder = new ArrayList<>(applications.size());

            // Upgrade all applications
            for (int i = 0; i < applications.size(); i++) {
                upgrader.maintain();
                tester.triggerJobs();
                Set<ApplicationId> triggered = tester.jobs().active().stream()
                        .map(Run::id)
                        .map(RunId::application)
                        .collect(Collectors.toSet());
                assertEquals(1, triggered.size(), "Expected number of applications is triggered");
                ApplicationId application = triggered.iterator().next();
                upgraderOrder.add(application);
                applications.get(application).completeRollout();
                tester.clock().advance(Duration.ofMinutes(1));
            }
            upgradeOrders.add(upgraderOrder);
        }
        assertEquals(versions.size(), upgradeOrders.size(), "Upgrade orders are distinct");
    }

    private static final ApplicationPackage canaryApplicationPackage =
            new ApplicationPackageBuilder().upgradePolicy("canary")
                                           .region("us-west-1")
                                           .region("us-east-3")
                                           .build();

    private static final ApplicationPackage defaultApplicationPackage =
            new ApplicationPackageBuilder().upgradePolicy("default")
                                           .region("us-west-1")
                                           .region("us-east-3")
                                           .build();

    private static final ApplicationPackage conservativeApplicationPackage =
            new ApplicationPackageBuilder().upgradePolicy("conservative")
                                           .region("us-west-1")
                                           .region("us-east-3")
                                           .build();

    /** Returns empty prebuilt applications for efficiency */
    private ApplicationPackage applicationPackage(String upgradePolicy) {
        switch (upgradePolicy) {
            case "canary" : return canaryApplicationPackage;
            case "default" : return defaultApplicationPackage;
            case "conservative" : return conservativeApplicationPackage;
            default : throw new IllegalArgumentException("No upgrade policy '" + upgradePolicy + "'");
        }
    }

    private DeploymentContext createAndDeploy(String applicationName, String upgradePolicy) {
        return tester.newDeploymentContext("tenant1", applicationName, "default")
                     .submit(applicationPackage(upgradePolicy))
                     .deploy();
    }

}
