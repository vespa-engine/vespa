// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
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
import static org.mockito.Matchers.any;
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
    private Flavor flavorDocker, flavorDocker2;

    @Before
    public void setup() {
        doAnswer(invocation -> invocation.getArguments()[0]).when(hostResourcesCalculator).availableCapacityOf(any());

        // Create flavors
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("host", "docker", "docker2");
        flavorDocker = nodeFlavors.getFlavorOrThrow("docker");
        flavorDocker2 = nodeFlavors.getFlavorOrThrow("docker2");

        // Create three docker hosts
        host1 = Node.create("host1", Set.of("::1"), generateIPs(2, 4), "host1", Optional.empty(), Optional.empty(), nodeFlavors.getFlavorOrThrow("host"), NodeType.host);
        host2 = Node.create("host2", Set.of("::11"), generateIPs(12, 3), "host2", Optional.empty(), Optional.empty(), nodeFlavors.getFlavorOrThrow("host"), NodeType.host);
        host3 = Node.create("host3", Set.of("::21"), generateIPs(22, 1), "host3", Optional.empty(), Optional.empty(), nodeFlavors.getFlavorOrThrow("host"), NodeType.host);

        // Add two containers to host1
        var nodeA = Node.create("nodeA", Set.of("::2"), Set.of(), "nodeA", Optional.of("host1"), Optional.empty(), flavorDocker, NodeType.tenant);
        var nodeB = Node.create("nodeB", Set.of("::3"), Set.of(), "nodeB", Optional.of("host1"), Optional.empty(), flavorDocker, NodeType.tenant);

        // Add two containers to host 2 (same as host 1)
        var nodeC = Node.create("nodeC", Set.of("::12"), Set.of(), "nodeC", Optional.of("host2"), Optional.empty(), flavorDocker, NodeType.tenant);
        var nodeD = Node.create("nodeD", Set.of("::13"), Set.of(), "nodeD", Optional.of("host2"), Optional.empty(), flavorDocker, NodeType.tenant);

        // Add a larger container to host3
        var nodeE = Node.create("nodeE", Set.of("::22"), Set.of(), "nodeE", Optional.of("host3"), Optional.empty(), flavorDocker2, NodeType.tenant);

        // init docker host capacity
        nodes = new ArrayList<>(List.of(host1, host2, host3, nodeA, nodeB, nodeC, nodeD, nodeE));
        capacity = new DockerHostCapacity(new LockedNodeList(nodes, () -> {}), hostResourcesCalculator);
    }

    @Test
    public void hasCapacity() {
        assertTrue(capacity.hasCapacity(host1, flavorDocker.resources()));
        assertTrue(capacity.hasCapacity(host1, flavorDocker2.resources()));
        assertTrue(capacity.hasCapacity(host2, flavorDocker.resources()));
        assertTrue(capacity.hasCapacity(host2, flavorDocker2.resources()));
        assertFalse(capacity.hasCapacity(host3, flavorDocker.resources()));  // No ip available
        assertFalse(capacity.hasCapacity(host3, flavorDocker2.resources())); // No ip available

        // Add a new node to host1 to deplete the memory resource
        Node nodeF = Node.create("nodeF", Set.of("::6"), Set.of(),
                "nodeF", Optional.of("host1"), Optional.empty(), flavorDocker, NodeType.tenant);
        nodes.add(nodeF);
        capacity = new DockerHostCapacity(new LockedNodeList(nodes, () -> {}), hostResourcesCalculator);
        assertFalse(capacity.hasCapacity(host1, flavorDocker.resources()));
        assertFalse(capacity.hasCapacity(host1, flavorDocker2.resources()));
    }

    @Test
    public void freeIPs() {
        assertEquals(2, capacity.freeIPs(host1));
        assertEquals(1, capacity.freeIPs(host2));
        assertEquals(0, capacity.freeIPs(host3));
    }

    @Test
    public void freeCapacityOf() {
        assertEquals(new NodeResources(5, 4, 8), capacity.freeCapacityOf(host1, false));
        assertEquals(new NodeResources(5, 6, 8), capacity.freeCapacityOf(host3, false));

        doAnswer(invocation -> {
            NodeResources totalHostResources = (NodeResources) invocation.getArguments()[0];
            return totalHostResources.subtract(new NodeResources(1, 2, 3, NodeResources.DiskSpeed.any));
        }).when(hostResourcesCalculator).availableCapacityOf(any());

        assertEquals(new NodeResources(4, 2, 5), capacity.freeCapacityOf(host1, false));
        assertEquals(new NodeResources(4, 4, 5), capacity.freeCapacityOf(host3, false));
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
