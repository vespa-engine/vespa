// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.NodeRepositoryTester;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.service.monitor.application.ConfigServerApplication;

import java.time.Duration;
import java.util.Optional;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
public class InfrastructureProvisionerTest {

    private final NodeRepositoryTester tester = new NodeRepositoryTester();

    private final Provisioner provisioner = mock(Provisioner.class);
    private final NodeRepository nodeRepository = tester.nodeRepository();
    private final InfrastructureVersions infrastructureVersions = mock(InfrastructureVersions.class);
    private final InfrastructureProvisioner infrastructureProvisioner = new InfrastructureProvisioner(
            provisioner, nodeRepository, infrastructureVersions, Duration.ofDays(99), new JobControl(nodeRepository.database()));

    @Test
    public void returns_version_if_usable_nodes_on_old_version() {
        Version target = Version.fromString("6.123.456");
        Version oldVersion = Version.fromString("6.122.333");
        when(infrastructureVersions.getTargetVersionFor(eq(NodeType.config))).thenReturn(Optional.of(target));

        addNode(1, Node.State.failed, Optional.of(oldVersion));
        addNode(2, Node.State.dirty, Optional.empty());
        addNode(3, Node.State.active, Optional.of(oldVersion));

        assertEquals(Optional.of(target), infrastructureProvisioner.getVersionToProvision(NodeType.config));
    }

    @Test
    public void returns_version_if_has_usable_nodes_without_version() {
        Version target = Version.fromString("6.123.456");
        Version oldVersion = Version.fromString("6.122.333");
        when(infrastructureVersions.getTargetVersionFor(eq(NodeType.config))).thenReturn(Optional.of(target));

        addNode(1, Node.State.failed, Optional.of(oldVersion));
        addNode(2, Node.State.ready, Optional.empty());
        addNode(3, Node.State.active, Optional.of(target));

        assertEquals(Optional.of(target), infrastructureProvisioner.getVersionToProvision(NodeType.config));
    }

    @Test
    public void returns_empty_if_usable_nodes_on_target_version() {
        Version target = Version.fromString("6.123.456");
        Version oldVersion = Version.fromString("6.122.333");
        when(infrastructureVersions.getTargetVersionFor(eq(NodeType.config))).thenReturn(Optional.of(target));

        addNode(1, Node.State.failed, Optional.of(oldVersion));
        addNode(2, Node.State.parked, Optional.of(target));
        addNode(3, Node.State.active, Optional.of(target));
        addNode(4, Node.State.inactive, Optional.of(target));
        addNode(5, Node.State.dirty, Optional.empty());

        assertEquals(Optional.empty(), infrastructureProvisioner.getVersionToProvision(NodeType.config));
    }

    @Test
    public void returns_empty_if_no_usable_nodes() {
        when(infrastructureVersions.getTargetVersionFor(eq(NodeType.config))).thenReturn(Optional.of(Version.fromString("6.123.456")));

        // No nodes in node repo
        assertEquals(Optional.empty(), infrastructureProvisioner.getVersionToProvision(NodeType.config));

        // Add nodes in non-provisionable states
        addNode(1, Node.State.dirty, Optional.empty());
        addNode(2, Node.State.failed, Optional.empty());
        assertEquals(Optional.empty(), infrastructureProvisioner.getVersionToProvision(NodeType.config));
    }

    @Test
    public void returns_empty_if_target_version_not_set() {
        when(infrastructureVersions.getTargetVersionFor(eq(NodeType.config))).thenReturn(Optional.empty());
        assertEquals(Optional.empty(), infrastructureProvisioner.getVersionToProvision(NodeType.config));
    }

    private Node addNode(int id, Node.State state, Optional<Version> wantedVespaVersion) {
        Node node = tester.addNode("id-" + id, "node-" + id, "default", NodeType.config);
        Optional<Node> nodeWithAllocation = wantedVespaVersion.map(version -> {
            ConfigServerApplication application = ConfigServerApplication.CONFIG_SERVER_APPLICATION;
            ClusterSpec clusterSpec = ClusterSpec.from(application.getClusterType(), application.getClusterId(), ClusterSpec.Group.from(0), version);
            ClusterMembership membership = ClusterMembership.from(clusterSpec, 1);
            Allocation allocation = new Allocation(application.getApplicationId(), membership, new Generation(0, 0), false);
            return node.with(allocation);
        });
        return nodeRepository.database().writeTo(state, nodeWithAllocation.orElse(node), Agent.system, Optional.empty());
    }
}
