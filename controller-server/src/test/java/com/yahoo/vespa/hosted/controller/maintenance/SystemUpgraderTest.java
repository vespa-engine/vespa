// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.NodeRepositoryMock;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.zone.UpgradePolicy;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class SystemUpgraderTest {

    private static final ZoneId zone1 = ZoneId.from("prod", "eu-west-1");
    private static final ZoneId zone2 = ZoneId.from("prod", "us-west-1");
    private static final ZoneId zone3 = ZoneId.from("prod", "us-central-1");
    private static final ZoneId zone4 = ZoneId.from("prod", "us-east-3");

    private final DeploymentTester tester = new DeploymentTester();

    @Test
    public void upgrade_system() {
        tester.controllerTester().zoneRegistry().setUpgradePolicy(
                UpgradePolicy.create()
                             .upgrade(zone1)
                             .upgradeInParallel(zone2, zone3)
                             .upgrade(zone4)
        );

        Version version1 = Version.fromString("6.5");
        tester.configServer().bootstrap(Arrays.asList(zone1, zone2, zone3, zone4));
        // Fail a few nodes. Failed nodes should not affect versions
        failNodeIn(zone1, SystemApplication.configServer);
        failNodeIn(zone3, SystemApplication.zone);
        tester.upgradeSystem(version1);
        tester.systemUpgrader().maintain();
        assertCurrentVersion(SystemApplication.configServer, version1, zone1, zone2, zone3, zone4);
        assertCurrentVersion(SystemApplication.zone, version1, zone1, zone2, zone3, zone4);

        // Controller upgrades
        Version version2 = Version.fromString("6.6");
        tester.upgradeController(version2);
        assertEquals(version2, tester.controller().versionStatus().controllerVersion().get().versionNumber());

        // System upgrade starts
        tester.systemUpgrader().maintain();
        assertWantedVersion(SystemApplication.configServer, version2, zone1);
        // Other zones remain on previous version
        assertWantedVersion(SystemApplication.configServer, version1, zone2, zone3, zone4);
        // Zone application is not upgraded yet
        assertWantedVersion(SystemApplication.zone, version1, zone1, zone2, zone3, zone4);

        // zone1: zone-config-server upgrades
        completeUpgrade(SystemApplication.configServer, version2, zone1);

        // zone 1: zone-application upgrades
        tester.systemUpgrader().maintain();
        assertWantedVersion(SystemApplication.zone, version2, zone1);
        completeUpgrade(SystemApplication.zone, version2, zone1);

        // zone 2, 3 and 4: still targets old version
        assertWantedVersion(SystemApplication.configServer, version1, zone2, zone3, zone4);
        assertWantedVersion(SystemApplication.zone, version1, zone2, zone3, zone4);

        // zone 2 and 3: zone-config-server upgrades in parallel
        tester.systemUpgrader().maintain();
        assertWantedVersion(SystemApplication.configServer, version2, zone2, zone3);
        assertWantedVersion(SystemApplication.configServer, version1, zone4);
        assertWantedVersion(SystemApplication.zone, version1, zone2, zone3, zone4);
        completeUpgrade(SystemApplication.configServer, version2, zone2, zone3);

        // zone 2 and 3: zone-application upgrades in parallel
        tester.systemUpgrader().maintain();
        assertWantedVersion(SystemApplication.zone, version2, zone2, zone3);
        completeUpgrade(SystemApplication.zone, version2, zone2, zone3);

        // zone 4: zone-config-server upgrades
        tester.systemUpgrader().maintain();
        assertWantedVersion(SystemApplication.configServer, version2, zone4);
        assertWantedVersion(SystemApplication.zone, version1, zone4);
        completeUpgrade(SystemApplication.configServer, version2, zone4);

        // System version remains unchanged until final application upgrades
        tester.computeVersionStatus();
        assertEquals(version1, tester.controller().versionStatus().systemVersion().get().versionNumber());

        // zone 4: zone-application upgrades
        tester.systemUpgrader().maintain();
        assertWantedVersion(SystemApplication.zone, version2, zone4);
        completeUpgrade(SystemApplication.zone, version2, zone4);
        tester.computeVersionStatus();
        assertEquals(version2, tester.controller().versionStatus().systemVersion().get().versionNumber());

        // Next run does nothing as system is now upgraded
        tester.systemUpgrader().maintain();
        assertWantedVersion(SystemApplication.configServer, version2, zone1, zone2, zone3, zone4);
        assertWantedVersion(SystemApplication.zone, version2, zone1, zone2, zone3, zone4);
    }

    @Test
    public void never_downgrades_system() {
        ZoneId zone = ZoneId.from("prod", "eu-west-1");
        tester.controllerTester().zoneRegistry().setUpgradePolicy(UpgradePolicy.create().upgrade(zone));

        Version version = Version.fromString("6.5");
        tester.upgradeSystem(version);
        tester.systemUpgrader().maintain();
        assertWantedVersion(SystemApplication.configServer, version, zone);
        assertWantedVersion(SystemApplication.zone, version, zone);

        // Controller is downgraded
        tester.upgradeController(Version.fromString("6.4"));

        // Wanted version for zone remains unchanged
        tester.systemUpgrader().maintain();
        assertWantedVersion(SystemApplication.configServer, version, zone);
        assertWantedVersion(SystemApplication.zone, version, zone);
    }

    /** Simulate upgrade of nodes allocated to given application. In a real system this is done by the node itself */
    private void completeUpgrade(SystemApplication application, Version version, ZoneId... zones) {
        for (ZoneId zone : zones) {
            for (Node node : nodeRepository().listOperational(zone, application.id())) {
                nodeRepository().add(zone, new Node(node.hostname(), node.state(), node.type(), node.owner(),
                                                    node.wantedVersion(), node.wantedVersion()));
            }
            assertCurrentVersion(application, version, zone);
        }
    }

    private void failNodeIn(ZoneId zone, SystemApplication application) {
        List<Node> nodes = nodeRepository().list(zone, application.id());
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("No nodes allocated to " + application.id());
        }
        Node node = nodes.get(0);
        nodeRepository().add(zone, new Node(node.hostname(), Node.State.failed, node.type(), node.owner(),
                                            node.currentVersion(), node.wantedVersion()));
    }

    private void assertWantedVersion(SystemApplication application, Version version, ZoneId... zones) {
        assertVersion(application.id(), version, Node::wantedVersion, zones);
    }

    private void assertCurrentVersion(SystemApplication application, Version version, ZoneId... zones) {
        assertVersion(application.id(), version, Node::currentVersion, zones);
    }

    private void assertVersion(ApplicationId application, Version version, Function<Node, Version> versionField,
                               ZoneId... zones) {
        for (ZoneId zone : zones) {
            for (Node node : nodeRepository().listOperational(zone, application)) {
                assertEquals(version, versionField.apply(node));
            }
        }
    }

    private NodeRepositoryMock nodeRepository() {
        return tester.controllerTester().configServer().nodeRepository();
    }

}
