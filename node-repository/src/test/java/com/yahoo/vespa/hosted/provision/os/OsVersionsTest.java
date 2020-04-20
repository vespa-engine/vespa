// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.OsVersion;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author mpolden
 */
public class OsVersionsTest {

    private final ProvisioningTester tester = new ProvisioningTester.Builder().build();

    @Test
    public void test_versions() {
        var versions = new OsVersions(tester.nodeRepository(), Integer.MAX_VALUE);
        tester.makeReadyNodes(10, "default", NodeType.host);
        Supplier<List<Node>> hostNodes = () -> tester.nodeRepository().getNodes(NodeType.host);

        // Upgrade OS
        assertTrue("No versions set", versions.targets().isEmpty());
        var version1 = Version.fromString("7.1");
        versions.setTarget(NodeType.host, version1, false);
        assertEquals(version1, versions.targetFor(NodeType.host).get());
        assertTrue("Per-node wanted OS version remains unset", hostNodes.get().stream().allMatch(node -> node.status().osVersion().wanted().isEmpty()));

        // Upgrade OS again
        var version2 = Version.fromString("7.2");
        versions.setTarget(NodeType.host, version2, false);
        assertEquals(version2, versions.targetFor(NodeType.host).get());

        // Target can be (de)activated
        versions.setActive(NodeType.host, true);
        assertTrue("Target version activated", hostNodes.get().stream()
                                                        .allMatch(node -> node.status().osVersion().wanted().isPresent()));
        versions.setActive(NodeType.host, false);
        assertTrue("Target version deactivated", hostNodes.get().stream()
                                                          .allMatch(node -> node.status().osVersion().wanted().isEmpty()));

        // Downgrading fails
        try {
            versions.setTarget(NodeType.host, version1, false);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {}

        // Forcing downgrade succeeds
        versions.setTarget(NodeType.host, version1, true);
        assertEquals(version1, versions.targetFor(NodeType.host).get());

        // Target can be removed
        versions.removeTarget(NodeType.host);
        assertFalse(versions.targetFor(NodeType.host).isPresent());
        assertTrue(hostNodes.get().stream().allMatch(node -> node.status().osVersion().wanted().isEmpty()));
    }

    @Test
    public void test_max_active_upgrades() {
        int totalNodes = 20;
        int maxActiveUpgrades = 5;
        var versions = new OsVersions(tester.nodeRepository(), maxActiveUpgrades);
        tester.makeReadyNodes(totalNodes, "default", NodeType.host);
        Supplier<NodeList> hostNodes = () -> tester.nodeRepository().list().nodeType(NodeType.host);

        // 5 nodes have no version. The other 15 are spread across different versions
        var hostNodesList = hostNodes.get().asList();
        for (int i = totalNodes - maxActiveUpgrades - 1; i >= 0; i--) {
            setCurrentVersion(List.of(hostNodesList.get(i)), new Version(7, 0, i));
        }

        // Set target
        var version1 = Version.fromString("7.1");
        versions.setTarget(NodeType.host, version1, false);
        assertEquals(version1, versions.targetFor(NodeType.host).get());

        // Activate target
        for (int i = 0; i < totalNodes; i += maxActiveUpgrades) {
            versions.setActive(NodeType.host, true);
            var nodes = hostNodes.get();
            var nodesUpgrading = nodes.changingOsVersion();
            assertEquals("Target is changed for a subset of nodes", maxActiveUpgrades, nodesUpgrading.size());
            assertEquals("Wanted version is set for nodes upgrading", version1,
                         minVersion(nodesUpgrading, OsVersion::wanted));
            var nodesOnLowestVersion = nodes.asList().stream()
                                            .sorted(Comparator.comparing(node -> node.status().osVersion().current().orElse(Version.emptyVersion)))
                                            .collect(Collectors.toList())
                                            .subList(0, maxActiveUpgrades);
            assertEquals("Nodes on lowest version are told to upgrade",
                         nodesUpgrading.asList(), nodesOnLowestVersion);
            completeUpgradeOf(nodesUpgrading.asList());
        }

        // Activating again after all nodes have upgraded does nothing
        versions.setActive(NodeType.host, true);
        assertEquals("All nodes upgraded", version1, minVersion(hostNodes.get(), OsVersion::current));
    }

    @Test
    public void test_newer_upgrade_aborts_upgrade_to_stale_version() {
        var versions = new OsVersions(tester.nodeRepository(), Integer.MAX_VALUE);
        tester.makeReadyNodes(10, "default", NodeType.host);
        Supplier<NodeList> hostNodes = () -> tester.nodeRepository().list().nodeType(NodeType.host);

        // Some nodes are targeting an older version
        var version1 = Version.fromString("7.1");
        setWantedVersion(hostNodes.get().asList().subList(0, 5), version1);

        // Trigger upgrade to next version
        var version2 = Version.fromString("7.2");
        versions.setTarget(NodeType.host, version2, false);
        versions.setActive(NodeType.host, true);

        // Wanted version is changed to newest target for all nodes
        assertEquals(version2, minVersion(hostNodes.get(), OsVersion::wanted));
    }

    private Version minVersion(NodeList nodes, Function<OsVersion, Optional<Version>> versionField) {
        return nodes.asList().stream()
                    .map(Node::status)
                    .map(Status::osVersion)
                    .map(versionField)
                    .flatMap(Optional::stream)
                    .min(Comparator.naturalOrder())
                    .orElse(Version.emptyVersion);

    }

    private void setWantedVersion(List<Node> nodes, Version wantedVersion) {
        writeNode(nodes, node -> node.with(node.status().withOsVersion(node.status().osVersion().withWanted(Optional.of(wantedVersion)))));
    }

    private void setCurrentVersion(List<Node> nodes, Version currentVersion) {
        writeNode(nodes, node -> node.with(node.status().withOsVersion(node.status().osVersion().withCurrent(Optional.of(currentVersion)))));
    }

    private void writeNode(List<Node> nodes, UnaryOperator<Node> updateFunc) {
        for (var node : nodes) {
            try (var lock = tester.nodeRepository().lock(node)) {
                node = tester.nodeRepository().getNode(node.hostname()).get();
                tester.nodeRepository().write(updateFunc.apply(node), lock);
            }
        }
    }

    private void completeUpgradeOf(List<Node> nodes) {
        for (var node : nodes) {
            try (var lock = tester.nodeRepository().lock(node)) {
                node = tester.nodeRepository().getNode(node.hostname()).get();
                node = node.with(node.status().withOsVersion(node.status().osVersion().withCurrent(node.status().osVersion().wanted())));
                tester.nodeRepository().write(node, lock);
            }
        }
    }

}
