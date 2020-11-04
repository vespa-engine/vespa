// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class SystemUpgraderTest {

    private static final ZoneApi zone1 = ZoneApiMock.fromId("prod.eu-west-1");
    private static final ZoneApi zone2 = ZoneApiMock.fromId("prod.us-west-1");
    private static final ZoneApi zone3 = ZoneApiMock.fromId("prod.us-central-1");
    private static final ZoneApi zone4 = ZoneApiMock.fromId("prod.us-east-3");

    private final ControllerTester tester = new ControllerTester();

    @Test
    public void upgrade_system() {
        SystemUpgrader systemUpgrader = systemUpgrader(
                UpgradePolicy.create()
                             .upgrade(zone1)
                             .upgradeInParallel(zone2, zone3)
                             .upgrade(zone4)
        );

        Version version1 = Version.fromString("6.5");
        // Bootstrap a system without host applications
        tester.configServer().bootstrap(List.of(zone1.getId(), zone2.getId(), zone3.getId(), zone4.getId()),
                                        SystemApplication.configServer, SystemApplication.proxy);
        // Fail a few nodes. Failed nodes should not affect versions
        failNodeIn(zone1, SystemApplication.configServer);
        failNodeIn(zone3, SystemApplication.proxy);
        tester.upgradeSystem(version1);
        systemUpgrader.maintain();
        assertCurrentVersion(SystemApplication.configServer, version1, zone1, zone2, zone3, zone4);
        assertCurrentVersion(SystemApplication.proxy, version1, zone1, zone2, zone3, zone4);
        assertSystemVersion(version1);

        // Controller upgrades
        Version version2 = Version.fromString("6.6");
        tester.upgradeController(version2);
        assertControllerVersion(version2);

        // System upgrade starts
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.configServer, version2, zone1);
        // Other zones remain on previous version
        assertWantedVersion(SystemApplication.configServer, version1, zone2, zone3, zone4);
        // Zone application is not upgraded yet
        assertWantedVersion(SystemApplication.proxy, version1, zone1, zone2, zone3, zone4);

        // zone1: zone-config-server upgrades
        completeUpgrade(SystemApplication.configServer, version2, zone1);

        // zone 1: proxy-application upgrades
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.proxy, version2, zone1);
        completeUpgrade(SystemApplication.proxy, version2, zone1);
        assertTrue("Deployed proxy application",
                   tester.configServer().application(SystemApplication.proxy.id(), zone1.getId()).isPresent());

        // zone 2, 3 and 4: still targets old version
        assertWantedVersion(SystemApplication.configServer, version1, zone2, zone3, zone4);
        assertWantedVersion(SystemApplication.proxy, version1, zone2, zone3, zone4);

        // zone 2 and 3: upgrade does not start until zone 1 zone-application config converges
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.configServer, version1, zone2, zone3);
        convergeServices(SystemApplication.proxy, zone1);

        // zone 2 and 3: zone-config-server upgrades, first in zone 2, then in zone 3
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.configServer, version2, zone2, zone3);
        assertWantedVersion(SystemApplication.configServer, version1, zone4);
        assertWantedVersion(SystemApplication.proxy, version1, zone2, zone3, zone4);
        completeUpgrade(SystemApplication.configServer, version2, zone2);

        // zone-application starts upgrading in zone 2, while zone-config-server completes upgrade in zone 3
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.proxy, version2, zone2);
        assertWantedVersion(SystemApplication.proxy, version1, zone3);
        completeUpgrade(SystemApplication.configServer, version2, zone3);

        // zone 2 and 3: proxy-application upgrades in parallel
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.proxy, version2, zone2, zone3);
        completeUpgrade(SystemApplication.proxy, version2, zone2, zone3);
        convergeServices(SystemApplication.proxy, zone2, zone3);

        // zone 4: zone-config-server upgrades
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.configServer, version2, zone4);
        assertWantedVersion(SystemApplication.proxy, version1, zone4);
        completeUpgrade(SystemApplication.configServer, version2, zone4);

        // System version remains unchanged until final application upgrades
        tester.computeVersionStatus();
        assertSystemVersion(version1);

        // zone 4: proxy-application upgrades
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.proxy, version2, zone4);
        completeUpgrade(SystemApplication.proxy, version2, zone4);

        // zone 4: System version remains unchanged until config converges
        tester.computeVersionStatus();
        assertSystemVersion(version1);
        convergeServices(SystemApplication.proxy, zone4);
        tester.computeVersionStatus();
        assertSystemVersion(version2);

        // Next run does nothing as system is now upgraded
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.configServer, version2, zone1, zone2, zone3, zone4);
        assertWantedVersion(SystemApplication.proxy, version2, zone1, zone2, zone3, zone4);
    }

    @Test
    public void upgrade_controller_with_non_converging_application() {
        SystemUpgrader systemUpgrader = systemUpgrader(UpgradePolicy.create().upgrade(zone1));

        // Bootstrap system
        tester.configServer().bootstrap(List.of(zone1.getId()), SystemApplication.configServer,
                                        SystemApplication.proxy);
        Version version1 = Version.fromString("6.5");
        tester.upgradeSystem(version1);

        // Controller upgrades
        Version version2 = Version.fromString("6.6");
        tester.upgradeController(version2);

        // zone 1: System applications upgrade
        systemUpgrader.maintain();
        completeUpgrade(SystemApplication.configServer, version2, zone1);
        systemUpgrader.maintain();
        completeUpgrade(SystemApplication.proxy, version2, zone1);
        tester.computeVersionStatus();
        assertSystemVersion(version1); // Unchanged until proxy-application converges

        // Controller upgrades again
        Version version3 = Version.fromString("6.7");
        tester.upgradeController(version3);
        assertSystemVersion(version1);
        assertControllerVersion(version3);

        // zone 1: proxy-application converges and system version changes
        convergeServices(SystemApplication.proxy, zone1);
        tester.computeVersionStatus();
        assertSystemVersion(version2);
        assertControllerVersion(version3);
    }

    @Test
    public void upgrade_system_containing_host_applications() {
        SystemUpgrader systemUpgrader = systemUpgrader(
                UpgradePolicy.create()
                             .upgrade(zone1)
                             .upgradeInParallel(zone2, zone3)
                             .upgrade(zone4)
        );

        Version version1 = Version.fromString("6.5");
        tester.configServer().bootstrap(List.of(zone1.getId(), zone2.getId(), zone3.getId(), zone4.getId()), SystemApplication.all());
        tester.upgradeSystem(version1);
        systemUpgrader.maintain();
        assertCurrentVersion(SystemApplication.all(), version1, zone1, zone2, zone3, zone4);

        // Controller upgrades
        Version version2 = Version.fromString("6.6");
        tester.upgradeController(version2);
        assertControllerVersion(version2);

        // System upgrades in zone 1:
        systemUpgrader.maintain();
        List<SystemApplication> allExceptZone = List.of(SystemApplication.configServerHost,
                                                        SystemApplication.configServer,
                                                        SystemApplication.proxyHost,
                                                        SystemApplication.tenantHost);
        completeUpgrade(allExceptZone, version2, zone1);
        systemUpgrader.maintain();
        completeUpgrade(SystemApplication.proxy, version2, zone1);
        convergeServices(SystemApplication.proxy, zone1);
        assertWantedVersion(SystemApplication.all(), version1, zone2, zone3, zone4);

        // zone 2 and 3:
        systemUpgrader.maintain();
        completeUpgrade(allExceptZone, version2, zone2, zone3);
        systemUpgrader.maintain();
        completeUpgrade(SystemApplication.proxy, version2, zone2, zone3);
        convergeServices(SystemApplication.proxy, zone2, zone3);
        assertWantedVersion(SystemApplication.all(), version1, zone4);

        // zone 4:
        systemUpgrader.maintain();
        completeUpgrade(allExceptZone, version2, zone4);
        systemUpgrader.maintain();
        completeUpgrade(SystemApplication.proxy, version2, zone4);

        // All done
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.all(), version2, zone1, zone2, zone3, zone4);
    }

    @Test
    public void downgrading_controller_never_downgrades_system() {
        SystemUpgrader systemUpgrader = systemUpgrader(UpgradePolicy.create().upgrade(zone1));

        Version version = Version.fromString("6.5");
        tester.upgradeSystem(version);
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.configServer, version, zone1);
        assertWantedVersion(SystemApplication.proxy, version, zone1);

        // Controller is downgraded
        tester.upgradeController(Version.fromString("6.4"));

        // Wanted version for zone remains unchanged
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.configServer, version, zone1);
        assertWantedVersion(SystemApplication.proxy, version, zone1);
    }

    @Test
    public void upgrade_halts_on_broken_version() {
        SystemUpgrader systemUpgrader = systemUpgrader(UpgradePolicy.create().upgrade(zone1).upgrade(zone2));

        // Initial system version
        Version version1 = Version.fromString("6.5");
        tester.upgradeSystem(version1);
        systemUpgrader.maintain();
        assertCurrentVersion(List.of(SystemApplication.configServerHost, SystemApplication.proxyHost,
                                     SystemApplication.configServer, SystemApplication.proxy),
                             version1, zone1);
        assertCurrentVersion(List.of(SystemApplication.configServerHost, SystemApplication.proxyHost,
                                     SystemApplication.configServer, SystemApplication.proxy),
                             version1, zone2);

        // System starts upgrading to next version
        Version version2 = Version.fromString("6.6");
        tester.upgradeController(version2);
        systemUpgrader.maintain();
        completeUpgrade(List.of(SystemApplication.configServerHost, SystemApplication.proxyHost), version2, zone1);
        systemUpgrader.maintain();
        completeUpgrade(SystemApplication.configServer, version2, zone1);
        systemUpgrader.maintain();
        completeUpgrade(SystemApplication.proxy, version2, zone1);
        convergeServices(SystemApplication.proxy, zone1);

        // Confidence is reduced to broken and next zone is not scheduled for upgrade
        new Upgrader(tester.controller(), Duration.ofDays(1))
                .overrideConfidence(version2, VespaVersion.Confidence.broken);
        tester.computeVersionStatus();
        systemUpgrader.maintain();
        assertWantedVersion(List.of(SystemApplication.configServerHost, SystemApplication.proxyHost,
                                    SystemApplication.configServer, SystemApplication.proxy), version1, zone2);
    }

    @Test
    public void does_not_deploy_proxy_app_in_zone_without_shared_routing() {
        var applications = List.of(SystemApplication.configServerHost, SystemApplication.configServer,
                                   SystemApplication.tenantHost);
        tester.configServer().bootstrap(List.of(zone1.getId()), applications);
        tester.configServer().disallowConvergenceCheck(SystemApplication.proxy.id());
        tester.zoneRegistry().exclusiveRoutingIn(zone1);
        var systemUpgrader = systemUpgrader(UpgradePolicy.create().upgrade(zone1));

        // System begins upgrade
        var version1 = Version.fromString("6.5");
        tester.upgradeController(version1);
        systemUpgrader.maintain();
        assertWantedVersion(applications, version1, zone1);
        assertWantedVersion(SystemApplication.proxy, Version.emptyVersion, zone1);

        // System completes upgrade. Wanted version is not raised for proxy as it's is never deployed
        completeUpgrade(applications, version1, zone1);
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.proxy, Version.emptyVersion, zone1);
        tester.computeVersionStatus();
        assertEquals(version1, tester.controller().readSystemVersion());
    }

    /** Simulate upgrade of nodes allocated to given application. In a real system this is done by the node itself */
    private void completeUpgrade(SystemApplication application, Version version, ZoneApi first, ZoneApi... rest) {
        assertWantedVersion(application, version, first, rest);
        Stream.concat(Stream.of(first), Stream.of(rest)).forEach(zone -> {
            for (Node node : listNodes(zone, application)) {
                nodeRepository().putNodes(
                        zone.getId(),
                        new Node.Builder(node).currentVersion(node.wantedVersion()).build());
            }
            assertCurrentVersion(application, version, zone);
        });
    }

    private void convergeServices(SystemApplication application, ZoneApi first, ZoneApi... rest) {
        Stream.concat(Stream.of(first), Stream.of(rest)).forEach(zone -> {
            tester.configServer().convergeServices(application.id(), zone.getId());
        });
    }

    private void completeUpgrade(List<SystemApplication> applications, Version version, ZoneApi zone, ZoneApi... rest) {
        applications.forEach(application -> completeUpgrade(application, version, zone, rest));
    }

    private void failNodeIn(ZoneApi zone, SystemApplication application) {
        List<Node> nodes = nodeRepository().list(zone.getId(), application.id());
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("No nodes allocated to " + application.id());
        }
        Node node = nodes.get(0);
        nodeRepository().putNodes(
                zone.getId(),
                new Node.Builder(node).state(Node.State.failed).build());
    }

    private void assertSystemVersion(Version version) {
        assertEquals(version, tester.controller().readSystemVersion());
    }

    private void assertControllerVersion(Version version) {
        assertEquals(version, tester.controller().readVersionStatus().controllerVersion().get().versionNumber());
    }

    private void assertWantedVersion(SystemApplication application, Version version, ZoneApi first, ZoneApi... rest) {
        Stream.concat(Stream.of(first), Stream.of(rest)).forEach(zone -> {
            if (!application.hasApplicationPackage()) {
                assertEquals("Target version set for " + application + " in " + zone.getId(), version,
                             nodeRepository().targetVersionsOf(zone.getId()).vespaVersion(application.nodeType()).orElse(Version.emptyVersion));
            }
            assertVersion(application, version, Node::wantedVersion, zone);
        });
    }

    private void assertCurrentVersion(SystemApplication application, Version version, ZoneApi first, ZoneApi... rest) {
        assertVersion(application, version, Node::currentVersion, first, rest);
    }

    private void assertWantedVersion(List<SystemApplication> applications, Version version, ZoneApi first, ZoneApi... rest) {
        applications.forEach(application -> assertWantedVersion(application, version, first, rest));
    }

    private void assertCurrentVersion(List<SystemApplication> applications, Version version, ZoneApi first, ZoneApi... rest) {
        applications.forEach(application -> assertVersion(application, version, Node::currentVersion, first, rest));
    }

    private void assertVersion(SystemApplication application, Version version, Function<Node, Version> versionField,
                               ZoneApi first, ZoneApi... rest) {
        Stream.concat(Stream.of(first), Stream.of(rest)).forEach(zone -> {
            for (Node node : listNodes(zone, application)) {
                assertEquals("Version of " + application.id() + " in " + zone.getId(), version, versionField.apply(node));
            }
        });
    }

    private List<Node> listNodes(ZoneApi zone, SystemApplication application) {
        return nodeRepository().list(zone.getId(), application.id()).stream()
                               .filter(SystemUpgrader::eligibleForUpgrade)
                               .collect(Collectors.toList());
    }

    private NodeRepositoryMock nodeRepository() {
        return tester.configServer().nodeRepository();
    }

    private SystemUpgrader systemUpgrader(UpgradePolicy upgradePolicy) {
        tester.zoneRegistry().setUpgradePolicy(upgradePolicy);
        return new SystemUpgrader(tester.controller(), Duration.ofDays(1));
    }

}
