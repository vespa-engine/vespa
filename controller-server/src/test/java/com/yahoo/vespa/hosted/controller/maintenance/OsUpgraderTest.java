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
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class OsUpgraderTest {

    private static final ZoneApi zone1 = ZoneApiMock.newBuilder().withId("prod.eu-west-1").build();
    private static final ZoneApi zone2 = ZoneApiMock.newBuilder().withId("prod.us-west-1").build();
    private static final ZoneApi zone3 = ZoneApiMock.newBuilder().withId("prod.us-central-1").build();
    private static final ZoneApi zone4 = ZoneApiMock.newBuilder().withId("prod.us-east-3").build();
    private static final ZoneApi zone5 = ZoneApiMock.newBuilder().withId("prod.us-north-1").withCloud("other").build();

    private ControllerTester tester;
    private OsVersionStatusUpdater statusUpdater;

    @Before
    public void before() {
        tester = new ControllerTester();
        statusUpdater = new OsVersionStatusUpdater(tester.controller(), Duration.ofDays(1),
                                                   new JobControl(tester.controller().curator()));
    }

    @Test
    public void upgrade_os() {
        OsUpgrader osUpgrader = osUpgrader(
                UpgradePolicy.create()
                             .upgrade(zone1)
                             .upgradeInParallel(zone2, zone3)
                             .upgrade(zone5) // Belongs to a different cloud and is ignored by this upgrader
                             .upgrade(zone4),
                SystemName.cd
        );

        // Bootstrap system
        tester.configServer().bootstrap(List.of(zone1.getId(), zone2.getId(), zone3.getId(), zone4.getId(), zone5.getId()),
                                        List.of(SystemApplication.tenantHost));

        // Add system applications that exist in a real system, but are currently not upgraded
        tester.configServer().addNodes(List.of(zone1.getId(), zone2.getId(), zone3.getId(), zone4.getId(), zone5.getId()),
                                       List.of(SystemApplication.configServer));

        // Fail a few nodes. Failed nodes should not affect versions
        failNodeIn(zone1.getId(), SystemApplication.tenantHost);
        failNodeIn(zone3.getId(), SystemApplication.tenantHost);

        // New OS version released
        Version version1 = Version.fromString("7.1");
        CloudName cloud = CloudName.defaultName();
        tester.controller().upgradeOsIn(cloud, Version.fromString("7.0"), false);
        tester.controller().upgradeOsIn(cloud, version1, false);
        assertEquals(1, tester.controller().osVersions().size()); // Only allows one version per cloud
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
        assertTrue("All nodes on target version", tester.controller().osVersionStatus().nodesIn(cloud).stream()
                                                        .allMatch(node -> node.currentVersion().equals(version1)));
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
                               .filter(node -> OsUpgrader.eligibleForUpgrade(node, application))
                               .collect(Collectors.toList());
    }

    private void failNodeIn(ZoneId zone, SystemApplication application) {
        List<Node> nodes = nodeRepository().list(zone, application.id());
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("No nodes allocated to " + application.id());
        }
        Node node = nodes.get(0);
        nodeRepository().putByHostname(zone, new Node(node.hostname(), Node.State.failed, node.type(), node.owner(),
                                                      node.currentVersion(), node.wantedVersion()));
    }

    /** Simulate OS upgrade of nodes allocated to application. In a real system this is done by the node itself */
    private void completeUpgrade(Version version, SystemApplication application, ZoneId... zones) {
        assertWanted(version, application, zones);
        for (ZoneId zone : zones) {
            for (Node node : nodesRequiredToUpgrade(zone, application)) {
                nodeRepository().putByHostname(zone, new Node(
                        node.hostname(), node.state(), node.type(), node.owner(), node.currentVersion(),
                        node.wantedVersion(), version, version, node.serviceState(),
                        node.restartGeneration(), node.wantedRestartGeneration(), node.rebootGeneration(),
                        node.wantedRebootGeneration(), node.vcpu(), node.memoryGb(), node.diskGb(),
                        node.bandwidthGbps(), node.fastDisk(), node.cost(), node.canonicalFlavor(),
                        node.clusterId(), node.clusterType()));
            }
            assertCurrent(version, application, zone);
        }
    }

    private NodeRepositoryMock nodeRepository() {
        return tester.configServer().nodeRepository();
    }

    private OsUpgrader osUpgrader(UpgradePolicy upgradePolicy, SystemName system) {
        tester.zoneRegistry()
              .setZones(zone1, zone2, zone3, zone4, zone5)
              .setSystemName(system)
              .setOsUpgradePolicy(CloudName.defaultName(), upgradePolicy);
        return new OsUpgrader(tester.controller(), Duration.ofDays(1),
                              new JobControl(tester.curator()), CloudName.defaultName());
    }

}
