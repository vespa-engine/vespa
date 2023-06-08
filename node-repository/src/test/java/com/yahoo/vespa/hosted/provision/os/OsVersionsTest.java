// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.OsVersion;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
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
    private final ApplicationId infraApplication = ApplicationId.from("hosted-vespa", "infra", "default");

    @Test
    public void upgrade() {
        var versions = new OsVersions(tester.nodeRepository());
        provisionInfraApplication(10);
        Supplier<NodeList> hostNodes = () -> tester.nodeRepository().nodes().list().nodeType(NodeType.host);

        // Upgrade OS
        assertTrue("No versions set", versions.readChange().targets().isEmpty());
        var version1 = Version.fromString("7.1");
        versions.setTarget(NodeType.host, version1, false);
        assertEquals(version1, versions.targetFor(NodeType.host).get());
        assertTrue("Per-node wanted OS version remains unset", hostNodes.get().stream().allMatch(node -> node.status().osVersion().wanted().isEmpty()));

        // One host upgrades to a later version outside the control of orchestration
        Node hostOnLaterVersion = hostNodes.get().first().get();
        setCurrentVersion(List.of(hostOnLaterVersion), Version.fromString("8.1"));

        // Upgrade OS again
        var version2 = Version.fromString("7.2");
        versions.setTarget(NodeType.host, version2, false);
        assertEquals(version2, versions.targetFor(NodeType.host).get());

        // Resume upgrade
        versions.resumeUpgradeOf(NodeType.host, true);
        NodeList allHosts = hostNodes.get();
        assertTrue("Wanted version is set", allHosts.except(hostOnLaterVersion).stream()
                                                    .allMatch(node -> node.status().osVersion().wanted().isPresent()));
        assertTrue("Wanted version is not set for host on later version",
                   allHosts.first().get().status().osVersion().wanted().isEmpty());

        // Halt upgrade
        versions.resumeUpgradeOf(NodeType.host, false);
        assertTrue("Wanted version is unset", hostNodes.get().stream()
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
    public void max_active_upgrades() {
        int totalNodes = 20;
        int maxActiveUpgrades = 5;
        var versions = new OsVersions(tester.nodeRepository(), Cloud.defaultCloud());
        setMaxActiveUpgrades(maxActiveUpgrades);
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
        versions.setTarget(NodeType.host, version1, false);
        assertEquals(version1, versions.targetFor(NodeType.host).get());

        // Activate target
        for (int i = 0; i < totalNodes; i += maxActiveUpgrades) {
            versions.resumeUpgradeOf(NodeType.host, true);
            NodeList nodes = hostNodes.get();
            NodeList nodesUpgrading = nodes.changingOsVersion();
            assertEquals("Target is changed for a subset of nodes", maxActiveUpgrades, nodesUpgrading.size());
            assertEquals("Wanted version is set for nodes upgrading", version1,
                         minVersion(nodesUpgrading, OsVersion::wanted));
            NodeList nodesOnLowestVersion = nodes.sortedBy(Comparator.comparing(node -> node.status().osVersion().current().orElse(Version.emptyVersion)))
                                                 .first(maxActiveUpgrades);
            assertEquals("Nodes on lowest version are told to upgrade",
                         nodesUpgrading.hostnames(),
                         nodesOnLowestVersion.hostnames());
            completeReprovisionOf(nodesUpgrading.asList());
        }

        // Activating again after all nodes have upgraded does nothing
        versions.resumeUpgradeOf(NodeType.host, true);
        assertEquals("All nodes upgraded", version1, minVersion(hostNodes.get(), OsVersion::current));
    }

    @Test
    public void newer_upgrade_aborts_upgrade_to_stale_version() {
        var versions = new OsVersions(tester.nodeRepository());
        provisionInfraApplication(10);
        Supplier<NodeList> hostNodes = () -> tester.nodeRepository().nodes().list().hosts();

        // Some nodes are targeting an older version
        var version1 = Version.fromString("7.1");
        setWantedVersion(hostNodes.get().asList().subList(0, 5), version1);

        // Trigger upgrade to next version
        var version2 = Version.fromString("7.2");
        versions.setTarget(NodeType.host, version2, false);
        versions.resumeUpgradeOf(NodeType.host, true);

        // Wanted version is changed to newest target for all nodes
        assertEquals(version2, minVersion(hostNodes.get(), OsVersion::wanted));
    }

    @Test
    public void upgrade_by_retiring() {
        int maxActiveUpgrades = 2;
        var versions = new OsVersions(tester.nodeRepository(), Cloud.builder().dynamicProvisioning(true).build());
        setMaxActiveUpgrades(maxActiveUpgrades);
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
        versions.setTarget(NodeType.host, version1, false);
        versions.resumeUpgradeOf(NodeType.host, true);

        // First batch of hosts starts deprovisioning
        assertEquals(maxActiveUpgrades, hostNodes.get().deprovisioning().size());

        // Deprovisioning is rescheduled if some other agent resets wantToRetire/wantToDeprovision
        Node host0 = hostNodes.get().deprovisioning().first().get();
        tester.patchNode(host0, (h) -> h.withWantToRetire(false, false, Agent.system,
                                                          tester.nodeRepository().clock().instant()));
        versions.resumeUpgradeOf(NodeType.host, true);
        assertTrue(hostNodes.get().deprovisioning().node(host0.hostname()).isPresent());

        // Nothing happens on next resume as first batch has not completed upgrade
        versions.resumeUpgradeOf(NodeType.host, true);
        NodeList nodesDeprovisioning = hostNodes.get().deprovisioning();
        assertEquals(maxActiveUpgrades, nodesDeprovisioning.size());
        assertEquals(2, deprovisioningChildrenOf(nodesDeprovisioning.asList().get(0)).size());
        completeReprovisionOf(nodesDeprovisioning.asList());

        // Remaining hosts complete upgrades one by one
        for (int i = 0; i < hostCount - 2; i += maxActiveUpgrades) {
            versions.resumeUpgradeOf(NodeType.host, true);
            nodesDeprovisioning = hostNodes.get().deprovisioning();
            assertEquals(maxActiveUpgrades, nodesDeprovisioning.size());
            assertEquals(2, deprovisioningChildrenOf(nodesDeprovisioning.asList().get(0)).size());
            completeReprovisionOf(nodesDeprovisioning.asList());
        }

        // All hosts upgraded and none are deprovisioning
        assertEquals(hostCount, hostNodes.get().onOsVersion(version1).not().deprovisioning().size());
        assertEquals(hostCount, tester.nodeRepository().nodes().list(Node.State.deprovisioned).size());

        // Resuming after everything has upgraded does nothing
        versions.resumeUpgradeOf(NodeType.host, true);
        assertEquals(0, hostNodes.get().deprovisioning().size());
    }

    @Test
    public void upgrade_by_retiring_everything_at_once() {
        var versions = new OsVersions(tester.nodeRepository(), Cloud.builder().dynamicProvisioning(true).build());
        setMaxActiveUpgrades(Integer.MAX_VALUE);
        int hostCount = 3;
        provisionInfraApplication(hostCount, infraApplication, NodeType.host);
        Supplier<NodeList> hostNodes = () -> tester.nodeRepository().nodes().list()
                                                   .nodeType(NodeType.host)
                                                   .not().state(Node.State.deprovisioned);

        // Target is set and upgrade started
        var version1 = Version.fromString("7.1");
        versions.setTarget(NodeType.host, version1, false);
        for (int i = 0; i < hostCount; i++) {
            versions.resumeUpgradeOf(NodeType.host, true);
        }

        // All hosts are deprovisioning
        assertEquals(hostCount, hostNodes.get().deprovisioning().size());
        // Nodes complete their upgrade by being reprovisioned
        completeReprovisionOf(hostNodes.get().deprovisioning().asList(), NodeType.host);
        assertEquals(hostCount, hostNodes.get().onOsVersion(version1).size());
    }

    @Test
    public void upgrade_by_rebuilding() {
        var versions = new OsVersions(tester.nodeRepository(), Cloud.defaultCloud());
        setMaxActiveUpgrades(1);
        int hostCount = 10;
        provisionInfraApplication(hostCount + 1);
        Supplier<NodeList> hostNodes = () -> tester.nodeRepository().nodes().list().nodeType(NodeType.host);

        // All hosts upgrade to first version. Upgrades are delegated
        var version0 = Version.fromString("7.0");
        versions.setTarget(NodeType.host, version0, false);
        setCurrentVersion(hostNodes.get().asList(), version0);

        // One host is failed out
        Node failedHost = tester.nodeRepository().nodes().fail(hostNodes.get().first().get().hostname(),
                                                               Agent.system, getClass().getSimpleName());

        // Target is set for new major version. Upgrade mechanism switches to rebuilding
        var version1 = Version.fromString("8.0");
        versions.setTarget(NodeType.host, version1, false);
        versions.resumeUpgradeOf(NodeType.host, true);

        // One host starts rebuilding
        assertEquals(1, hostNodes.get().rebuilding(false).size());

        // We cannot rebuild another host until the current one is done
        versions.resumeUpgradeOf(NodeType.host, true);
        NodeList hostsRebuilding = hostNodes.get().rebuilding(false);
        assertEquals(1, hostsRebuilding.size());
        completeRebuildOf(hostsRebuilding.asList(), NodeType.host);
        assertEquals(1, hostNodes.get().onOsVersion(version1).size());

        // Second host is rebuilt
        versions.resumeUpgradeOf(NodeType.host, true);
        completeRebuildOf(hostNodes.get().rebuilding(false).asList(), NodeType.host);
        assertEquals(2, hostNodes.get().onOsVersion(version1).size());

        // The remaining hosts complete their upgrade
        for (int i = 0; i < hostCount - 2; i++) {
            versions.resumeUpgradeOf(NodeType.host, true);
            hostsRebuilding = hostNodes.get().rebuilding(false);
            assertEquals(1, hostsRebuilding.size());
            completeRebuildOf(hostsRebuilding.asList(), NodeType.host);
        }

        // All hosts upgraded and none are rebuilding
        assertEquals(hostCount, hostNodes.get().onOsVersion(version1).not().rebuilding(false).size());
        assertEquals(hostCount, tester.nodeRepository().nodes().list(Node.State.active).size());

        // Resuming after everything has upgraded has no effect
        versions.resumeUpgradeOf(NodeType.host, true);
        assertEquals(0, hostNodes.get().rebuilding(false).size());

        // Next version is within same major. Upgrade mechanism switches to delegated
        setMaxActiveUpgrades(100);
        var version2 = Version.fromString("8.1");
        versions.setTarget(NodeType.host, version2, false);
        versions.resumeUpgradeOf(NodeType.host, true);
        NodeList nonFailingHosts = hostNodes.get().except(failedHost);
        assertTrue("Wanted version is set", nonFailingHosts.stream()
                                                           .allMatch(node -> node.status().osVersion().wanted().isPresent()));
        setCurrentVersion(nonFailingHosts.asList(), version2);
        assertEquals(10, hostNodes.get().except(failedHost).onOsVersion(version2).size());

        // Failed host is reactivated
        Node reactivatedHost = tester.nodeRepository().nodes().reactivate(failedHost.hostname(), Agent.system, getClass().getSimpleName());
        assertEquals(version0, reactivatedHost.status().osVersion().current().get());

        // Resuming upgrades reactivated host. Upgrade mechanism switches to rebuilding
        versions.resumeUpgradeOf(NodeType.host, true);
        hostsRebuilding = hostNodes.get().rebuilding(false);
        assertEquals(List.of(reactivatedHost), hostsRebuilding.asList());
        completeRebuildOf(hostsRebuilding.asList(), NodeType.host);
    }

    @Test
    public void upgrade_by_soft_rebuilding() {
        int maxRebuilds = 3;
        int hostCount = 12;
        boolean softRebuild = true;

        setMaxActiveUpgrades(maxRebuilds);
        var versions = new OsVersions(tester.nodeRepository(), Cloud.builder()
                                                                    .dynamicProvisioning(true)
                                                                    .name(CloudName.AWS)
                                                                    .account(CloudAccount.from("000000000000"))
                                                                    .build());

        provisionInfraApplication(hostCount, infraApplication, NodeType.host, NodeResources.StorageType.remote, NodeResources.Architecture.x86_64);
        Supplier<NodeList> hostNodes = () -> tester.nodeRepository().nodes().list().nodeType(NodeType.host);

        // New target is set
        int hostsRebuilt = 0;
        var version1 = Version.fromString("8.0");
        versions.setTarget(NodeType.host, version1, false);
        versions.resumeUpgradeOf(NodeType.host, true);

        // First batch of hosts start rebuilding
        assertEquals(maxRebuilds, hostNodes.get().rebuilding(softRebuild).size());

        // We cannot rebuild another host yet
        versions.resumeUpgradeOf(NodeType.host, true);
        NodeList hostsRebuilding = hostNodes.get().rebuilding(softRebuild);
        assertEquals(maxRebuilds, hostsRebuilding.size());
        completeSoftRebuildOf(hostsRebuilding.asList());
        assertEquals(hostsRebuilt += maxRebuilds, hostNodes.get().onOsVersion(version1).size());

        // Another batch is rebuilt
        versions.resumeUpgradeOf(NodeType.host, true);
        completeSoftRebuildOf(hostNodes.get().rebuilding(softRebuild).asList());
        assertEquals(hostsRebuilt += maxRebuilds, hostsRebuilt);

        // The remaining batches complete their upgrade
        for (int i = 0; i < (hostCount - hostsRebuilt) / maxRebuilds; i++) {
            versions.resumeUpgradeOf(NodeType.host, true);
            hostsRebuilding = hostNodes.get().rebuilding(softRebuild);
            assertEquals(maxRebuilds, hostsRebuilding.size());
            completeSoftRebuildOf(hostsRebuilding.asList());
        }

        // All hosts upgraded and none are rebuilding
        assertEquals(hostCount, hostNodes.get().onOsVersion(version1).not().rebuilding(softRebuild).size());

        // Resuming after everything has upgraded has no effect
        versions.resumeUpgradeOf(NodeType.host, true);
        assertEquals(0, hostNodes.get().rebuilding(softRebuild).size());
    }

    @Test
    public void upgrade_by_rebuilding_multiple_host_types() {
        setMaxActiveUpgrades(1);
        var versions = new OsVersions(tester.nodeRepository(), Cloud.defaultCloud());
        int hostCount = 3;
        provisionInfraApplication(hostCount, infraApplication, NodeType.host);
        provisionInfraApplication(hostCount, ApplicationId.from("hosted-vespa", "confighost", "default"), NodeType.confighost);
        Supplier<NodeList> hosts = () -> tester.nodeRepository().nodes().list().nodeType(NodeType.host,
                                                                                         NodeType.confighost);

        // All hosts upgrade to first version. Upgrades are delegated
        var version0 = Version.fromString("7.0");
        versions.setTarget(NodeType.host, version0, false);
        versions.setTarget(NodeType.confighost, version0, false);
        setCurrentVersion(hosts.get().asList(), version0);

        // Target is set for new major version
        var version1 = Version.fromString("8.0");
        versions.setTarget(NodeType.host, version1, false);
        versions.setTarget(NodeType.confighost, version1, false);

        // One  host of each type is upgraded
        for (int i = 0; i < hostCount; i++) {
            versions.resumeUpgradeOf(NodeType.host, true);
            versions.resumeUpgradeOf(NodeType.confighost, true);
            NodeList hostsRebuilding = hosts.get().rebuilding(false);
            assertEquals(2, hostsRebuilding.size());
            completeRebuildOf(hostsRebuilding.nodeType(NodeType.host).asList(), NodeType.host);
            completeRebuildOf(hostsRebuilding.nodeType(NodeType.confighost).asList(), NodeType.confighost);
        }
        assertEquals("All hosts upgraded", hostCount * 2, hosts.get().onOsVersion(version1).size());
    }

    @Test
    public void upgrade_by_rebuilding_is_limited_by_stateful_clusters() {
        setMaxActiveUpgrades(3);
        var versions = new OsVersions(tester.nodeRepository(), Cloud.defaultCloud());
        int hostCount = 5;
        ApplicationId app1 = ApplicationId.from("t1", "a1", "i1");
        ApplicationId app2 = ApplicationId.from("t2", "a2", "i2");
        provisionInfraApplication(hostCount, infraApplication, NodeType.host);
        deployApplication(app1);
        deployApplication(app2);
        Supplier<NodeList> hosts = () -> tester.nodeRepository().nodes().list().nodeType(NodeType.host);

        // All hosts are on initial version
        var version0 = Version.fromString("7.0");
        versions.setTarget(NodeType.host, version0, false);
        setCurrentVersion(hosts.get().asList(), version0);

        // Target is set for new major version
        var version1 = Version.fromString("8.0");
        versions.setTarget(NodeType.host, version1, false);

        // Upgrades 1 host per stateful cluster and 1 empty host
        versions.resumeUpgradeOf(NodeType.host, true);
        NodeList allNodes = tester.nodeRepository().nodes().list();
        List<Node> hostsRebuilding = allNodes.nodeType(NodeType.host)
                                             .rebuilding(false)
                                             .sortedBy(Comparator.comparing(Node::hostname))
                                             .asList();
        List<Optional<ApplicationId>> owners = List.of(Optional.of(app1), Optional.of(app2), Optional.empty());
        assertEquals(3, hostsRebuilding.size());
        for (int i = 0; i < hostsRebuilding.size(); i++) {
            Optional<ApplicationId> owner = owners.get(i);
            List<Node> retiringChildren = allNodes.childrenOf(hostsRebuilding.get(i)).retiring().asList();
            assertEquals("Retiring children of " + hostsRebuilding.get(i) + ": " + retiringChildren, owner.isPresent() ? 1 : 0, retiringChildren.size());
            assertEquals("Rebuilding host of " + owner.map(ApplicationId::toString)
                                                      .orElse("no application"),
                         owner,
                         retiringChildren.stream()
                                         .findFirst()
                                         .flatMap(Node::allocation)
                                         .map(Allocation::owner));
        }

        // Replace any retired nodes
        replaceNodes(app1);
        replaceNodes(app2);

        // Complete rebuild
        completeRebuildOf(hostsRebuilding, NodeType.host);
        assertEquals(3, hosts.get().onOsVersion(version1).size());

        // Both applications have moved their nodes to the hosts on old OS version
        allNodes = tester.nodeRepository().nodes().list();
        NodeList hostsOnOldVersion = allNodes.onOsVersion(version0);
        assertEquals(2, hostsOnOldVersion.size());
        for (var host : hostsOnOldVersion) {
            assertEquals(1, allNodes.childrenOf(host).owner(app1).size());
            assertEquals(1, allNodes.childrenOf(host).owner(app2).size());
        }

        // Since both applications now occupy all remaining hosts, we can only upgrade 1 at a time
        for (int i = 0; i < hostsOnOldVersion.size(); i++) {
            versions.resumeUpgradeOf(NodeType.host, true);
            hostsRebuilding = hosts.get().rebuilding(false).asList();
            assertEquals(1, hostsRebuilding.size());
            replaceNodes(app1);
            replaceNodes(app2);
            completeRebuildOf(hostsRebuilding, NodeType.host);
        }

        // Resuming upgrade has no effect as all hosts have upgraded
        versions.resumeUpgradeOf(NodeType.host, true);
        NodeList allHosts = hosts.get();
        assertEquals(0, allHosts.rebuilding(false).size());
        assertEquals(allHosts.size(), allHosts.onOsVersion(version1).size());
    }

    @Test
    public void upgrade_by_rebuilding_limits_infrastructure_host() {
        int hostCount = 3;
        setMaxActiveUpgrades(hostCount);
        var versions = new OsVersions(tester.nodeRepository(), Cloud.defaultCloud());
        provisionInfraApplication(hostCount, infraApplication, NodeType.proxyhost);
        Supplier<NodeList> hosts = () -> tester.nodeRepository().nodes().list().nodeType(NodeType.proxyhost);

        // All hosts are on initial version
        var version0 = Version.fromString("7.0");
        versions.setTarget(NodeType.proxyhost, version0, false);
        setCurrentVersion(hosts.get().asList(), version0);

        // Target is set for new major version
        var version1 = Version.fromString("8.0");
        versions.setTarget(NodeType.proxyhost, version1, false);

        // Upgrades 1 infrastructure host at a time
        for (int i = 0; i < hostCount; i++) {
            versions.resumeUpgradeOf(NodeType.proxyhost, true);
            List<Node> hostsRebuilding = hosts.get().rebuilding(false).asList();
            assertEquals(1, hostsRebuilding.size());
            completeRebuildOf(hostsRebuilding, NodeType.proxyhost);
        }
    }

    private void setMaxActiveUpgrades(int max) {
        tester.flagSource().withIntFlag(PermanentFlags.MAX_OS_UPGRADES.id(), max);
    }

    private void deployApplication(ApplicationId application) {
        ClusterSpec contentSpec = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("content1")).vespaVersion("7").build();
        List<HostSpec> hostSpecs = tester.prepare(application, contentSpec, 2, 1, new NodeResources(4, 8, 100, 0.3));
        tester.activate(application, hostSpecs);
    }

    private void replaceNodes(ApplicationId application) {
        // Deploy to retire nodes
        deployApplication(application);
        NodeList retired = tester.nodeRepository().nodes().list().owner(application).retired();
        assertFalse("At least one node is retired", retired.isEmpty());
        tester.nodeRepository().nodes().setRemovable(retired, false);

        // Redeploy to deactivate removable nodes and allocate new ones
        deployApplication(application);
        tester.nodeRepository().nodes().list(Node.State.inactive).owner(application)
              .forEach(node -> tester.nodeRepository().nodes().removeRecursively(node, true));
    }

    private NodeList deprovisioningChildrenOf(Node parent) {
        return tester.nodeRepository().nodes().list()
                     .childrenOf(parent)
                     .deprovisioning();
    }

    private List<Node> provisionInfraApplication(int nodeCount) {
        return provisionInfraApplication(nodeCount, infraApplication, NodeType.host);
    }

    private List<Node> provisionInfraApplication(int nodeCount, ApplicationId application,
                                                 NodeType nodeType) {
        return provisionInfraApplication(nodeCount, application, nodeType, NodeResources.StorageType.local);
    }

    private List<Node> provisionInfraApplication(int nodeCount, ApplicationId application,
                                                 NodeType nodeType, NodeResources.StorageType storageType) {
        return provisionInfraApplication(nodeCount, application, nodeType, storageType, NodeResources.Architecture.x86_64);
    }

    private List<Node> provisionInfraApplication(int nodeCount, ApplicationId application, NodeType nodeType,
                                                 NodeResources.StorageType storageType, NodeResources.Architecture architecture) {
        var nodes = tester.makeReadyNodes(nodeCount, new NodeResources(48, 128, 2000, 10,
                                                                       NodeResources.DiskSpeed.fast, storageType, architecture),
                                          nodeType, 10);
        tester.prepareAndActivateInfraApplication(application, nodeType);
        tester.clock().advance(Duration.ofDays(1).plusSeconds(1)); // Let grace period pass
        return nodes.stream()
                    .map(Node::hostname)
                    .flatMap(hostname -> tester.nodeRepository().nodes().node(hostname).stream())
                    .toList();

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

    private void completeReprovisionOf(List<Node> nodes) {
        completeReprovisionOf(nodes, NodeType.host);
    }

    private void completeReprovisionOf(List<Node> nodes, NodeType nodeType) {
        // Complete upgrade by deprovisioning stale hosts and provisioning new ones
        tester.patchNodes(nodes, (node) -> {
            Optional<Version> wantedOsVersion = node.status().osVersion().wanted();
            if (node.status().wantToDeprovision()) {
                ApplicationId application = node.allocation().get().owner();
                tester.nodeRepository().nodes().park(node.hostname(), true, Agent.system,
                                                     getClass().getSimpleName());
                tester.nodeRepository().nodes().removeRecursively(node.hostname());
                node = provisionInfraApplication(1, application, nodeType).get(0);
            }
            return node.with(node.status().withOsVersion(node.status().osVersion().withCurrent(wantedOsVersion)));
        });
    }

    private void completeRebuildOf(List<Node> nodes, NodeType nodeType) {
        // Complete upgrade by rebuilding stale hosts
        tester.patchNodes(nodes, (node) -> {
            Optional<Version> wantedOsVersion = node.status().osVersion().wanted();
            if (node.status().wantToRebuild()) {
                ApplicationId application = node.allocation().get().owner();
                tester.nodeRepository().nodes().park(node.hostname(), true, Agent.system,
                                                     getClass().getSimpleName());
                tester.nodeRepository().nodes().removeRecursively(node.hostname());
                Node newNode = Node.create(node.id(), node.ipConfig(), node.hostname(), node.flavor(), node.type())
                                   .build();
                node = tester.nodeRepository().nodes().addNodes(List.of(newNode), Agent.system).get(0);
                node = tester.move(Node.State.ready, node);
                tester.prepareAndActivateInfraApplication(application, nodeType);
                node = tester.nodeRepository().nodes().node(node.hostname()).get();
            }
            return node.with(node.status().withOsVersion(node.status().osVersion().withCurrent(wantedOsVersion)));
        });
    }

    private void completeSoftRebuildOf(List<Node> nodes) {
        tester.patchNodes(nodes, (node) -> {
            Optional<Version> wantedOsVersion = node.status().osVersion().wanted();
            assertFalse(node + " is not retiring", node.status().wantToRetire());
            assertTrue(node + " is rebuilding", node.status().wantToRebuild());
            node = node.withWantToRetire(false, false, false, false, Agent.system,
                                         tester.clock().instant());
            return node.with(node.status().withOsVersion(node.status().osVersion().withCurrent(wantedOsVersion)));
        });
    }

}
