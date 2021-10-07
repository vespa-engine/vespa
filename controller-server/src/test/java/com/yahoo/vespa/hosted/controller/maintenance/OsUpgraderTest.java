// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.versions.NodeVersion;
import org.junit.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class OsUpgraderTest {

    private final ControllerTester tester = new ControllerTester();
    private final OsVersionStatusUpdater statusUpdater = new OsVersionStatusUpdater(tester.controller(), Duration.ofDays(1));

    @Test
    public void upgrade_os() {
        CloudName cloud1 = CloudName.from("c1");
        CloudName cloud2 = CloudName.from("c2");
        ZoneApi zone0 = zone("prod.us-north-42", "prod.controller", cloud1);
        ZoneApi zone1 = zone("prod.eu-west-1", cloud1);
        ZoneApi zone2 = zone("prod.us-west-1", cloud1);
        ZoneApi zone3 = zone("prod.us-central-1", cloud1);
        ZoneApi zone4 = zone("prod.us-east-3", cloud1);
        ZoneApi zone5 = zone("prod.us-north-1", cloud2);
        UpgradePolicy upgradePolicy = UpgradePolicy.create()
                                                   .upgrade(zone0)
                                                   .upgrade(zone1)
                                                   .upgradeInParallel(zone2, zone3)
                                                   .upgrade(zone5) // Belongs to a different cloud and is ignored by this upgrader
                                                   .upgrade(zone4);
        OsUpgrader osUpgrader = osUpgrader(upgradePolicy, cloud1, false);

        // Bootstrap system
        List<ZoneId> nonControllerZones = List.of(zone1, zone2, zone3, zone4, zone5).stream()
                                              .map(ZoneApi::getVirtualId)
                                              .collect(Collectors.toList());
        tester.configServer().bootstrap(nonControllerZones, List.of(SystemApplication.tenantHost));
        tester.configServer().addNodes(List.of(zone0.getVirtualId()), List.of(SystemApplication.controllerHost));

        // Add system application that exists in a real system, but isn't eligible for OS upgrades
        tester.configServer().addNodes(nonControllerZones, List.of(SystemApplication.configServer));

        // Fail a few nodes. Failed nodes should not affect versions
        failNodeIn(zone1, SystemApplication.tenantHost);
        failNodeIn(zone3, SystemApplication.tenantHost);

        // New OS version released
        Version version1 = Version.fromString("7.1");
        tester.controller().upgradeOsIn(cloud1, Version.fromString("7.0"), Duration.ZERO, false);
        tester.controller().upgradeOsIn(cloud1, version1, Duration.ZERO, false);
        assertEquals(1, tester.controller().osVersionTargets().size()); // Only allows one version per cloud
        statusUpdater.maintain();

        // zone 0: controllers upgrade first
        osUpgrader.maintain();
        assertWanted(version1, SystemApplication.controllerHost, zone0);
        completeUpgrade(version1, SystemApplication.controllerHost, zone0);
        statusUpdater.maintain();
        assertEquals(3, nodesOn(version1).size());

        // zone 1: begins upgrading
        assertWanted(Version.emptyVersion, SystemApplication.tenantHost, zone1);
        osUpgrader.maintain();
        assertWanted(version1, SystemApplication.tenantHost, zone1);

        // Other zones remain on previous version (none)
        assertWanted(Version.emptyVersion, SystemApplication.proxy, zone2, zone3, zone4);

        // zone 1: completes upgrade
        completeUpgrade(version1, SystemApplication.tenantHost, zone1);
        statusUpdater.maintain();
        assertEquals(5, nodesOn(version1).size());
        assertEquals(11, nodesOn(Version.emptyVersion).size());

        // zone 2 and 3: begins upgrading
        osUpgrader.maintain();
        assertWanted(version1, SystemApplication.tenantHost, zone2, zone3);

        // zone 4: still on previous version
        assertWanted(Version.emptyVersion, SystemApplication.tenantHost, zone4);

        // zone 2 and 3: completes upgrade
        completeUpgrade(version1, SystemApplication.tenantHost, zone2, zone3);

        // zone 4: begins upgrading
        osUpgrader.maintain();
        assertWanted(version1, SystemApplication.tenantHost, zone4);

        // zone 4: completes upgrade
        completeUpgrade(version1, SystemApplication.tenantHost, zone4);

        // Next run does nothing as all zones are upgraded
        osUpgrader.maintain();
        assertWanted(version1, SystemApplication.tenantHost, zone1, zone2, zone3, zone4);
        statusUpdater.maintain();
        assertTrue("All nodes on target version", tester.controller().osVersionStatus().nodesIn(cloud1).stream()
                                                        .allMatch(node -> node.currentVersion().equals(version1)));
    }

    @Test
    public void upgrade_os_with_budget() {
        CloudName cloud = CloudName.from("cloud");
        ZoneApi zone0 = zone("prod.us-north-42", "prod.controller", cloud);
        ZoneApi zone1 = zone("dev.us-east-1", cloud);
        ZoneApi zone2 = zone("prod.us-west-1", cloud);
        ZoneApi zone3 = zone("prod.us-central-1", cloud);
        ZoneApi zone4 = zone("prod.eu-west-1", cloud);
        UpgradePolicy upgradePolicy = UpgradePolicy.create()
                                                   .upgrade(zone0)
                                                   .upgrade(zone1)
                                                   .upgradeInParallel(zone2, zone3)
                                                   .upgrade(zone4);
        OsUpgrader osUpgrader = osUpgrader(upgradePolicy, cloud, true);

        // Bootstrap system
        List<SystemApplication> nodeTypes = List.of(SystemApplication.configServerHost, SystemApplication.tenantHost);
        tester.configServer().bootstrap(List.of(zone1.getId(), zone2.getId(), zone3.getId(), zone4.getId()),
                                        nodeTypes);
        tester.configServer().addNodes(List.of(zone0.getVirtualId()), List.of(SystemApplication.controllerHost));

        // Upgrade with budget
        Version version = Version.fromString("7.1");
        tester.controller().upgradeOsIn(cloud, version, Duration.ofHours(12), false);
        assertEquals(Duration.ofHours(12), tester.controller().osVersionTarget(cloud).get().upgradeBudget());
        statusUpdater.maintain();
        osUpgrader.maintain();

        // Controllers upgrade first
        osUpgrader.maintain();
        assertWanted(version, SystemApplication.controllerHost, zone0);
        assertEquals("Controller zone gets a zero budget", Duration.ZERO, upgradeBudget(zone0, SystemApplication.controllerHost, version));
        completeUpgrade(version, SystemApplication.controllerHost, zone0);
        statusUpdater.maintain();
        assertEquals(3, nodesOn(version).size());

        // First zone upgrades
        osUpgrader.maintain();
        for (var nodeType : nodeTypes) {
            assertEquals("Dev zone gets a zero budget", Duration.ZERO, upgradeBudget(zone1, nodeType, version));
            completeUpgrade(version, nodeType, zone1);
        }

        // Next set of zones upgrade
        osUpgrader.maintain();
        for (var zone : List.of(zone2, zone3)) {
            for (var nodeType : nodeTypes) {
                assertEquals("Parallel prod zones share the budget of a single zone", Duration.ofHours(6),
                             upgradeBudget(zone, nodeType, version));
                completeUpgrade(version, nodeType, zone);
            }
        }

        // Last zone upgrades
        osUpgrader.maintain();
        for (var nodeType : nodeTypes) {
            assertEquals(nodeType + " in last prod zone gets the budget of a single zone", Duration.ofHours(6),
                         upgradeBudget(zone4, nodeType, version));
            completeUpgrade(version, nodeType, zone4);
        }

        // All host applications upgraded
        statusUpdater.maintain();
        assertTrue("All nodes on target version", tester.controller().osVersionStatus().nodesIn(cloud).stream()
                                                        .allMatch(node -> node.currentVersion().equals(version)));
    }

    @Test
    public void upgrade_os_nodes_choose_newer_version() {
        CloudName cloud = CloudName.from("cloud");
        ZoneApi zone1 = zone("dev.us-east-1", cloud);
        ZoneApi zone2 = zone("prod.us-west-1", cloud);
        UpgradePolicy upgradePolicy = UpgradePolicy.create()
                                                   .upgrade(zone1)
                                                   .upgrade(zone2);
        OsUpgrader osUpgrader = osUpgrader(upgradePolicy, cloud, false);

        // Bootstrap system
        tester.configServer().bootstrap(List.of(zone1.getId(), zone2.getId()),
                                        List.of(SystemApplication.tenantHost));

        // New OS version released
        Version version = Version.fromString("7.1");
        tester.controller().upgradeOsIn(cloud, Version.fromString("7.0"), Duration.ZERO, false);
        tester.controller().upgradeOsIn(cloud, version, Duration.ZERO, false); // Replaces existing target
        statusUpdater.maintain();

        // zone 1 upgrades
        osUpgrader.maintain();
        assertWanted(version, SystemApplication.tenantHost, zone1);
        Version chosenVersion = Version.fromString("7.1.1"); // Upgrade mechanism chooses a slightly newer version
        completeUpgrade(version, chosenVersion, SystemApplication.tenantHost, zone1);
        statusUpdater.maintain();
        assertEquals(3, nodesOn(chosenVersion).size());

        // zone 2 upgrades
        osUpgrader.maintain();
        assertWanted(version, SystemApplication.tenantHost, zone2);
        completeUpgrade(version, chosenVersion, SystemApplication.tenantHost, zone2);
        statusUpdater.maintain();
        assertEquals(6, nodesOn(chosenVersion).size());

        // No more upgrades
        osUpgrader.maintain();
        assertWanted(version, SystemApplication.tenantHost, zone1, zone2);
        assertTrue("All nodes on target version or newer", tester.controller().osVersionStatus().nodesIn(cloud).stream()
                                                                 .noneMatch(node -> node.currentVersion().isBefore(version)));
    }

    private Duration upgradeBudget(ZoneApi zone, SystemApplication application, Version version) {
        var upgradeBudget = tester.configServer().nodeRepository().osUpgradeBudget(zone.getVirtualId(), application.nodeType(), version);
        assertTrue("Expected budget for upgrade to " + version + " of " + application.id() + " in " + zone.getVirtualId(),
                   upgradeBudget.isPresent());
        return upgradeBudget.get();
    }

    private List<NodeVersion> nodesOn(Version version) {
        return tester.controller().osVersionStatus().versions().entrySet().stream()
                     .filter(entry -> entry.getKey().version().equals(version))
                     .flatMap(entry -> entry.getValue().stream())
                     .collect(Collectors.toList());
    }

    private void assertCurrent(Version version, SystemApplication application, ZoneApi... zones) {
        assertVersion(application, version, Node::currentOsVersion, zones);
    }

    private void assertWanted(Version version, SystemApplication application, ZoneApi... zones) {
        for (var zone : zones) {
            assertEquals("Target version set for " + application + " in " + zone.getVirtualId(), version,
                         nodeRepository().targetVersionsOf(zone.getVirtualId()).osVersion(application.nodeType())
                                         .orElse(Version.emptyVersion));
        }
    }

    private void assertVersion(SystemApplication application, Version version, Function<Node, Version> versionField,
                               ZoneApi... zones) {
        for (ZoneApi zone : zones) {
            for (Node node : nodesRequiredToUpgrade(zone, application)) {
                assertEquals(application + " version in " + zone, version, versionField.apply(node));
            }
        }
    }

    private List<Node> nodesRequiredToUpgrade(ZoneApi zone, SystemApplication application) {
        return nodeRepository().list(zone.getVirtualId(), NodeFilter.all().applications(application.id()))
                               .stream()
                               .filter(OsUpgrader::canUpgrade)
                               .collect(Collectors.toList());
    }

    private void failNodeIn(ZoneApi zone, SystemApplication application) {
        List<Node> nodes = nodeRepository().list(zone.getVirtualId(), NodeFilter.all().applications(application.id()));
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("No nodes allocated to " + application.id());
        }
        Node node = nodes.get(0);
        nodeRepository().putNodes(zone.getVirtualId(), Node.builder(node).state(Node.State.failed).build());
    }

    /** Simulate OS upgrade of nodes allocated to application. In a real system this is done by the node itself */
    private void completeUpgrade(Version version, SystemApplication application, ZoneApi... zones) {
        completeUpgrade(version, version, application, zones);
    }

    private void completeUpgrade(Version wantedVersion, Version version, SystemApplication application, ZoneApi... zones) {
        assertWanted(wantedVersion, application, zones);
        for (ZoneApi zone : zones) {
            for (Node node : nodesRequiredToUpgrade(zone, application)) {
                nodeRepository().putNodes(zone.getVirtualId(), Node.builder(node).wantedOsVersion(version)
                                                                   .currentOsVersion(version)
                                                                   .build());
            }
            assertCurrent(version, application, zone);
        }
    }

    private NodeRepositoryMock nodeRepository() {
        return tester.configServer().nodeRepository();
    }

    private OsUpgrader osUpgrader(UpgradePolicy upgradePolicy, CloudName cloud, boolean reprovisionToUpgradeOs) {
        var zones = upgradePolicy.asList().stream().flatMap(Collection::stream).collect(Collectors.toList());
        tester.zoneRegistry()
              .setZones(zones)
              .setOsUpgradePolicy(cloud, upgradePolicy);
        if (reprovisionToUpgradeOs) {
            tester.zoneRegistry().reprovisionToUpgradeOsIn(zones);
        }
        return new OsUpgrader(tester.controller(), Duration.ofDays(1), cloud);
    }

    private static ZoneApi zone(String id, CloudName cloud) {
        return ZoneApiMock.newBuilder().withId(id).with(cloud).build();
    }

    private static ZoneApi zone(String id, String virtualId, CloudName cloud) {
        return ZoneApiMock.newBuilder().withId(id).withVirtualId(virtualId).with(cloud).build();
    }

}
