// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.IP;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * @author smorgrav
 */
public class DockerHostCapacityTest {

    private final HostResourcesCalculator hostResourcesCalculator = mock(HostResourcesCalculator.class);
    private DockerHostCapacity capacity;
    private List<Node> nodes;
    private Node host1, host2, host3;
    private final NodeResources resources1 = new NodeResources(1, 3, 2, 1.5);
    private final NodeResources resources2 = new NodeResources(2, 4, 4, 0.5);

    @Before
    public void setup() {
        doAnswer(invocation -> invocation.getArguments()[1]).when(hostResourcesCalculator).availableCapacityOf(any(), any());

        // Create flavors
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("host", "docker", "docker2");

        // Create three docker hosts
        host1 = Node.create("host1", new IP.Config(Set.of("::1"), generateIPs(2, 4)), "host1", Optional.empty(), Optional.empty(), nodeFlavors.getFlavorOrThrow("host"), Optional.empty(), NodeType.host);
        host2 = Node.create("host2", new IP.Config(Set.of("::11"), generateIPs(12, 3)), "host2", Optional.empty(), Optional.empty(), nodeFlavors.getFlavorOrThrow("host"), Optional.empty(), NodeType.host);
        host3 = Node.create("host3", new IP.Config(Set.of("::21"), generateIPs(22, 1)), "host3", Optional.empty(), Optional.empty(), nodeFlavors.getFlavorOrThrow("host"), Optional.empty(), NodeType.host);

        // Add two containers to host1
        var nodeA = Node.createDockerNode(Set.of("::2"), "nodeA", "host1", resources1, NodeType.tenant);
        var nodeB = Node.createDockerNode(Set.of("::3"), "nodeB", "host1", resources1, NodeType.tenant);

        // Add two containers to host 2 (same as host 1)
        var nodeC = Node.createDockerNode(Set.of("::12"), "nodeC", "host2", resources1, NodeType.tenant);
        var nodeD = Node.createDockerNode(Set.of("::13"), "nodeD", "host2", resources1, NodeType.tenant);

        // Add a larger container to host3
        var nodeE = Node.createDockerNode(Set.of("::22"), "nodeE", "host3", resources2, NodeType.tenant);

        // init docker host capacity
        nodes = new ArrayList<>(List.of(host1, host2, host3, nodeA, nodeB, nodeC, nodeD, nodeE));
        capacity = new DockerHostCapacity(new LockedNodeList(nodes, () -> {}), hostResourcesCalculator);
    }

    @Test
    public void hasCapacity() {
        assertTrue(capacity.hasCapacity(host1, resources1));
        assertTrue(capacity.hasCapacity(host1, resources2));
        assertTrue(capacity.hasCapacity(host2, resources1));
        assertTrue(capacity.hasCapacity(host2, resources2));
        assertFalse(capacity.hasCapacity(host3, resources1));  // No ip available
        assertFalse(capacity.hasCapacity(host3, resources2)); // No ip available

        // Add a new node to host1 to deplete the memory resource
        Node nodeF = Node.createDockerNode(Set.of("::6"), "nodeF", "host1", resources1, NodeType.tenant);
        nodes.add(nodeF);
        capacity = new DockerHostCapacity(new LockedNodeList(nodes, () -> {}), hostResourcesCalculator);
        assertFalse(capacity.hasCapacity(host1, resources1));
        assertFalse(capacity.hasCapacity(host1, resources2));
    }

    @Test
    public void freeIPs() {
        assertEquals(2, capacity.freeIPs(host1));
        assertEquals(1, capacity.freeIPs(host2));
        assertEquals(0, capacity.freeIPs(host3));
    }

    @Test
    public void freeCapacityOf() {
        assertEquals(new NodeResources(5, 4, 8, 2, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote),
                     capacity.freeCapacityOf(host1, false));
        assertEquals(new NodeResources(5, 6, 8, 4.5, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote),
                     capacity.freeCapacityOf(host3, false));

        doAnswer(invocation -> {
            NodeResources totalHostResources = (NodeResources) invocation.getArguments()[1];
            return totalHostResources.subtract(new NodeResources(1, 2, 3, 0.5, NodeResources.DiskSpeed.any));
        }).when(hostResourcesCalculator).availableCapacityOf(any(), any());

        assertEquals(new NodeResources(4, 2, 5, 1.5, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote),
                     capacity.freeCapacityOf(host1, false));
        assertEquals(new NodeResources(4, 4, 5, 4, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote),
                     capacity.freeCapacityOf(host3, false));
    }

    @Test
    public void devhostCapacityTest() {
        // Dev host can assign both configserver and tenant containers.

        var nodeFlavors = FlavorConfigBuilder.createDummies("devhost", "container");
        var devHost = Node.create("devhost", new IP.Config(Set.of("::1"), generateIPs(2, 10)), "devhost", Optional.empty(), Optional.empty(), nodeFlavors.getFlavorOrThrow("devhost"), Optional.empty(), NodeType.devhost);

        var cfg = Node.createDockerNode(Set.of("::2"), "cfg", "devhost", resources1, NodeType.config);

        var nodes = new ArrayList<>(List.of(cfg));
        var capacity = new DockerHostCapacity(new LockedNodeList(nodes, () -> {}), hostResourcesCalculator);
        assertTrue(capacity.hasCapacity(devHost, resources1));

        var container1 = Node.createDockerNode(Set.of("::3"), "container1", "devhost", resources1, NodeType.tenant);
        nodes = new ArrayList<>(List.of(cfg, container1));
        capacity = new DockerHostCapacity(new LockedNodeList(nodes, () -> {}), hostResourcesCalculator);
        assertFalse(capacity.hasCapacity(devHost, resources1));

    }

    private Set<String> generateIPs(int start, int count) {
        // Allow 4 containers
        Set<String> ipAddressPool = new LinkedHashSet<>();
        for (int i = start; i < (start + count); i++) {
            ipAddressPool.add("::" + i);
        }
        return ipAddressPool;
    }

}
