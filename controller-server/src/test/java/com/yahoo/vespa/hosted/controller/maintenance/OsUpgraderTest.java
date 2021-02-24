// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.versions.NodeVersion;
import org.junit.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
        ZoneApi zone1 = zone("prod.eu-west-1", cloud1);
        ZoneApi zone2 = zone("prod.us-west-1", cloud1);
        ZoneApi zone3 = zone("prod.us-central-1", cloud1);
        ZoneApi zone4 = zone("prod.us-east-3", cloud1);
        ZoneApi zone5 = zone("prod.us-north-1", cloud2);
        UpgradePolicy upgradePolicy = UpgradePolicy.create()
                                                   .upgrade(zone1)
                                                   .upgradeInParallel(zone2, zone3)
                                                   .upgrade(zone5) // Belongs to a different cloud and is ignored by this upgrader
                                                   .upgrade(zone4);
        OsUpgrader osUpgrader = osUpgrader(upgradePolicy, SystemName.cd, cloud1, false);

        // Bootstrap system
        tester.configServer().bootstrap(List.of(zone1.getId(), zone2.getId(), zone3.getId(), zone4.getId(), zone5.getId()),
                                        List.of(SystemApplication.tenantHost));

        // Add system applications that exist in a real system, but isn't upgraded
        tester.configServer().addNodes(List.of(zone1.getId(), zone2.getId(), zone3.getId(), zone4.getId(), zone5.getId()),
                                       List.of(SystemApplication.configServer));

        // Fail a few nodes. Failed nodes should not affect versions
        failNodeIn(zone1.getId(), SystemApplication.tenantHost);
        failNodeIn(zone3.getId(), SystemApplication.tenantHost);

        // New OS version released
        Version version1 = Version.fromString("7.1");
        tester.controller().upgradeOsIn(cloud1, Version.fromString("7.0"), Optional.empty(), false);
        tester.controller().upgradeOsIn(cloud1, version1, Optional.empty(), false);
        assertEquals(1, tester.controller().osVersionTargets().size()); // Only allows one version per cloud
        statusUpdater.maintain();

        // zone 1: begins upgrading
        osUpgrader.maintain();
        assertWanted(version1, SystemApplication.tenantHost, zone1.getId());

        // Other zones remain on previous version (none)
        assertWanted(Version.emptyVersion, SystemApplication.proxy, zone2.getId(), zone3.getId(), zone4.getId());

        // zone 1: completes upgrade
        completeUpgrade(version1, SystemApplication.tenantHost, zone1.getId());
        statusUpdater.maintain();
        assertEquals(2, nodesOn(version1).size());
        assertEquals(11, nodesOn(Version.emptyVersion).size());

        // zone 2 and 3: begins upgrading
        osUpgrader.maintain();
        assertWanted(version1, SystemApplication.tenantHost, zone2.getId(), zone3.getId());

        // zone 4: still on previous version
        assertWanted(Version.emptyVersion, SystemApplication.tenantHost, zone4.getId());

        // zone 2 and 3: completes upgrade
        completeUpgrade(version1, SystemApplication.tenantHost, zone2.getId(), zone3.getId());

        // zone 4: begins upgrading
        osUpgrader.maintain();
        assertWanted(version1, SystemApplication.tenantHost, zone4.getId());

        // zone 4: completes upgrade
        completeUpgrade(version1, SystemApplication.tenantHost, zone4.getId());

        // Next run does nothing as all zones are upgraded
        osUpgrader.maintain();
        assertWanted(version1, SystemApplication.tenantHost, zone1.getId(), zone2.getId(), zone3.getId(), zone4.getId());
        statusUpdater.maintain();
        assertTrue("All nodes on target version", tester.controller().osVersionStatus().nodesIn(cloud1).stream()
                                                        .allMatch(node -> node.currentVersion().equals(version1)));
    }

    @Test
    public void upgrade_os_with_budget() {
        CloudName cloud = CloudName.from("cloud");
        ZoneApi zone1 = zone("dev.us-east-1", cloud);
        ZoneApi zone2 = zone("prod.us-west-1", cloud);
        ZoneApi zone3 = zone("prod.us-central-1", cloud);
        ZoneApi zone4 = zone("prod.eu-west-1", cloud);
        UpgradePolicy upgradePolicy = UpgradePolicy.create()
                                                   .upgrade(zone1)
                                                   .upgradeInParallel(zone2, zone3)
                                                   .upgrade(zone4);
        OsUpgrader osUpgrader = osUpgrader(upgradePolicy, SystemName.cd, cloud, true);

        // Bootstrap system
        tester.configServer().bootstrap(List.of(zone1.getId(), zone2.getId(), zone3.getId(), zone4.getId()),
                                        List.of(SystemApplication.tenantHost));
        tester.configServer().addNodes(List.of(zone1.getId(), zone2.getId(), zone3.getId(), zone4.getId()),
                                       List.of(SystemApplication.configServerHost)); // Not supported yet

        // Upgrade without budget fails
        Version version = Version.fromString("7.1");
        try {
            tester.controller().upgradeOsIn(cloud, version, Optional.empty(), false);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {}

        // Upgrade with budget
        tester.controller().upgradeOsIn(cloud, version, Optional.of(Duration.ofHours(12)), false);
        assertEquals(Duration.ofHours(12), tester.controller().osVersionTarget(cloud).get().upgradeBudget().get());
        statusUpdater.maintain();
        osUpgrader.maintain();

        // First zone upgrades
        assertWanted(Version.emptyVersion, SystemApplication.configServerHost, zone1.getId());
        assertEquals("Dev zone gets a zero budget", Duration.ZERO, upgradeBudget(zone1.getId(), SystemApplication.tenantHost, version));
        completeUpgrade(version, SystemApplication.tenantHost, zone1.getId());

        // Next set of zones upgrade
        osUpgrader.maintain();
        for (var zone : List.of(zone2.getId(), zone3.getId())) {
            assertEquals("Parallel prod zones share the budget of a single zone", Duration.ofHours(6),
                         upgradeBudget(zone, SystemApplication.tenantHost, version));
            completeUpgrade(version, SystemApplication.tenantHost, zone);
        }

        // Last zone upgrades
        osUpgrader.maintain();
        assertEquals("Last prod zone gets the budget of a single zone", Duration.ofHours(6),
                     upgradeBudget(zone4.getId(), SystemApplication.tenantHost, version));
        completeUpgrade(version, SystemApplication.tenantHost, zone4.getId());

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
        OsUpgrader osUpgrader = osUpgrader(upgradePolicy, SystemName.cd, cloud, false);

        // Bootstrap system
        tester.configServer().bootstrap(List.of(zone1.getId(), zone2.getId()),
                                        List.of(SystemApplication.tenantHost));

        // New OS version released
        Version version = Version.fromString("7.1");
        tester.controller().upgradeOsIn(cloud, Version.fromString("7.0"), Optional.empty(), false);
        tester.controller().upgradeOsIn(cloud, version, Optional.empty(), false);
        statusUpdater.maintain();

        // zone 1 upgrades
        osUpgrader.maintain();
        assertWanted(version, SystemApplication.tenantHost, zone1.getId());
        Version chosenVersion = Version.fromString("7.1.1"); // Upgrade mechanism chooses a slightly newer version
        completeUpgrade(version, chosenVersion, SystemApplication.tenantHost, zone1.getId());
        statusUpdater.maintain();
        assertEquals(3, nodesOn(chosenVersion).size());

        // zone 2 upgrades
        osUpgrader.maintain();
        assertWanted(version, SystemApplication.tenantHost, zone2.getId());
        completeUpgrade(version, chosenVersion, SystemApplication.tenantHost, zone2.getId());
        statusUpdater.maintain();
        assertEquals(6, nodesOn(chosenVersion).size());

        // No more upgrades
        osUpgrader.maintain();
        assertWanted(version, SystemApplication.tenantHost, zone1.getId(), zone2.getId());
        assertTrue("All nodes on target version or newer", tester.controller().osVersionStatus().nodesIn(cloud).stream()
                                                                 .noneMatch(node -> node.currentVersion().isBefore(version)));
    }

    private Duration upgradeBudget(ZoneId zone, SystemApplication application, Version version) {
        var upgradeBudget = tester.configServer().nodeRepository().osUpgradeBudget(zone, application.nodeType(), version);
        assertTrue("Expected budget for upgrade to " + version + " of " + application.id() + " in " + zone,
                   upgradeBudget.isPresent());
        return upgradeBudget.get();
    }

    private List<NodeVersion> nodesOn(Version version) {
        return tester.controller().osVersionStatus().versions().entrySet().stream()
                     .filter(entry -> entry.getKey().version().equals(version))
                     .flatMap(entry -> entry.getValue().asMap().values().stream())
                     .collect(Collectors.toList());
    }

    private void assertCurrent(Version version, SystemApplication application, ZoneId... zones) {
        assertVersion(application, version, Node::currentOsVersion, zones);
    }

    private void assertWanted(Version version, SystemApplication application, ZoneId... zones) {
        for (var zone : zones) {
            assertEquals("Target version set for " + application + " in " + zone, version,
                         nodeRepository().targetVersionsOf(zone).osVersion(application.nodeType()).orElse(Version.emptyVersion));
        }
    }

    private void assertVersion(SystemApplication application, Version version, Function<Node, Version> versionField,
                               ZoneId... zones) {
        for (ZoneId zone : zones) {
            for (Node node : nodesRequiredToUpgrade(zone, application)) {
                assertEquals(application + " version in " + zone, version, versionField.apply(node));
            }
        }
    }

    private List<Node> nodesRequiredToUpgrade(ZoneId zone, SystemApplication application) {
        return nodeRepository().list(zone, application.id())
                               .stream()
                               .filter(OsUpgrader::canUpgrade)
                               .collect(Collectors.toList());
    }

    private void failNodeIn(ZoneId zone, SystemApplication application) {
        List<Node> nodes = nodeRepository().list(zone, application.id());
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("No nodes allocated to " + application.id());
        }
        Node node = nodes.get(0);
        nodeRepository().putNodes(zone, new Node.Builder(node).state(Node.State.failed).build());
    }

    /** Simulate OS upgrade of nodes allocated to application. In a real system this is done by the node itself */
    private void completeUpgrade(Version version, SystemApplication application, ZoneId... zones) {
        completeUpgrade(version, version, application, zones);
    }

    private void completeUpgrade(Version wantedVersion, Version version, SystemApplication application, ZoneId... zones) {
        assertWanted(wantedVersion, application, zones);
        for (ZoneId zone : zones) {
            for (Node node : nodesRequiredToUpgrade(zone, application)) {
                nodeRepository().putNodes(zone, new Node.Builder(node).wantedOsVersion(version).currentOsVersion(version).build());
            }
            assertCurrent(version, application, zone);
        }
    }

    private NodeRepositoryMock nodeRepository() {
        return tester.configServer().nodeRepository();
    }

    private OsUpgrader osUpgrader(UpgradePolicy upgradePolicy, SystemName system, CloudName cloud, boolean reprovisionToUpgradeOs) {
        var zones = upgradePolicy.asList().stream().flatMap(Collection::stream).collect(Collectors.toList());
        tester.zoneRegistry()
              .setZones(zones)
              .setSystemName(system)
              .setOsUpgradePolicy(cloud, upgradePolicy);
        if (reprovisionToUpgradeOs) {
            tester.zoneRegistry().reprovisionToUpgradeOsIn(zones);
        }
        return new OsUpgrader(tester.controller(), Duration.ofDays(1), cloud);
    }

    private static ZoneApi zone(String id, CloudName cloud) {
        return ZoneApiMock.newBuilder().withId(id).with(cloud).build();
    }

}
