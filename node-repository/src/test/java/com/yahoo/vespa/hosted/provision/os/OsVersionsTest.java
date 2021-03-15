// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.OsVersion;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
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
    private final ApplicationId infraApplication = ApplicationId.from("hosted-vespa", "infra", "default");

    @Test
    public void upgrade() {
        var versions = new OsVersions(tester.nodeRepository(), new DelegatingUpgrader(tester.nodeRepository(), Integer.MAX_VALUE));
        provisionInfraApplication(10);
        Supplier<NodeList> hostNodes = () -> tester.nodeRepository().nodes().list().nodeType(NodeType.host);

        // Upgrade OS
        assertTrue("No versions set", versions.readChange().targets().isEmpty());
        var version1 = Version.fromString("7.1");
        versions.setTarget(NodeType.host, version1, Optional.empty(), false);
        assertEquals(version1, versions.targetFor(NodeType.host).get());
        assertTrue("Per-node wanted OS version remains unset", hostNodes.get().stream().allMatch(node -> node.status().osVersion().wanted().isEmpty()));

        // One host upgrades to a later version outside the control of orchestration
        Node hostOnLaterVersion = hostNodes.get().first().get();
        setCurrentVersion(List.of(hostOnLaterVersion), Version.fromString("8.1"));

        // Upgrade OS again
        var version2 = Version.fromString("7.2");
        versions.setTarget(NodeType.host, version2, Optional.empty(), false);
        assertEquals(version2, versions.targetFor(NodeType.host).get());

        // Resume upgrade
        versions.resumeUpgradeOf(NodeType.host, true);
        NodeList allHosts = hostNodes.get();
        assertTrue("Wanted version is set", allHosts.stream()
                                                       .filter(node -> !node.equals(hostOnLaterVersion))
                                                       .allMatch(node -> node.status().osVersion().wanted().isPresent()));
        assertTrue("Wanted version is not set for host on later version",
                   allHosts.first().get().status().osVersion().wanted().isEmpty());

        // Halt upgrade
        versions.resumeUpgradeOf(NodeType.host, false);
        assertTrue("Wanted version is unset", hostNodes.get().stream()
                                                       .allMatch(node -> node.status().osVersion().wanted().isEmpty()));

        // Downgrading fails
        try {
            versions.setTarget(NodeType.host, version1, Optional.empty(), false);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {}

        // Forcing downgrade succeeds
        versions.setTarget(NodeType.host, version1, Optional.empty(), true);
        assertEquals(version1, versions.targetFor(NodeType.host).get());

        // Target can be removed
        versions.removeTarget(NodeType.host);
        assertFalse(versions.targetFor(NodeType.host).isPresent());
        assertTrue(hostNodes.get().stream().allMatch(node -> node.status().osVersion().wanted().isEmpty()));
    }

    @Test
    public void max_active_upgrades() {
        int totalNodes = 20;
        int maxActiveUpgrades = 5;
        var versions = new OsVersions(tester.nodeRepository(), new DelegatingUpgrader(tester.nodeRepository(), maxActiveUpgrades));
        provisionInfraApplication(totalNodes);
        Supplier<NodeList> hostNodes = () -> tester.nodeRepository().nodes().list().state(Node.State.active).hosts();

        // 5 nodes have no version. The other 15 are spread across different versions
        var hostNodesList = hostNodes.get().asList();
        for (int i = totalNodes - maxActiveUpgrades - 1; i >= 0; i--) {
            setCurrentVersion(List.of(hostNodesList.get(i)), new Version(7, 0, i));
        }

        // Deprovisioned hosts are not considered
        for (var host : tester.makeReadyNodes(10, "default", NodeType.host)) {
            tester.nodeRepository().nodes().fail(host.hostname(), Agent.system, OsVersions.class.getSimpleName());
            tester.nodeRepository().nodes().removeRecursively(host.hostname());
        }
        assertEquals(10, tester.nodeRepository().nodes().list(Node.State.deprovisioned).size());

        // Set target
        var version1 = Version.fromString("7.1");
        versions.setTarget(NodeType.host, version1, Optional.empty(), false);
        assertEquals(version1, versions.targetFor(NodeType.host).get());

        // Activate target
        for (int i = 0; i < totalNodes; i += maxActiveUpgrades) {
            versions.resumeUpgradeOf(NodeType.host, true);
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
        versions.resumeUpgradeOf(NodeType.host, true);
        assertEquals("All nodes upgraded", version1, minVersion(hostNodes.get(), OsVersion::current));
    }

    @Test
    public void newer_upgrade_aborts_upgrade_to_stale_version() {
        var versions = new OsVersions(tester.nodeRepository(), new DelegatingUpgrader(tester.nodeRepository(), Integer.MAX_VALUE));
        provisionInfraApplication(10);
        Supplier<NodeList> hostNodes = () -> tester.nodeRepository().nodes().list().hosts();

        // Some nodes are targeting an older version
        var version1 = Version.fromString("7.1");
        setWantedVersion(hostNodes.get().asList().subList(0, 5), version1);

        // Trigger upgrade to next version
        var version2 = Version.fromString("7.2");
        versions.setTarget(NodeType.host, version2, Optional.empty(), false);
        versions.resumeUpgradeOf(NodeType.host, true);

        // Wanted version is changed to newest target for all nodes
        assertEquals(version2, minVersion(hostNodes.get(), OsVersion::wanted));
    }

    @Test
    public void upgrade_by_retiring() {
        var versions = new OsVersions(tester.nodeRepository(), new RetiringUpgrader(tester.nodeRepository()));
        var clock = (ManualClock) tester.nodeRepository().clock();
        int hostCount = 10;
        // Provision hosts and children
        List<Node> hosts = provisionInfraApplication(hostCount);
        NodeResources resources = new NodeResources(2, 4, 8, 1);
        for (var host : hosts) {
            tester.makeReadyChildren(2, resources, host.hostname());
        }
        Supplier<NodeList> hostNodes = () -> tester.nodeRepository().nodes().list()
                                                   .hosts()
                                                   .not().state(Node.State.deprovisioned);

        // Target is set and upgrade started
        var version1 = Version.fromString("7.1");
        Duration totalBudget = Duration.ofHours(12);
        Duration nodeBudget = totalBudget.dividedBy(hostCount);
        versions.setTarget(NodeType.host, version1, Optional.of(totalBudget),false);
        versions.resumeUpgradeOf(NodeType.host, true);

        // One host is deprovisioning
        assertEquals(1, hostNodes.get().deprovisioning().size());

        // Nothing happens on next resume as first host has not spent its budget
        versions.resumeUpgradeOf(NodeType.host, true);
        NodeList nodesDeprovisioning = hostNodes.get().deprovisioning();
        assertEquals(1, nodesDeprovisioning.size());
        assertEquals(2, retiringChildrenOf(nodesDeprovisioning.asList().get(0)).size());

        // Budget has been spent and another host is retired
        clock.advance(nodeBudget);
        versions.resumeUpgradeOf(NodeType.host, true);
        assertEquals(2, hostNodes.get().deprovisioning().size());

        // Two nodes complete their upgrade by being reprovisioned
        completeUpgradeOf(hostNodes.get().deprovisioning().asList());
        assertEquals(2, hostNodes.get().onOsVersion(version1).size());
        // The remaining hosts complete their upgrade
        for (int i = 0; i < hostCount - 2; i++) {
            clock.advance(nodeBudget);
            versions.resumeUpgradeOf(NodeType.host, true);
            nodesDeprovisioning = hostNodes.get().deprovisioning();
            assertEquals(1, nodesDeprovisioning.size());
            assertEquals(2, retiringChildrenOf(nodesDeprovisioning.asList().get(0)).size());
            completeUpgradeOf(nodesDeprovisioning.asList());
        }

        // All hosts upgraded and none are deprovisioning
        assertEquals(hostCount, hostNodes.get().onOsVersion(version1).not().deprovisioning().size());
        assertEquals(hostCount, tester.nodeRepository().nodes().list().state(Node.State.deprovisioned).size());
        var lastRetiredAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);

        // Resuming after everything has upgraded does nothing
        versions.resumeUpgradeOf(NodeType.host, true);
        assertEquals(0, hostNodes.get().deprovisioning().size());

        // Another upgrade is triggered. Last retirement time is preserved
        clock.advance(Duration.ofDays(1));
        var version2 = Version.fromString("7.2");
        versions.setTarget(NodeType.host, version2, Optional.of(totalBudget), false);
        assertEquals(lastRetiredAt, versions.readChange().targets().get(NodeType.host).lastRetiredAt().get());
    }

    @Test
    public void upgrade_by_retiring_everything_at_once() {
        var versions = new OsVersions(tester.nodeRepository(), new RetiringUpgrader(tester.nodeRepository()));
        int hostCount = 3;
        provisionInfraApplication(hostCount, NodeType.confighost);
        Supplier<NodeList> hostNodes = () -> tester.nodeRepository().nodes().list()
                                                   .nodeType(NodeType.confighost)
                                                   .not().state(Node.State.deprovisioned);

        // Target is set with zero budget and upgrade started
        var version1 = Version.fromString("7.1");
        versions.setTarget(NodeType.confighost, version1, Optional.of(Duration.ZERO),false);
        for (int i = 0; i < hostCount; i++) {
            versions.resumeUpgradeOf(NodeType.confighost, true);
        }

        // All hosts are deprovisioning
        assertEquals(hostCount, hostNodes.get().deprovisioning().size());
        // Nodes complete their upgrade by being reprovisioned
        completeUpgradeOf(hostNodes.get().deprovisioning().asList(), NodeType.confighost);
        assertEquals(hostCount, hostNodes.get().onOsVersion(version1).size());
    }

    private NodeList retiringChildrenOf(Node parent) {
        return tester.nodeRepository().nodes().list()
                     .childrenOf(parent)
                     .matching(child -> child.status().wantToRetire() &&
                                        child.status().wantToDeprovision());
    }

    private List<Node> provisionInfraApplication(int nodeCount) {
        return provisionInfraApplication(nodeCount, NodeType.host);
    }

    private List<Node> provisionInfraApplication(int nodeCount, NodeType nodeType) {
        var nodes = tester.makeReadyNodes(nodeCount, "default", nodeType);
        tester.prepareAndActivateInfraApplication(infraApplication, nodeType);
        return nodes.stream()
                    .map(Node::hostname)
                    .flatMap(hostname -> tester.nodeRepository().nodes().node(hostname).stream())
                    .collect(Collectors.toList());
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
        tester.patchNodes(nodes, node -> node.with(node.status().withOsVersion(node.status().osVersion().withWanted(Optional.of(wantedVersion)))));
    }

    private void setCurrentVersion(List<Node> nodes, Version currentVersion) {
        tester.patchNodes(nodes, node -> node.with(node.status().withOsVersion(node.status().osVersion().withCurrent(Optional.of(currentVersion)))));
    }

    private void completeUpgradeOf(List<Node> nodes) {
        completeUpgradeOf(nodes, NodeType.host);
    }

    private void completeUpgradeOf(List<Node> nodes, NodeType nodeType) {
        // Complete upgrade by deprovisioning stale hosts and provisioning new ones
        tester.patchNodes(nodes, (node) -> {
            Optional<Version> wantedOsVersion = node.status().osVersion().wanted();
            if (node.status().wantToDeprovision()) {
                // Complete upgrade by deprovisioning stale hosts and provisioning new ones
                tester.nodeRepository().nodes().park(node.hostname(), false, Agent.system,
                                                     OsVersionsTest.class.getSimpleName());
                tester.nodeRepository().nodes().removeRecursively(node.hostname());
                node = provisionInfraApplication(1, nodeType).get(0);
            }
            return node.with(node.status().withOsVersion(node.status().osVersion().withCurrent(wantedOsVersion)));
        });
    }

}
