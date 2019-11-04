// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Test computing of version status
 * 
 * @author bratseth
 */
public class VersionStatusTest {
    
    @Test
    public void testEmptyVersionStatus() {
        VersionStatus status = VersionStatus.empty();
        assertFalse(status.systemVersion().isPresent());
        assertTrue(status.versions().isEmpty());
    }

    @Test
    public void testSystemVersionIsControllerVersionIfConfigServersAreNewer() {
        ControllerTester tester = new ControllerTester();
        Version largerThanCurrent = new Version(Vtag.currentVersion.getMajor() + 1);
        tester.upgradeSystemApplications(largerThanCurrent);
        VersionStatus versionStatus = VersionStatus.compute(tester.controller());
        assertEquals(Vtag.currentVersion, versionStatus.systemVersion().get().versionNumber());
    }

    @Test
    public void testSystemVersionIsVersionOfOldestConfigServer() {
        ControllerTester tester = new ControllerTester();
        Version version0 = Version.fromString("6.1");
        Version version1 = Version.fromString("6.5");
        // Upgrade some config servers
        for (ZoneApi zone : tester.zoneRegistry().zones().all().zones()) {
            for (Node node : tester.configServer().nodeRepository().list(zone.getId(), SystemApplication.configServer.id())) {
                Node upgradedNode = new Node(node.hostname(), node.state(), node.type(), node.owner(), version1, node.wantedVersion());
                tester.configServer().nodeRepository().putByHostname(zone.getId(), upgradedNode);
                break;
            }
        }
        VersionStatus versionStatus = VersionStatus.compute(tester.controller());
        assertEquals(version0, versionStatus.systemVersion().get().versionNumber());
    }

    @Test
    public void testControllerVersionIsVersionOfOldestController() {
        HostName controller1 = HostName.from("controller-1");
        HostName controller2 = HostName.from("controller-2");
        HostName controller3 = HostName.from("controller-3");
        MockCuratorDb db = new MockCuratorDb(Stream.of(controller1, controller2, controller3)
                                                   .map(hostName -> hostName.value() + ":2222")
                                                   .collect(Collectors.joining(",")));
        ControllerTester tester = new ControllerTester(db);

        writeControllerVersion(controller1, Version.fromString("6.2"), db);
        writeControllerVersion(controller2, Version.fromString("6.1"), db);
        writeControllerVersion(controller3, Version.fromString("6.2"), db);

        VersionStatus versionStatus = VersionStatus.compute(tester.controller());
        assertEquals("Controller version is oldest version", Version.fromString("6.1"),
                     versionStatus.controllerVersion().get().versionNumber());

        // Last controller upgrades
        writeControllerVersion(controller2, Version.fromString("6.2"), db);
        versionStatus = VersionStatus.compute(tester.controller());
        assertEquals(Version.fromString("6.2"), versionStatus.controllerVersion().get().versionNumber());
    }

    @Test
    public void testSystemVersionNeverShrinks() {
        ControllerTester tester = new ControllerTester();
        Version version0 = Version.fromString("6.2");
        tester.upgradeSystem(version0);
        assertEquals(version0, tester.controller().systemVersion());

        // Downgrade one config server in each zone
        Version ancientVersion = Version.fromString("5.1");
        for (ZoneApi zone : tester.controller().zoneRegistry().zones().all().zones()) {
            for (Node node : tester.configServer().nodeRepository().list(zone.getId(), SystemApplication.configServer.id())) {
                Node downgradedNode = new Node(node.hostname(), node.state(), node.type(), node.owner(), ancientVersion, node.wantedVersion());
                tester.configServer().nodeRepository().putByHostname(zone.getId(), downgradedNode);
                break;
            }
        }

        tester.computeVersionStatus();
        assertEquals(version0, tester.controller().systemVersion());
    }

    @Test
    public void testVersionStatusAfterApplicationUpdates() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .environment(Environment.prod)
                .region("us-west-1")
                .region("us-east-3")
                .build();

        Version version1 = new Version("6.2");
        Version version2 = new Version("6.3");
        tester.controllerTester().upgradeSystem(version1);

        // Setup applications
        var context1 = tester.newDeploymentContext("tenant1", "app1", "default").submit(applicationPackage).deploy();
        var context2 = tester.newDeploymentContext("tenant1", "app2", "default").submit(applicationPackage).deploy();
        var context3 = tester.newDeploymentContext("tenant1", "app3", "default").submit(applicationPackage).deploy();

        // version2 is released
        tester.controllerTester().upgradeSystem(version2);
        tester.upgrader().maintain();
        tester.triggerJobs();

        // - app1 is in production on version1, but then fails in system test on version2
        context1.submit(applicationPackage)
                .timeOutConvergence(systemTest);
        // - app2 is partially in production on version1 and partially on version2
        context2.submit(applicationPackage)
                .runJob(systemTest)
                .runJob(stagingTest)
                .runJob(productionUsWest1)
                .failDeployment(productionUsEast3);
        // - app3 is in production on version1, but then fails in staging test on version2
        context3.submit(applicationPackage)
                .timeOutUpgrade(stagingTest);

        tester.controllerTester().computeVersionStatus();
        List<VespaVersion> versions = tester.controller().versionStatus().versions();
        assertEquals("The two versions above exist", 2, versions.size());

        VespaVersion v1 = versions.get(0);
        assertEquals(version1, v1.versionNumber());
        assertEquals("No applications are failing on version1.", ImmutableSet.of(), v1.statistics().failing());
        assertEquals("All applications have at least one active production deployment on version 1.", ImmutableSet.of(context1.instanceId(), context2.instanceId(), context3.instanceId()), v1.statistics().production());
        assertEquals("No applications have active deployment jobs on version1.", ImmutableSet.of(), v1.statistics().deploying());

        VespaVersion v2 = versions.get(1);
        assertEquals(version2, v2.versionNumber());
        assertEquals("All applications have failed on version2 in at least one zone.", ImmutableSet.of(context1.instanceId(), context2.instanceId(), context3.instanceId()), v2.statistics().failing());
        assertEquals("Only app2 has successfully deployed to production on version2.", ImmutableSet.of(context2.instanceId()), v2.statistics().production());
        // Should test the below, but can't easily be done with current test framework. This test passes in DeploymentApiTest.
        // assertEquals("All applications are being retried on version2.", ImmutableSet.of(app1.id(), app2.id(), app3.id()), v2.statistics().deploying());
    }
    
    @Test
    public void testVersionConfidence() {
        DeploymentTester tester = new DeploymentTester().atHourOfDay(5);
        Version version0 = new Version("6.2");
        tester.controllerTester().upgradeSystem(version0);
        tester.upgrader().maintain();
        var builder = new ApplicationPackageBuilder().region("us-west-1").region("us-east-3");

        // Setup applications - all running on version0
        builder.upgradePolicy("canary");
        var canary0 = tester.newDeploymentContext("tenant1", "canary0", "default")
                            .submit(builder.build())
                            .deploy();
        var canary1 = tester.newDeploymentContext("tenant1", "canary1", "default")
                            .submit(builder.build())
                            .deploy();
        var canary2 = tester.newDeploymentContext("tenant1", "canary2", "default")
                            .submit(builder.build())
                            .deploy();

        builder.upgradePolicy("default");
        var default0 = tester.newDeploymentContext("tenant1", "default0", "default")
                             .submit(builder.build())
                             .deploy();
        var default1 = tester.newDeploymentContext("tenant1", "default1", "default")
                             .submit(builder.build())
                             .deploy();
        var default2 = tester.newDeploymentContext("tenant1", "default2", "default")
                             .submit(builder.build())
                             .deploy();
        var default3 = tester.newDeploymentContext("tenant1", "default3", "default")
                             .submit(builder.build())
                             .deploy();
        var default4 = tester.newDeploymentContext("tenant1", "default4", "default")
                             .submit(builder.build())
                             .deploy();
        var default5 = tester.newDeploymentContext("tenant1", "default5", "default")
                             .submit(builder.build())
                             .deploy();
        var default6 = tester.newDeploymentContext("tenant1", "default6", "default")
                             .submit(builder.build())
                             .deploy();
        var default7 = tester.newDeploymentContext("tenant1", "default7", "default")
                             .submit(builder.build())
                             .deploy();
        var default8 = tester.newDeploymentContext("tenant1", "default8", "default")
                             .submit(builder.build())
                             .deploy();
        var default9 = tester.newDeploymentContext("tenant1", "default9", "default")
                            .submit(builder.build())
                            .deploy();

        builder.upgradePolicy("conservative");
        var conservative0 = tester.newDeploymentContext("tenant1", "conservative0", "default")
                .submit(builder.build())
                .deploy();

        // Applications that do not affect confidence calculation:

        // Application without deployment
        var ignored0 = tester.newDeploymentContext("tenant1", "ignored0", "default");

        assertEquals("All applications running on this version: High",
                     Confidence.high, confidence(tester.controller(), version0));

        // New version is released
        Version version1 = new Version("6.3");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        tester.triggerJobs();

        // Canaries upgrade to new versions and fail
        canary0.deployPlatform(version1);
        canary1.runJob(systemTest)
               .runJob(stagingTest)
               .failDeployment(productionUsWest1);
        tester.controllerTester().computeVersionStatus();
        assertEquals("One canary failed: Broken",
                     Confidence.broken, confidence(tester.controller(), version1));

        // New version is released
        Version version2 = new Version("6.4");
        tester.controllerTester().upgradeSystem(version2);
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals("Confidence defaults to low for version with no applications",
                     Confidence.low, confidence(tester.controller(), version2));

        // All canaries upgrade successfully
        canary0.deployPlatform(version2);
        canary1.deployPlatform(version2);

        assertEquals("Confidence for remains unchanged for version1: Broken",
                     Confidence.broken, confidence(tester.controller(), version1));
        assertEquals("Nothing has failed but not all canaries have upgraded: Low",
                     Confidence.low, confidence(tester.controller(), version2));

        // Remaining canary upgrades to version2 which raises confidence to normal and more apps upgrade
        canary2.failDeployment(systemTest);
        canary2.runJob(stagingTest);
        canary2.deployPlatform(version2);
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        tester.triggerJobs();
        assertEquals("Canaries have upgraded: Normal",
                     Confidence.normal, confidence(tester.controller(), version2));
        default0.deployPlatform(version2);
        default1.deployPlatform(version2);
        default2.deployPlatform(version2);
        default3.deployPlatform(version2);
        default4.deployPlatform(version2);
        default5.deployPlatform(version2);
        default6.deployPlatform(version2);
        default7.deployPlatform(version2);
        tester.controllerTester().computeVersionStatus();

        // Remember confidence across restart
        tester.controllerTester().createNewController();

        assertEquals("Confidence remains unchanged for version0: High",
                     Confidence.high, confidence(tester.controller(), version0));
        assertEquals("All canaries deployed + < 90% of defaults: Normal",
                     Confidence.normal, confidence(tester.controller(), version2));
        assertTrue("Status for version without applications is removed",
                   tester.controller().versionStatus().versions().stream()
                           .noneMatch(vespaVersion -> vespaVersion.versionNumber().equals(version1)));

        // Another default application upgrades, raising confidence to high
        default8.deployPlatform(version2);
        default9.deployPlatform(version2);
        tester.controllerTester().computeVersionStatus();

        assertEquals("Confidence remains unchanged for version0: High",
                     Confidence.high, confidence(tester.controller(), version0));
        assertEquals("90% of defaults deployed successfully: High",
                     VespaVersion.Confidence.high, confidence(tester.controller(), version2));

        // A new version is released, all canaries upgrade successfully, but enough "default" apps fail to mark version
        // as broken
        Version version3 = new Version("6.5");
        tester.controllerTester().upgradeSystem(version3);
        tester.upgrader().maintain();
        tester.triggerJobs();
        canary0.deployPlatform(version3);
        canary1.deployPlatform(version3);
        canary2.deployPlatform(version3);
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        tester.triggerJobs();
        default0.failDeployment(stagingTest);
        default1.failDeployment(stagingTest);
        default2.failDeployment(stagingTest);
        default3.failDeployment(stagingTest);
        tester.controllerTester().computeVersionStatus();

        assertEquals("Confidence remains unchanged for version0: High",
                     Confidence.high, confidence(tester.controller(), version0));
        assertEquals("Confidence remains unchanged for version2: High",
                     Confidence.high, confidence(tester.controller(), version2));
        assertEquals("40% of defaults failed: Broken",
                     VespaVersion.Confidence.broken, confidence(tester.controller(), version3));

        // Test version order
        List<VespaVersion> versions = tester.controller().versionStatus().versions();
        assertEquals(3, versions.size());
        assertEquals("6.2", versions.get(0).versionNumber().toString());
        assertEquals("6.4", versions.get(1).versionNumber().toString());
        assertEquals("6.5", versions.get(2).versionNumber().toString());

        // Check release status is correct (static data in MockMavenRepository).
        assertTrue(versions.get(0).isReleased());
        assertFalse(versions.get(1).isReleased());
        assertFalse(versions.get(2).isReleased());
    }

    @Test
    public void testConfidenceOverride() {
        DeploymentTester tester = new DeploymentTester();
        Version version0 = new Version("6.2");
        tester.controllerTester().upgradeSystem(version0);

        // Create and deploy application on current version
        var app = tester.newDeploymentContext("tenant1", "app1", "default")
                        .submit(DeploymentContext.applicationPackage)
                        .deploy();
        tester.controllerTester().computeVersionStatus();
        assertEquals(Confidence.high, confidence(tester.controller(), version0));

        // Override confidence
        tester.upgrader().overrideConfidence(version0, Confidence.broken);
        tester.controllerTester().computeVersionStatus();
        assertEquals(Confidence.broken, confidence(tester.controller(), version0));

        // New version is released and application upgrades
        Version version1 = new Version("6.3");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        app.deployPlatform(version1);
        tester.controllerTester().computeVersionStatus();
        assertEquals(Confidence.high, confidence(tester.controller(), version1));

        // Stale override was removed
        assertFalse("Stale override removed", tester.controller().curator().readConfidenceOverrides()
                                                    .keySet().contains(version0));
    }

    @Test
    public void testCommitDetailsPreservation() {
        HostName controller1 = HostName.from("controller-1");
        HostName controller2 = HostName.from("controller-2");
        HostName controller3 = HostName.from("controller-3");
        MockCuratorDb db = new MockCuratorDb(Stream.of(controller1, controller2, controller3)
                                                   .map(hostName -> hostName.value() + ":2222")
                                                   .collect(Collectors.joining(",")));
        DeploymentTester tester = new DeploymentTester(new ControllerTester(db));

        // Commit details are set for initial version
        var version0 = tester.controllerTester().nextVersion();
        var commitSha0 = "badc0ffee";
        var commitDate0 = Instant.EPOCH;
        tester.controllerTester().upgradeSystem(version0);
        assertEquals(version0, tester.controller().versionStatus().systemVersion().get().versionNumber());
        assertEquals(commitSha0, tester.controller().versionStatus().systemVersion().get().releaseCommit());
        assertEquals(commitDate0, tester.controller().versionStatus().systemVersion().get().committedAt());

        // Deploy app on version0 to keep computing statistics for that version
        tester.newDeploymentContext().submit().deploy();

        // Commit details are updated for new version
        var version1 = tester.controllerTester().nextVersion();
        var commitSha1 = "deadbeef";
        var commitDate1 = Instant.ofEpochMilli(123);
        tester.controllerTester().upgradeController(version1, commitSha1, commitDate1);
        tester.controllerTester().upgradeSystemApplications(version1);
        assertEquals(version1, tester.controller().versionStatus().systemVersion().get().versionNumber());
        assertEquals(commitSha1, tester.controller().versionStatus().systemVersion().get().releaseCommit());
        assertEquals(commitDate1, tester.controller().versionStatus().systemVersion().get().committedAt());

        // Commit details for previous version are preserved
        assertEquals(commitSha0, tester.controller().versionStatus().version(version0).releaseCommit());
        assertEquals(commitDate0, tester.controller().versionStatus().version(version0).committedAt());
    }

    @Test
    public void testConfidenceChangeRespectsTimeWindow() {
        DeploymentTester tester = new DeploymentTester().atHourOfDay(5);
        // Canaries and normal application deploys on initial version
        Version version0 = Version.fromString("7.1");
        tester.controllerTester().upgradeSystem(version0);
        var canary0 = tester.newDeploymentContext("tenant1", "canary0", "default")
                            .submit(new ApplicationPackageBuilder().upgradePolicy("canary").region("us-west-1").build())
                            .deploy();
        var canary1 = tester.newDeploymentContext("tenant1", "canary1", "default")
                            .submit(new ApplicationPackageBuilder().upgradePolicy("canary").region("us-west-1").build())
                            .deploy();
        var default0 = tester.newDeploymentContext("tenant1", "default0", "default")
                            .submit(new ApplicationPackageBuilder().upgradePolicy("default").region("us-west-1").build())
                            .deploy();
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.high, tester.controller().versionStatus().version(version0).confidence());

        // System and canary0 is upgraded within allowed time window
        Version version1 = Version.fromString("7.2");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        canary0.deployPlatform(version1);
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.low, tester.controller().versionStatus().version(version1).confidence());

        // canary1 breaks just outside allowed upgrade window
        assertEquals(12, tester.controllerTester().hourOfDayAfter(Duration.ofHours(7)));
        canary1.failDeployment(systemTest);
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.broken, tester.controller().versionStatus().version(version1).confidence());

        // Second canary is fixed later in the day. All canaries are now fixed, but confidence is not raised as we're
        // outside the allowed time window
        assertEquals(20, tester.controllerTester().hourOfDayAfter(Duration.ofHours(8)));
        canary1.deployPlatform(version1);
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.broken, tester.controller().versionStatus().version(version1).confidence());

        // Early morning arrives, confidence is raised and normal application upgrades
        assertEquals(5, tester.controllerTester().hourOfDayAfter(Duration.ofHours(9)));
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.normal, tester.controller().versionStatus().version(version1).confidence());
        tester.upgrader().maintain();
        tester.triggerJobs();
        default0.deployPlatform(version1);

        // Another version is released. System and canaries upgrades late, confidence stays low
        Version version2 = Version.fromString("7.3");
        tester.controllerTester().upgradeSystem(version2);
        tester.upgrader().maintain();
        assertEquals(14, tester.controllerTester().hourOfDayAfter(Duration.ofHours(9)));
        canary0.deployPlatform(version2);
        canary1.deployPlatform(version2);
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.low, tester.controller().versionStatus().version(version2).confidence());

        // Confidence override takes precedence over time window constraints
        tester.upgrader().overrideConfidence(version2, Confidence.normal);
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.normal, tester.controller().versionStatus().version(version2).confidence());
        tester.upgrader().overrideConfidence(version2, Confidence.low);
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.low, tester.controller().versionStatus().version(version2).confidence());
        tester.upgrader().removeConfidenceOverride(version2);

        // Next morning arrives, confidence is raised and normal application upgrades
        assertEquals(7, tester.controllerTester().hourOfDayAfter(Duration.ofHours(17)));
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.normal, tester.controller().versionStatus().version(version2).confidence());
        tester.upgrader().maintain();
        tester.triggerJobs();
        default0.deployPlatform(version2);
    }

    private static void writeControllerVersion(HostName hostname, Version version, CuratorDb db) {
        db.writeControllerVersion(hostname, new ControllerVersion(version, "badc0ffee", Instant.EPOCH));
    }

    private Confidence confidence(Controller controller, Version version) {
        return controller.versionStatus().versions().stream()
                .filter(v -> v.statistics().version().equals(version))
                .findFirst()
                .map(VespaVersion::confidence)
                .orElseThrow(() -> new IllegalArgumentException("Expected to find version: " + version));
    }

}
