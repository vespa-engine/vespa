// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.zone.NodeSlice;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.UpgradePolicy.Step;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.versions.NodeVersion;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mpolden
 */
public class OsUpgraderTest {

    private final ControllerTester tester = new ControllerTester();
    private final OsVersionStatusUpdater statusUpdater = new OsVersionStatusUpdater(tester.controller(), Duration.ofDays(1));

    @Test
    void upgrade_os() {
        CloudName cloud1 = CloudName.from("c1");
        CloudName cloud2 = CloudName.from("c2");
        ZoneApi zone0 = zone("prod.us-north-42", "prod.controller", cloud1);
        ZoneApi zone1 = zone("prod.eu-west-1", cloud1);
        ZoneApi zone2 = zone("prod.us-west-1", cloud1);
        ZoneApi zone3 = zone("prod.us-central-1", cloud1);
        ZoneApi zone4 = zone("prod.us-east-3", cloud1);
        ZoneApi zone5 = zone("prod.us-north-1", cloud2);
        UpgradePolicy upgradePolicy = UpgradePolicy.builder()
                                                   .upgrade(zone0)
                                                   .upgrade(zone1)
                                                   .upgrade(Step.of(zone2, zone3).require(NodeSlice.minCount(1)))
                                                   .upgrade(zone5) // Belongs to a different cloud and is ignored by this upgrader
                                                   .upgrade(zone4)
                                                   .build();
        OsUpgrader osUpgrader = osUpgrader(upgradePolicy, cloud1, false);

        // Bootstrap system
        List<ZoneId> nonControllerZones = Stream.of(zone1, zone2, zone3, zone4, zone5)
                .map(ZoneApi::getVirtualId)
                .collect(Collectors.toList());
        tester.configServer().bootstrap(nonControllerZones, List.of(SystemApplication.tenantHost));
        tester.configServer().addNodes(List.of(zone0.getVirtualId()), List.of(SystemApplication.controllerHost));

        // Add system application that exists in a real system, but isn't eligible for OS upgrades
        tester.configServer().addNodes(nonControllerZones, List.of(SystemApplication.configServer));

        // Change state of a few nodes. These should not affect convergence
        failNodeIn(zone1, SystemApplication.tenantHost);
        failNodeIn(zone3, SystemApplication.tenantHost);
        Node nodeDeferringOsUpgrade = deferOsUpgradeIn(zone2, SystemApplication.tenantHost);

        // New OS version released
        Version version1 = Version.fromString("7.1");
        tester.controller().upgradeOsIn(cloud1, Version.fromString("7.0"), false);
        tester.controller().upgradeOsIn(cloud1, version1, false);
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

        // zone 2 and 3: enough nodes upgrade to satisfy node slice of this step
        completeUpgrade(1, version1, SystemApplication.tenantHost, zone2);
        completeUpgrade(1, version1, SystemApplication.tenantHost, zone3);
        assertEquals(Version.emptyVersion,
                     nodeRepository().list(zone2.getVirtualId(), NodeFilter.all().hostnames(nodeDeferringOsUpgrade.hostname()))
                                     .get(0)
                                     .currentOsVersion(),
                     "Current version is unchanged for node deferring OS upgrade");

        // zone 4: begins upgrading
        osUpgrader.maintain();
        assertWanted(version1, SystemApplication.tenantHost, zone4);

        // zone 4: completes upgrade
        completeUpgrade(version1, SystemApplication.tenantHost, zone4);

        // zone 2 and 3: stragglers complete upgrade
        completeUpgrade(version1, SystemApplication.tenantHost, zone2, zone3);

        // Next run does nothing as all zones are upgraded
        osUpgrader.maintain();
        assertWanted(version1, SystemApplication.tenantHost, zone1, zone2, zone3, zone4);
        statusUpdater.maintain();
        assertTrue(tester.controller().osVersionStatus().nodesIn(cloud1).stream()
                        .filter(node -> !node.hostname().equals(nodeDeferringOsUpgrade.hostname()))
                        .allMatch(node -> node.currentVersion().equals(version1)),
                "All non-deferring nodes are on target version");
    }

    @Test
    void upgrade_os_nodes_choose_newer_version() {
        CloudName cloud = CloudName.from("cloud");
        ZoneApi zone1 = zone("dev.us-east-1", cloud);
        ZoneApi zone2 = zone("prod.us-west-1", cloud);
        UpgradePolicy upgradePolicy = UpgradePolicy.builder()
                .upgrade(zone1)
                .upgrade(zone2)
                .build();
        OsUpgrader osUpgrader = osUpgrader(upgradePolicy, cloud, false);

        // Bootstrap system
        tester.configServer().bootstrap(List.of(zone1.getId(), zone2.getId()),
                List.of(SystemApplication.tenantHost));

        // New OS version released
        Version version = Version.fromString("7.1");
        tester.controller().upgradeOsIn(cloud, Version.fromString("7.0"), false);
        tester.controller().upgradeOsIn(cloud, version, false); // Replaces existing target
        statusUpdater.maintain();

        // zone 1 upgrades
        osUpgrader.maintain();
        assertWanted(version, SystemApplication.tenantHost, zone1);
        Version chosenVersion = Version.fromString("7.1.1"); // Upgrade mechanism chooses a slightly newer version
        completeUpgrade(Integer.MAX_VALUE, version, chosenVersion, SystemApplication.tenantHost, zone1);
        statusUpdater.maintain();
        assertEquals(3, nodesOn(chosenVersion).size());

        // zone 2 upgrades
        osUpgrader.maintain();
        assertWanted(version, SystemApplication.tenantHost, zone2);
        completeUpgrade(Integer.MAX_VALUE, version, chosenVersion, SystemApplication.tenantHost, zone2);
        statusUpdater.maintain();
        assertEquals(6, nodesOn(chosenVersion).size());

        // No more upgrades
        osUpgrader.maintain();
        assertWanted(version, SystemApplication.tenantHost, zone1, zone2);
        assertTrue(tester.controller().osVersionStatus().nodesIn(cloud).stream()
                .noneMatch(node -> node.currentVersion().isBefore(version)), "All nodes on target version or newer");
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
            assertEquals(version,
                         nodeRepository().targetVersionsOf(zone.getVirtualId()).osVersion(application.nodeType())
                                         .orElse(Version.emptyVersion),
                         "Target version set for " + application + " in " + zone.getVirtualId());
        }
    }

    private void assertVersion(SystemApplication application, Version version, Function<Node, Version> versionField,
                               ZoneApi... zones) {
        for (ZoneApi zone : zones) {
            for (Node node : nodesRequiredToUpgrade(zone, application)) {
                assertEquals(version, versionField.apply(node), application + " version in " + zone.getId());
            }
        }
    }

    private List<Node> nodesRequiredToUpgrade(ZoneApi zone, SystemApplication application) {
        return nodeRepository().list(zone.getVirtualId(), NodeFilter.all().applications(application.id()))
                               .stream()
                               .filter(node -> OsUpgrader.canUpgrade(node, false))
                               .collect(Collectors.toList());
    }

    private Node failNodeIn(ZoneApi zone, SystemApplication application) {
        return patchOneNodeIn(zone, application, (node) -> Node.builder(node).state(Node.State.failed).build());
    }

    private Node deferOsUpgradeIn(ZoneApi zone, SystemApplication application) {
        return patchOneNodeIn(zone, application, (node) -> Node.builder(node).deferOsUpgrade(true).build());
    }

    private Node patchOneNodeIn(ZoneApi zone, SystemApplication application, UnaryOperator<Node> patcher) {
        List<Node> nodes = nodeRepository().list(zone.getVirtualId(), NodeFilter.all().applications(application.id()));
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("No nodes allocated to " + application.id());
        }
        Node node = nodes.get(0);
        Node newNode = patcher.apply(node);
        nodeRepository().putNodes(zone.getVirtualId(), newNode);
        return newNode;
    }

    /** Simulate OS upgrade of nodes allocated to application. In a real system this is done by the node itself */
    private void completeUpgrade(Version version, SystemApplication application, ZoneApi... zones) {
        completeUpgrade(-1, version, application, zones);
    }

    private void completeUpgrade(int nodeCount, Version version, SystemApplication application, ZoneApi... zones) {
        completeUpgrade(nodeCount, version, version, application, zones);
    }

    private void completeUpgrade(int nodeCount, Version wantedVersion, Version version, SystemApplication application, ZoneApi... zones) {
        assertWanted(wantedVersion, application, zones);
        for (ZoneApi zone : zones) {
            int nodesUpgraded = 0;
            List<Node> nodes = nodesRequiredToUpgrade(zone, application);
            for (Node node : nodes) {
                if (node.currentVersion().equals(wantedVersion)) continue;
                nodeRepository().putNodes(zone.getVirtualId(), Node.builder(node).wantedOsVersion(version)
                                                                   .currentOsVersion(version)
                                                                   .build());
                if (++nodesUpgraded == nodeCount) {
                    break;
                }
            }
            if (nodesUpgraded == nodes.size()) {
                assertCurrent(version, application, zone);
            }
        }
    }

    private NodeRepositoryMock nodeRepository() {
        return tester.configServer().nodeRepository();
    }

    private OsUpgrader osUpgrader(UpgradePolicy upgradePolicy, CloudName cloud, boolean dynamicProvisioning) {
        var zones = upgradePolicy.steps().stream().map(Step::zones).flatMap(Collection::stream).collect(Collectors.toList());
        tester.zoneRegistry()
              .setZones(zones)
              .setOsUpgradePolicy(cloud, upgradePolicy);
        if (dynamicProvisioning) {
            tester.zoneRegistry().dynamicProvisioningIn(zones);
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
