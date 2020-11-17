// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
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
public class HostCapacityTest {

    private final HostResourcesCalculator hostResourcesCalculator = mock(HostResourcesCalculator.class);
    private HostCapacity capacity;
    private List<Node> nodes;
    private Node host1, host2, host3;
    private final NodeResources resources1 = new NodeResources(1, 30, 20, 1.5);
    private final NodeResources resources2 = new NodeResources(2, 40, 40, 0.5);

    @Before
    public void setup() {
        doAnswer(invocation -> ((Flavor)invocation.getArguments()[0]).resources()).when(hostResourcesCalculator).advertisedResourcesOf(any());

        // Create flavors
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("host", "docker", "docker2");

        // Create three docker hosts
        host1 = Node.create("host1", IP.Config.of(Set.of("::1"), generateIPs(2, 4), List.of()), "host1", nodeFlavors.getFlavorOrThrow("host"), NodeType.host).build();
        host2 = Node.create("host2", IP.Config.of(Set.of("::11"), generateIPs(12, 3), List.of()), "host2", nodeFlavors.getFlavorOrThrow("host"), NodeType.host).build();
        host3 = Node.create("host3", IP.Config.of(Set.of("::21"), generateIPs(22, 1), List.of()), "host3", nodeFlavors.getFlavorOrThrow("host"), NodeType.host).build();

        // Add two containers to host1
        var nodeA = Node.createDockerNode(Set.of("::2"), "nodeA", "host1", resources1, NodeType.tenant).build();
        var nodeB = Node.createDockerNode(Set.of("::3"), "nodeB", "host1", resources1, NodeType.tenant).build();

        // Add two containers to host 2 (same as host 1)
        var nodeC = Node.createDockerNode(Set.of("::12"), "nodeC", "host2", resources1, NodeType.tenant).build();
        var nodeD = Node.createDockerNode(Set.of("::13"), "nodeD", "host2", resources1, NodeType.tenant).build();

        // Add a larger container to host3
        var nodeE = Node.createDockerNode(Set.of("::22"), "nodeE", "host3", resources2, NodeType.tenant).build();

        // init docker host capacity
        nodes = new ArrayList<>(List.of(host1, host2, host3, nodeA, nodeB, nodeC, nodeD, nodeE));
        capacity = new HostCapacity(new LockedNodeList(nodes, () -> {}), hostResourcesCalculator);
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
        Node nodeF = Node.createDockerNode(Set.of("::6"), "nodeF", "host1", resources1, NodeType.tenant).build();
        nodes.add(nodeF);
        capacity = new HostCapacity(new LockedNodeList(nodes, () -> {}), hostResourcesCalculator);
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
        assertEquals(new NodeResources(5, 40, 80, 2, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote),
                     capacity.freeCapacityOf(host1, false));
        assertEquals(new NodeResources(5, 60, 80, 4.5, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote),
                     capacity.freeCapacityOf(host3, false));

        doAnswer(invocation -> {
            NodeResources totalHostResources = ((Flavor) invocation.getArguments()[0]).resources();
            return totalHostResources.subtract(new NodeResources(1, 2, 3, 0.5, NodeResources.DiskSpeed.any));
        }).when(hostResourcesCalculator).advertisedResourcesOf(any());

        assertEquals(new NodeResources(4, 38, 77, 1.5, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote),
                     capacity.freeCapacityOf(host1, false));
        assertEquals(new NodeResources(4, 58, 77, 4, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote),
                     capacity.freeCapacityOf(host3, false));
    }

    @Test
    public void devhostCapacityTest() {
        // Dev host can assign both configserver and tenant containers.

        var nodeFlavors = FlavorConfigBuilder.createDummies("devhost", "container");
        var devHost = Node.create("devhost", new IP.Config(Set.of("::1"), generateIPs(2, 10)), "devhost", nodeFlavors.getFlavorOrThrow("devhost"), NodeType.devhost).build();

        var cfg = Node.createDockerNode(Set.of("::2"), "cfg", "devhost", resources1, NodeType.config).build();

        var nodes = new ArrayList<>(List.of(cfg));
        var capacity = new HostCapacity(new LockedNodeList(nodes, () -> {}), hostResourcesCalculator);
        assertTrue(capacity.hasCapacity(devHost, resources1));

        var container1 = Node.createDockerNode(Set.of("::3"), "container1", "devhost", resources1, NodeType.tenant).build();
        nodes = new ArrayList<>(List.of(cfg, container1));
        capacity = new HostCapacity(new LockedNodeList(nodes, () -> {}), hostResourcesCalculator);
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
