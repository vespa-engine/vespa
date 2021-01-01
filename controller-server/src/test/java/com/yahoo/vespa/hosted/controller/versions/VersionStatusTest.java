// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
                Node upgradedNode = new Node.Builder(node).currentVersion(version1).build();
                tester.configServer().nodeRepository().putNodes(zone.getId(), upgradedNode);
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
        assertEquals(version0, tester.controller().readSystemVersion());

        // Downgrade one config server in each zone
        Version ancientVersion = Version.fromString("5.1");
        for (ZoneApi zone : tester.controller().zoneRegistry().zones().all().zones()) {
            for (Node node : tester.configServer().nodeRepository().list(zone.getId(), SystemApplication.configServer.id())) {
                Node downgradedNode = new Node.Builder(node).currentVersion(ancientVersion).build();
                tester.configServer().nodeRepository().putNodes(zone.getId(), downgradedNode);
                break;
            }
        }

        tester.computeVersionStatus();
        assertEquals(version0, tester.controller().readSystemVersion());
    }

    @Test
    public void testVersionStatusAfterApplicationUpdates() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
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
        context1.timeOutConvergence(systemTest);
        // - app2 is partially in production on version1 and partially on version2
        context2.runJob(systemTest)
                .runJob(stagingTest)
                .runJob(productionUsWest1)
                .failDeployment(productionUsEast3);
        // - app3 is in production on version1, but then fails in staging test on version2
        context3.timeOutUpgrade(stagingTest);

        tester.triggerJobs();
        tester.controllerTester().computeVersionStatus();
        List<VespaVersion> versions = tester.controller().readVersionStatus().versions();
        assertEquals("The two versions above exist", 2, versions.size());

        VespaVersion v1 = versions.get(0);
        assertEquals(version1, v1.versionNumber());
        var statistics = DeploymentStatistics.compute(List.of(version1, version2), tester.deploymentStatuses());
        var statistics1 = statistics.get(0);
        assertJobsRun("No runs are failing on version1.",
                      Map.of(context1.instanceId(), List.of(),
                             context2.instanceId(), List.of(),
                             context3.instanceId(), List.of()),
                      statistics1.failingUpgrades());
        assertJobsRun("All applications have at least one active production deployment on version 1.",
                     Map.of(context1.instanceId(), List.of(productionUsWest1, productionUsEast3),
                            context2.instanceId(), List.of(productionUsEast3),
                            context3.instanceId(), List.of(productionUsWest1, productionUsEast3)),
                     statistics1.productionSuccesses());
        assertEquals("No applications have active deployment jobs on version1.",
                     List.of(),
                     statistics1.runningUpgrade());

        VespaVersion v2 = versions.get(1);
        assertEquals(version2, v2.versionNumber());
        var statistics2 = statistics.get(1);
        assertJobsRun("All applications have failed on version2 in at least one zone.",
                     Map.of(context1.instanceId(), List.of(systemTest),
                            context2.instanceId(), List.of(productionUsEast3),
                            context3.instanceId(), List.of(stagingTest)),
                     statistics2.failingUpgrades());
        assertJobsRun("Only app2 has successfully deployed to production on version2.",
                      Map.of(context1.instanceId(), List.of(),
                             context2.instanceId(), List.of(productionUsWest1),
                             context3.instanceId(), List.of()),
                     statistics2.productionSuccesses());
        assertJobsRun("All applications are being retried on version2.",
                      Map.of(context1.instanceId(), List.of(systemTest, stagingTest),
                             context2.instanceId(), List.of(productionUsEast3),
                             context3.instanceId(), List.of(systemTest, stagingTest)),
                     statistics2.runningUpgrade());
    }

    private static void assertJobsRun(String assertion, Map<ApplicationId, List<JobType>> jobs, List<Run> runs) {
        assertEquals(assertion,
                     jobs.entrySet().stream()
                         .flatMap(entry -> entry.getValue().stream().map(type -> new JobId(entry.getKey(), type)))
                         .collect(toSet()),
                     runs.stream()
                         .map(run -> run.id().job())
                         .collect(toSet()));
    }

    @Test
    public void testVersionConfidence() {
        DeploymentTester tester = new DeploymentTester().atMondayMorning();
        Version version0 = new Version("6.2");
        tester.controllerTester().upgradeSystem(version0);
        tester.upgrader().maintain();
        var builder = new ApplicationPackageBuilder().region("us-west-1").region("us-east-3");

        // Setup applications - all running on version0
        ApplicationPackage canaryPolicy = builder.upgradePolicy("canary").build();
        var canary0 = tester.newDeploymentContext("tenant1", "canary0", "default")
                            .submit(canaryPolicy)
                            .deploy();
        var canary1 = tester.newDeploymentContext("tenant1", "canary1", "default")
                            .submit(canaryPolicy)
                            .deploy();
        var canary2 = tester.newDeploymentContext("tenant1", "canary2", "default")
                            .submit(canaryPolicy)
                            .deploy();

        ApplicationPackage defaultPolicy = builder.upgradePolicy("default").build();
        var default0 = tester.newDeploymentContext("tenant1", "default0", "default")
                             .submit(defaultPolicy)
                             .deploy();
        var default1 = tester.newDeploymentContext("tenant1", "default1", "default")
                             .submit(defaultPolicy)
                             .deploy();
        var default2 = tester.newDeploymentContext("tenant1", "default2", "default")
                             .submit(defaultPolicy)
                             .deploy();
        var default3 = tester.newDeploymentContext("tenant1", "default3", "default")
                             .submit(defaultPolicy)
                             .deploy();
        var default4 = tester.newDeploymentContext("tenant1", "default4", "default")
                             .submit(defaultPolicy)
                             .deploy();
        var default5 = tester.newDeploymentContext("tenant1", "default5", "default")
                             .submit(defaultPolicy)
                             .deploy();
        var default6 = tester.newDeploymentContext("tenant1", "default6", "default")
                             .submit(defaultPolicy)
                             .deploy();
        var default7 = tester.newDeploymentContext("tenant1", "default7", "default")
                             .submit(defaultPolicy)
                             .deploy();
        var default8 = tester.newDeploymentContext("tenant1", "default8", "default")
                             .submit(defaultPolicy)
                             .deploy();
        var default9 = tester.newDeploymentContext("tenant1", "default9", "default")
                            .submit(defaultPolicy)
                            .deploy();

        ApplicationPackage conservativePolicy = builder.upgradePolicy("conservative").build();
        var conservative0 = tester.newDeploymentContext("tenant1", "conservative0", "default")
                .submit(conservativePolicy)
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
                   tester.controller().readVersionStatus().versions().stream()
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
        List<VespaVersion> versions = tester.controller().readVersionStatus().versions();
        assertEquals(List.of("6.2", "6.4", "6.5"), versions.stream().map(version -> version.versionNumber().toString()).collect(Collectors.toList()));

        // Check release status is correct (static data in MockMavenRepository).
        assertTrue(versions.get(0).isReleased());
        assertFalse(versions.get(1).isReleased());
        assertFalse(versions.get(2).isReleased());
    }

    @Test
    public void testConfidenceWithLingeringVersions() {
        DeploymentTester tester = new DeploymentTester().atMondayMorning();
        Version version0 = new Version("6.2");
        tester.controllerTester().upgradeSystem(version0);
        tester.upgrader().maintain();
        var appPackage = new ApplicationPackageBuilder().region("us-west-1").region("us-east-3").upgradePolicy("canary");

        var canary0 = tester.newDeploymentContext("tenant1", "canary0", "default")
                            .submit(appPackage.build())
                            .deploy();

        assertEquals("All applications running on this version: High",
                     Confidence.high, confidence(tester.controller(), version0));

        // New version is released
        Version version1 = new Version("6.3");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        tester.triggerJobs();

        // App upgrades to the new version and fails
        canary0.failDeployment(systemTest);
        canary0.abortJob(stagingTest);
        tester.controllerTester().computeVersionStatus();
        assertEquals("One canary failed: Broken",
                     Confidence.broken, confidence(tester.controller(), version1));

        // New version is released
        Version version2 = new Version("6.4");
        tester.controllerTester().upgradeSystem(version2);
        tester.upgrader().maintain();
        assertEquals("Confidence remains unchanged for version1 until app overrides old tests: Broken",
                     Confidence.broken, confidence(tester.controller(), version1));
        assertEquals("Confidence defaults to low for version with no applications",
                     Confidence.low, confidence(tester.controller(), version2));
        assertEquals(version2, canary0.instance().change().platform().orElseThrow());

        canary0.failDeployment(systemTest);
        canary0.abortJob(stagingTest);
        tester.controllerTester().computeVersionStatus();
        assertFalse("Previous version should be forgotten, as canary only had test jobs run on it",
                    tester.controller().readVersionStatus().versions().stream().anyMatch(version -> version.versionNumber().equals(version1)));

        // App succeeds with tests, but fails production deployment
        canary0.runJob(systemTest)
               .runJob(stagingTest)
               .failDeployment(productionUsWest1);

        assertEquals("One canary failed: Broken",
                     Confidence.broken, confidence(tester.controller(), version2));

        // A new version is released, and the app again fails production deployment.
        Version version3 = new Version("6.5");
        tester.controllerTester().upgradeSystem(version3);
        tester.upgrader().maintain();
        assertEquals("Confidence remains unchanged for version2: Broken",
                     Confidence.broken, confidence(tester.controller(), version2));
        assertEquals("Confidence defaults to low for version with no applications",
                     Confidence.low, confidence(tester.controller(), version3));
        assertEquals(version3, canary0.instance().change().platform().orElseThrow());

        canary0.runJob(systemTest)
               .runJob(stagingTest)
               .failDeployment(productionUsWest1);
        tester.controllerTester().computeVersionStatus();
        assertEquals("Confidence remains unchanged for version2: Broken",
                     Confidence.broken, confidence(tester.controller(), version2));
        assertEquals("Canary broken, so confidence for version3: Broken",
                     Confidence.broken, confidence(tester.controller(), version3));

        // App succeeds production deployment, clearing failure on version2
        canary0.runJob(productionUsWest1);
        tester.controllerTester().computeVersionStatus();
        assertFalse("Previous version should be forgotten, as canary only had test jobs run on it",
                    tester.controller().readVersionStatus().versions().stream().anyMatch(version -> version.versionNumber().equals(version2)));
        assertEquals("Canary OK, but not done upgrading, so confidence for version3: Low",
                     Confidence.low, confidence(tester.controller(), version3));
    }

    @Test
    public void testConfidenceOverride() {
        DeploymentTester tester = new DeploymentTester();
        Version version0 = new Version("6.2");
        tester.controllerTester().upgradeSystem(version0);

        // Create and deploy application on current version
        var app = tester.newDeploymentContext("tenant1", "app1", "default")
                        .submit()
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
                                                    .containsKey(version0));
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
        assertEquals(version0, tester.controller().readVersionStatus().systemVersion().get().versionNumber());
        assertEquals(commitSha0, tester.controller().readVersionStatus().systemVersion().get().releaseCommit());
        assertEquals(commitDate0, tester.controller().readVersionStatus().systemVersion().get().committedAt());

        // Deploy app on version0 to keep computing statistics for that version
        tester.newDeploymentContext().submit().deploy();

        // Commit details are updated for new version
        var version1 = tester.controllerTester().nextVersion();
        var commitSha1 = "deadbeef";
        var commitDate1 = Instant.ofEpochMilli(123);
        tester.controllerTester().upgradeController(version1, commitSha1, commitDate1);
        tester.controllerTester().upgradeSystemApplications(version1);
        assertEquals(version1, tester.controller().readVersionStatus().systemVersion().get().versionNumber());
        assertEquals(commitSha1, tester.controller().readVersionStatus().systemVersion().get().releaseCommit());
        assertEquals(commitDate1, tester.controller().readVersionStatus().systemVersion().get().committedAt());

        // Commit details for previous version are preserved
        assertEquals(commitSha0, tester.controller().readVersionStatus().version(version0).releaseCommit());
        assertEquals(commitDate0, tester.controller().readVersionStatus().version(version0).committedAt());
    }

    @Test
    public void testConfidenceChangeRespectsTimeWindow() {
        DeploymentTester tester = new DeploymentTester().atMondayMorning();
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
        assertSame(Confidence.high, tester.controller().readVersionStatus().version(version0).confidence());

        // System and canary0 is upgraded within allowed time window
        Version version1 = Version.fromString("7.2");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        canary0.deployPlatform(version1);
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.low, tester.controller().readVersionStatus().version(version1).confidence());

        // canary1 breaks just outside allowed upgrade window
        assertEquals(12, tester.controllerTester().hourOfDayAfter(Duration.ofHours(7)));
        canary1.failDeployment(systemTest);
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.broken, tester.controller().readVersionStatus().version(version1).confidence());

        // Second canary is fixed later in the day. All canaries are now fixed, but confidence is not raised as we're
        // outside the allowed time window
        assertEquals(20, tester.controllerTester().hourOfDayAfter(Duration.ofHours(8)));
        canary1.deployPlatform(version1);
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.broken, tester.controller().readVersionStatus().version(version1).confidence());

        // Early morning arrives, confidence is raised and normal application upgrades
        assertEquals(5, tester.controllerTester().hourOfDayAfter(Duration.ofHours(9)));
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.normal, tester.controller().readVersionStatus().version(version1).confidence());
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
        assertSame(Confidence.low, tester.controller().readVersionStatus().version(version2).confidence());

        // Confidence override takes precedence over time window constraints
        tester.upgrader().overrideConfidence(version2, Confidence.normal);
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.normal, tester.controller().readVersionStatus().version(version2).confidence());
        tester.upgrader().overrideConfidence(version2, Confidence.low);
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.low, tester.controller().readVersionStatus().version(version2).confidence());
        tester.upgrader().removeConfidenceOverride(version2);

        // Next morning arrives, confidence is raised and normal application upgrades
        assertEquals(7, tester.controllerTester().hourOfDayAfter(Duration.ofHours(17)));
        tester.controllerTester().computeVersionStatus();
        assertSame(Confidence.normal, tester.controller().readVersionStatus().version(version2).confidence());
        tester.upgrader().maintain();
        tester.triggerJobs();
        default0.deployPlatform(version2);
    }

    @Test
    public void testStatusIncludesIncompleteUpgrades() {
        var tester = new DeploymentTester().atMondayMorning();
        var version0 = Version.fromString("7.1");
        var applicationPackage = new ApplicationPackageBuilder().region("us-west-1").build();

        // Application deploys on initial version
        tester.controllerTester().upgradeSystem(version0);
        var context = tester.newDeploymentContext("tenant1", "default0", "default");
        context.submit(applicationPackage).deploy();

        // System is upgraded and application starts upgrading to next version
        var version1 = Version.fromString("7.2");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();

        // Upgrade of prod zone fails
        context.runJob(systemTest)
               .runJob(stagingTest)
               .failDeployment(productionUsWest1);
        tester.controllerTester().computeVersionStatus();
        for (var version : List.of(version0, version1)) {
            assertOnVersion(version, context.instanceId(), tester);
        }

        // System is upgraded and application starts upgrading to next version
        var version2 = Version.fromString("7.3");
        tester.controllerTester().upgradeSystem(version2);
        tester.upgrader().maintain();

        // Upgrade of prod zone fails again, application is now potentially on 3 different versions:
        // 1 completed upgrade + 2 failed
        context.runJob(systemTest)
               .runJob(stagingTest)
               .failDeployment(productionUsWest1);
        tester.controllerTester().computeVersionStatus();
        for (var version : List.of(version0, version1, version2)) {
            assertOnVersion(version, context.instanceId(), tester);
        }

        // Upgrade succeeds
        context.deployPlatform(version2);
        tester.controllerTester().computeVersionStatus();
        assertEquals(1, tester.controller().readVersionStatus().versions().size());
        assertOnVersion(version2, context.instanceId(), tester);

        // System is upgraded and application starts upgrading to next version
        var version3 = Version.fromString("7.4");
        tester.controllerTester().upgradeSystem(version3);
        tester.upgrader().maintain();

        // Upgrade of prod zone fails again. Upgrades that failed before the most recent success are not counted
        context.runJob(systemTest)
               .runJob(stagingTest)
               .failDeployment(productionUsWest1);
        tester.controllerTester().computeVersionStatus();
        assertEquals(2, tester.controller().readVersionStatus().versions().size());
        for (var version : List.of(version2, version3)) {
            assertOnVersion(version, context.instanceId(), tester);
        }
    }

    private void assertOnVersion(Version version, ApplicationId instance, DeploymentTester tester) {
        var vespaVersion = tester.controller().readVersionStatus().version(version);
        assertNotNull("Statistics for version " + version + " exist", vespaVersion);
        var statistics = DeploymentStatistics.compute(List.of(version), tester.deploymentStatuses()).get(0);
        assertTrue("Application is on version " + version,
                   Stream.of(statistics.productionSuccesses(), statistics.failingUpgrades(), statistics.runningUpgrade())
                         .anyMatch(runs -> runs.stream().anyMatch(run -> run.id().application().equals(instance))));
    }

    private static void writeControllerVersion(HostName hostname, Version version, CuratorDb db) {
        db.writeControllerVersion(hostname, new ControllerVersion(version, "badc0ffee", Instant.EPOCH));
    }

    private Confidence confidence(Controller controller, Version version) {
        return controller.readVersionStatus().versions().stream()
                         .filter(v -> v.versionNumber().equals(version))
                         .findFirst()
                         .map(VespaVersion::confidence)
                         .orElseThrow(() -> new IllegalArgumentException("Expected to find version: " + version));
    }

}
