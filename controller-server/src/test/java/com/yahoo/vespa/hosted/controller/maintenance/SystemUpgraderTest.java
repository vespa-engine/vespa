// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
    void upgrade_system() {
        SystemUpgrader systemUpgrader = systemUpgrader(
                UpgradePolicy.builder()
                        .upgrade(zone1)
                        .upgradeInParallel(zone2, zone3)
                        .upgrade(zone4)
                        .build()
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
        // Proxy application is not upgraded yet
        assertWantedVersion(SystemApplication.proxy, version1, zone1, zone2, zone3, zone4);

        // zone1: config server upgrades and proxy application
        completeUpgrade(SystemApplication.configServer, version2, zone1);
        systemUpgrader.maintain();

        // zone 1: proxy application upgrades
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.proxy, version2, zone1);
        completeUpgrade(SystemApplication.proxy, version2, zone1);
        assertTrue(tester.configServer().application(SystemApplication.proxy.id(), zone1.getId()).isPresent(),
                "Deployed proxy application");

        // zone 2, 3 and 4: still targets old version
        assertWantedVersion(SystemApplication.configServer, version1, zone2, zone3, zone4);
        assertWantedVersion(SystemApplication.proxy, version1, zone2, zone3, zone4);

        // zone 2 and 3: upgrade does not start until zone 1 proxy application config converges
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.configServer, version1, zone2, zone3);
        convergeServices(SystemApplication.proxy, zone1);

        // zone 2 and 3: config server upgrades, first in zone 2, then in zone 3
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.configServer, version2, zone2, zone3);
        assertWantedVersion(SystemApplication.configServer, version1, zone4);
        assertWantedVersion(SystemApplication.proxy, version1, zone2, zone3, zone4);
        completeUpgrade(SystemApplication.configServer, version2, zone2);

        // proxy application starts upgrading in zone 2, while config server completes upgrade in zone 3
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.proxy, version2, zone2);
        assertWantedVersion(SystemApplication.proxy, version1, zone3);
        completeUpgrade(SystemApplication.configServer, version2, zone3);

        // zone 2 and 3: proxy application upgrades in parallel
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.proxy, version2, zone2, zone3);
        completeUpgrade(SystemApplication.proxy, version2, zone2, zone3);
        convergeServices(SystemApplication.proxy, zone2, zone3);

        // zone 4: config server upgrades
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.configServer, version2, zone4);
        assertWantedVersion(SystemApplication.proxy, version1, zone4);
        // zone 4: proxy application does not upgrade until all config servers are done
        completeUpgrade(2, SystemApplication.configServer, version2, zone4);
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.proxy, version1, zone4);
        completeUpgrade(1, SystemApplication.configServer, version2, zone4);

        // System version remains unchanged until final application upgrades
        tester.computeVersionStatus();
        assertSystemVersion(version1);

        // zone 4: proxy application upgrades
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
    void upgrade_controller_with_non_converging_application() {
        SystemUpgrader systemUpgrader = systemUpgrader(UpgradePolicy.builder().upgrade(zone1).build());

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
        assertSystemVersion(version1); // Unchanged until proxy application converges

        // Controller upgrades again
        Version version3 = Version.fromString("6.7");
        tester.upgradeController(version3);
        assertSystemVersion(version1);
        assertControllerVersion(version3);

        // zone 1: proxy application converges and system version changes
        convergeServices(SystemApplication.proxy, zone1);
        tester.computeVersionStatus();
        assertSystemVersion(version2);
        assertControllerVersion(version3);
    }

    @Test
    void upgrade_system_containing_host_applications() {
        SystemUpgrader systemUpgrader = systemUpgrader(
                UpgradePolicy.builder()
                        .upgrade(zone1)
                        .upgradeInParallel(zone2, zone3)
                        .upgrade(zone4)
                        .build()
        );

        Version version1 = Version.fromString("6.5");
        tester.configServer().bootstrap(List.of(zone1.getId(), zone2.getId(), zone3.getId(), zone4.getId()), SystemApplication.notController());
        tester.upgradeSystem(version1);
        systemUpgrader.maintain();
        assertCurrentVersion(SystemApplication.notController(), version1, zone1, zone2, zone3, zone4);

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
        assertWantedVersion(SystemApplication.notController(), version1, zone2, zone3, zone4);

        // zone 2 and 3:
        systemUpgrader.maintain();
        completeUpgrade(allExceptZone, version2, zone2, zone3);
        systemUpgrader.maintain();
        completeUpgrade(SystemApplication.proxy, version2, zone2, zone3);
        convergeServices(SystemApplication.proxy, zone2, zone3);
        assertWantedVersion(SystemApplication.notController(), version1, zone4);

        // zone 4:
        systemUpgrader.maintain();
        completeUpgrade(allExceptZone, version2, zone4);
        systemUpgrader.maintain();
        completeUpgrade(SystemApplication.proxy, version2, zone4);

        // All done
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.notController(), version2, zone1, zone2, zone3, zone4);
    }

    @Test
    void downgrading_controller_never_downgrades_system() {
        SystemUpgrader systemUpgrader = systemUpgrader(UpgradePolicy.builder().upgrade(zone1).build());

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
    void upgrade_halts_on_broken_version() {
        SystemUpgrader systemUpgrader = systemUpgrader(UpgradePolicy.builder().upgrade(zone1).upgrade(zone2).build());

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
        overrideConfidence(version2, VespaVersion.Confidence.broken);
        systemUpgrader.maintain();
        assertWantedVersion(List.of(SystemApplication.configServerHost, SystemApplication.proxyHost,
                SystemApplication.configServer, SystemApplication.proxy), version1, zone2);
    }

    @Test
    void does_not_deploy_proxy_app_in_zone_without_shared_routing() {
        var applications = List.of(SystemApplication.configServerHost, SystemApplication.configServer,
                SystemApplication.tenantHost);
        tester.configServer().bootstrap(List.of(zone1.getId()), applications);
        tester.configServer().disallowConvergenceCheck(SystemApplication.proxy.id());
        tester.zoneRegistry().exclusiveRoutingIn(zone1);
        var systemUpgrader = systemUpgrader(UpgradePolicy.builder().upgrade(zone1).build());

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

    @Test
    void downgrade_from_aborted_version() {
        SystemUpgrader systemUpgrader = systemUpgrader(UpgradePolicy.builder().upgrade(zone1).upgrade(zone2).upgrade(zone3).build());

        Version version1 = Version.fromString("6.5");
        tester.configServer().bootstrap(List.of(zone1.getId(), zone2.getId(), zone3.getId()), SystemApplication.notController());
        tester.upgradeSystem(version1);
        systemUpgrader.maintain();
        assertCurrentVersion(SystemApplication.notController(), version1, zone1, zone2, zone3);

        // Controller upgrades
        Version version2 = Version.fromString("6.6");
        tester.upgradeController(version2);
        assertControllerVersion(version2);

        // 2/3 zones upgrade
        for (var zone : List.of(zone1, zone2)) {
            systemUpgrader.maintain();
            completeUpgrade(List.of(SystemApplication.tenantHost,
                            SystemApplication.proxyHost,
                            SystemApplication.configServerHost),
                    version2, zone);
            completeUpgrade(SystemApplication.configServer, version2, zone);
            systemUpgrader.maintain();
            completeUpgrade(SystemApplication.proxy, version2, zone);
            convergeServices(SystemApplication.proxy, zone);
        }

        // Upgrade is aborted
        overrideConfidence(version2, VespaVersion.Confidence.aborted);

        // Dependency graph is inverted and applications without dependencies downgrade first. Upgrade policy is
        // also followed in inverted order
        for (var zone : List.of(zone2, zone1)) {
            systemUpgrader.maintain();
            completeUpgrade(List.of(SystemApplication.tenantHost,
                            SystemApplication.configServerHost,
                            SystemApplication.proxy),
                    version1, zone);
            convergeServices(SystemApplication.proxy, zone);
            List<SystemApplication> lastToDowngrade = List.of(SystemApplication.configServer,
                    SystemApplication.proxyHost);
            assertWantedVersion(lastToDowngrade, version2, zone);

            // ... and then configserver and proxyhost
            systemUpgrader.maintain();
            completeUpgrade(lastToDowngrade, version1, zone);
        }
        assertSystemVersion(version1);

        // Another version is released and system upgrades
        Version version3 = Version.fromString("6.7");
        tester.upgradeSystem(version3);
        assertEquals(version3, tester.controller().readSystemVersion());

        // Attempt to abort current system version is rejected
        try {
            overrideConfidence(version3, VespaVersion.Confidence.aborted);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {
        }
        systemUpgrader.maintain();
        assertWantedVersion(SystemApplication.notController(), version3, zone1, zone2, zone3);
    }

    private void overrideConfidence(Version version, VespaVersion.Confidence confidence) {
        new Upgrader(tester.controller(), Duration.ofDays(1)).overrideConfidence(version, confidence);
        tester.computeVersionStatus();
    }

    private void completeUpgrade(SystemApplication application, Version version, ZoneApi first, ZoneApi... rest) {
        completeUpgrade(-1, application, version, first, rest);
    }

    /** Simulate upgrade of nodes allocated to given application. In a real system this is done by the node itself */
    private void completeUpgrade(int nodeCount, SystemApplication application, Version version, ZoneApi first, ZoneApi... rest) {
        assertWantedVersion(application, version, first, rest);
        Stream.concat(Stream.of(first), Stream.of(rest)).forEach(zone -> {
            int nodesUpgraded = 0;
            List<Node> nodes = listNodes(zone, application);
            for (Node node : nodes) {
                if (node.currentVersion().equals(node.wantedVersion())) continue;
                nodeRepository().putNodes(
                        zone.getId(),
                        Node.builder(node).currentVersion(node.wantedVersion()).build());
                if (++nodesUpgraded == nodeCount) {
                    break;
                }
            }
            if (nodesUpgraded == nodes.size()) {
                assertCurrentVersion(application, version, zone);
            }
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
        List<Node> nodes = nodeRepository().list(zone.getId(), NodeFilter.all().applications(application.id()));
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("No nodes allocated to " + application.id());
        }
        Node node = nodes.get(0);
        nodeRepository().putNodes(
                zone.getId(),
                Node.builder(node).state(Node.State.failed).build());
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
                assertEquals(version,
                             nodeRepository().targetVersionsOf(zone.getId()).vespaVersion(application.nodeType()).orElse(Version.emptyVersion),
                             "Target version set for " + application + " in " + zone.getId());
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
                assertEquals(version, versionField.apply(node), "Version of " + application.id() + " in " + zone.getId());
            }
        });
    }

    private List<Node> listNodes(ZoneApi zone, SystemApplication application) {
        return nodeRepository().list(zone.getId(), NodeFilter.all().applications(application.id())).stream()
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
