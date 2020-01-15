// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

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

        // Some nodes have reported current version
        setCurrentVersion(hostNodes.get().asList().subList(0, 2), Version.fromString("7.0"));

        // Set target
        var version1 = Version.fromString("7.1");
        versions.setTarget(NodeType.host, version1, false);
        assertEquals(version1, versions.targetFor(NodeType.host).get());

        // Activate target
        for (int i = 0; i < totalNodes; i += maxActiveUpgrades) {
            versions.setActive(NodeType.host, true);
            var nodesUpgrading = hostNodes.get().changingOsVersion();
            assertEquals("Target is changed for a subset of nodes", maxActiveUpgrades, nodesUpgrading.size());
            assertEquals("Wanted version is set for nodes upgrading", version1,
                         nodesUpgrading.stream()
                                       .map(node -> node.status().osVersion().wanted().get())
                                       .min(Comparator.naturalOrder()).get());
            completeUpgradeOf(nodesUpgrading.asList());
        }

        // Activating again after all nodes have upgraded does nothing
        versions.setActive(NodeType.host, true);
        assertEquals(version1, hostNodes.get().stream()
                                        .map(n -> n.status().osVersion().current().get())
                                        .min(Comparator.naturalOrder()).get());
    }

    private void setCurrentVersion(List<Node> nodes, Version currentVersion) {
        for (var node : nodes) {
            try (var lock = tester.nodeRepository().lock(node)) {
                node = tester.nodeRepository().getNode(node.hostname()).get();
                node = node.with(node.status().withOsVersion(node.status().osVersion().withCurrent(Optional.of(currentVersion))));
                tester.nodeRepository().write(node, lock);
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
