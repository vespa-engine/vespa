// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.zone.UpgradePolicy;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryMock;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class OsUpgraderTest {

    private static final ZoneId zone1 = ZoneId.from("prod", "eu-west-1");
    private static final ZoneId zone2 = ZoneId.from("prod", "us-west-1");
    private static final ZoneId zone3 = ZoneId.from("prod", "us-central-1");
    private static final ZoneId zone4 = ZoneId.from("prod", "us-east-3");

    private DeploymentTester tester;
    private OsUpgrader osUpgrader;

    @Before
    public void before() {
        tester = new DeploymentTester();
    }

    @Test
    public void upgrade_os() {
        osUpgrader = osUpgrader(
                UpgradePolicy.create()
                             .upgrade(zone1)
                             .upgradeInParallel(zone2, zone3)
                             .upgrade(zone4)
        );

        // Bootstrap system
        tester.configServer().bootstrap(Arrays.asList(zone1, zone2, zone3, zone4),
                                        singletonList(SystemApplication.zone),
                                        Optional.of(NodeType.host));

        // Add system applications that exist in a real system, but are currently not upgraded
        tester.configServer().addNodes(Arrays.asList(zone1, zone2, zone3, zone4),
                                       Arrays.asList(SystemApplication.configServer,
                                                     SystemApplication.configServerHost),
                                       Optional.empty());

        // Fail a few nodes. Failed nodes should not affect versions
        failNodeIn(zone1, SystemApplication.zone);
        failNodeIn(zone3, SystemApplication.zone);

        // New OS version released
        Version version1 = Version.fromString("7.1");
        tester.controller().upgradeOs(version1);

        // zone 1: begins upgrading
        osUpgrader.maintain();
        assertWanted(version1, SystemApplication.zone, zone1);

        // Other zones remain on previous version (none)
        assertWanted(Version.emptyVersion, SystemApplication.zone, zone2, zone3, zone4);

        // zone 1: completes upgrade
        completeUpgrade(version1, SystemApplication.zone, zone1);

        // zone 2 and 3: begins upgrading
        osUpgrader.maintain();
        assertWanted(version1, SystemApplication.zone, zone2, zone3);

        // zone 4: still on previous version
        assertWanted(Version.emptyVersion, SystemApplication.zone, zone4);

        // zone 2 and 3: completes upgrade
        completeUpgrade(version1, SystemApplication.zone, zone2, zone3);

        // zone 4: begins upgrading
        osUpgrader.maintain();
        assertWanted(version1, SystemApplication.zone, zone4);

        // zone 4: completes upgrade
        completeUpgrade(version1, SystemApplication.zone, zone4);

        // Next run does nothing as all zones are upgraded
        osUpgrader.maintain();
        assertWanted(version1, SystemApplication.zone, zone1, zone2, zone3, zone4);

        // TODO: Test that OS version status is updated
    }

    private void assertCurrent(Version version, SystemApplication application, ZoneId... zones) {
        assertVersion(application, version, Node::currentOsVersion, zones);
    }

    private void assertWanted(Version version, SystemApplication application, ZoneId... zones) {
        assertVersion(application, version, Node::wantedOsVersion, zones);
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
                               .filter(node -> osUpgrader.requireUpgradeOf(node, application))
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
                        node.wantedVersion(), node.wantedOsVersion(), node.wantedOsVersion(), node.serviceState(),
                        node.restartGeneration(), node.wantedRestartGeneration(), node.rebootGeneration(),
                        node.wantedRebootGeneration()));
            }
            assertCurrent(version, application, zone);
        }
    }

    private NodeRepositoryMock nodeRepository() {
        return tester.controllerTester().configServer().nodeRepository();
    }

    private OsUpgrader osUpgrader(UpgradePolicy upgradePolicy) {
        tester.controllerTester().zoneRegistry().setSystemName(SystemName.cd);
        tester.controllerTester().zoneRegistry().setUpgradePolicy(upgradePolicy);
        return new OsUpgrader(tester.controller(), Duration.ofDays(1),
                              new JobControl(tester.controllerTester().curator()));
    }

}
