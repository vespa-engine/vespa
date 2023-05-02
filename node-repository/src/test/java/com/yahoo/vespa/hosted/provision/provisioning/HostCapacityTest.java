// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostName;
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
import java.util.stream.Stream;

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
    private Node host1;
    private Node host2;
    private Node host3;
    private Node host4;
    private final NodeResources resources0 = new NodeResources(1, 30, 20, 1.5);
    private final NodeResources resources1 = new NodeResources(2, 40, 40, 0.5);

    @Before
    public void setup() {
        doAnswer(invocation -> ((Flavor)invocation.getArguments()[0]).resources()).when(hostResourcesCalculator).advertisedResourcesOf(any());

        // Create flavors
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("host", "docker", "docker2");

        // Create hosts
        host1 = Node.create("host1", IP.Config.of(Set.of("::1"), createIps(2, 4), List.of()), "host1", nodeFlavors.getFlavorOrThrow("host"), NodeType.host).build();
        host2 = Node.create("host2", IP.Config.of(Set.of("::11"), createIps(12, 3), List.of()), "host2", nodeFlavors.getFlavorOrThrow("host"), NodeType.host).build();
        host3 = Node.create("host3", IP.Config.of(Set.of("::21"), createIps(22, 2), List.of()), "host3", nodeFlavors.getFlavorOrThrow("host"), NodeType.host).build();
        host4 = Node.create("host3", IP.Config.of(Set.of("::21"), createIps(50, 0), List.of()), "host4", nodeFlavors.getFlavorOrThrow("host"), NodeType.host).build();

        // Add two containers to host1
        var nodeA = Node.reserve(Set.of("::2"), "nodeA", "host1", resources0, NodeType.tenant).build();
        var nodeB = Node.reserve(Set.of("::3"), "nodeB", "host1", resources0, NodeType.tenant).build();

        // Add two containers to host 2 (same as host 1)
        var nodeC = Node.reserve(Set.of("::12"), "nodeC", "host2", resources0, NodeType.tenant).build();
        var nodeD = Node.reserve(Set.of("::13"), "nodeD", "host2", resources0, NodeType.tenant).build();

        // Add a larger container to host3
        var nodeE = Node.reserve(Set.of("::22"), "nodeE", "host3", resources1, NodeType.tenant).build();

        // init host capacity
        nodes = new ArrayList<>(List.of(host1, host2, host3, nodeA, nodeB, nodeC, nodeD, nodeE));
        capacity = new HostCapacity(new LockedNodeList(nodes, () -> {}), hostResourcesCalculator);
    }

    @Test
    public void hasCapacity() {
        assertTrue(capacity.hasCapacity(host1, resources0));
        assertTrue(capacity.hasCapacity(host1, resources1));
        assertTrue(capacity.hasCapacity(host2, resources0));
        assertTrue(capacity.hasCapacity(host2, resources1));
        assertTrue(capacity.hasCapacity(host3, resources0));
        assertTrue(capacity.hasCapacity(host3, resources1));
        assertFalse(capacity.hasCapacity(host4, resources0));  // No IPs available
        assertFalse(capacity.hasCapacity(host4, resources1)); // No IPs available

        // Add a new node to host1 to deplete the memory resource
        Node nodeF = Node.reserve(Set.of("::6"), "nodeF", "host1", resources0, NodeType.tenant).build();
        nodes.add(nodeF);
        capacity = new HostCapacity(new LockedNodeList(nodes, () -> {}), hostResourcesCalculator);
        assertFalse(capacity.hasCapacity(host1, resources0));
        assertFalse(capacity.hasCapacity(host1, resources1));
    }

    @Test
    public void freeIPs() {
        assertEquals(2, capacity.freeIps(host1));
        assertEquals(1, capacity.freeIps(host2));
        assertEquals(1, capacity.freeIps(host3));
        assertEquals(0, capacity.freeIps(host4));
    }

    @Test
    public void unusedCapacityOf() {
        assertEquals(new NodeResources(5, 40, 80, 2,
                                       NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote, NodeResources.Architecture.x86_64),
                     capacity.unusedCapacityOf(host1));
        assertEquals(new NodeResources(5, 60, 80, 4.5,
                                       NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote, NodeResources.Architecture.x86_64),
                     capacity.unusedCapacityOf(host3));
        assertEquals(new NodeResources(7, 100, 120, 5,
                                       NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote, NodeResources.Architecture.x86_64),
                     capacity.unusedCapacityOf(host4));

        doAnswer(invocation -> {
            NodeResources totalHostResources = ((Flavor) invocation.getArguments()[0]).resources();
            return totalHostResources.subtract(new NodeResources(1, 2, 3, 0.5, NodeResources.DiskSpeed.any));
        }).when(hostResourcesCalculator).advertisedResourcesOf(any());

        assertEquals(new NodeResources(4, 38, 77, 1.5, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote, NodeResources.Architecture.x86_64),
                     capacity.unusedCapacityOf(host1));
        assertEquals(new NodeResources(4, 58, 77, 4, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote, NodeResources.Architecture.x86_64),
                     capacity.unusedCapacityOf(host3));
    }

    @Test
    public void availableCapacityOf() {
        assertEquals(new NodeResources(5, 40, 80, 2,
                                       NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote, NodeResources.Architecture.x86_64),
                     capacity.availableCapacityOf(host1));
        assertEquals(new NodeResources(5, 60, 80, 4.5,
                                       NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote, NodeResources.Architecture.x86_64),
                     capacity.availableCapacityOf(host3));
        assertEquals(NodeResources.zero(),
                     capacity.availableCapacityOf(host4));
    }

    @Test
    public void verifyCapacityFromAddresses() {
        Node nodeA = Node.reserve(Set.of("::2"), "nodeA", "host1", resources0, NodeType.tenant).build();
        Node nodeB = Node.reserve(Set.of("::3"), "nodeB", "host1", resources0, NodeType.tenant).build();
        Node nodeC = Node.reserve(Set.of("::4"), "nodeC", "host1", resources0, NodeType.tenant).build();

        // host1 is a host with resources = 7-100-120-5 (7 vcpus, 100G memory, 120G disk, and 5Gbps),
        // while nodeA-C have resources = resources0 = 1-30-20-1.5

        Node host1 = setupHostWithAdditionalHostnames("host1", "nodeA");
        // Allocating nodeA should be OK
        assertTrue(hasCapacity(resources0, host1));
        // then, the second node lacks hostname address
        assertFalse(hasCapacity(resources0, host1, nodeA));

        host1 = setupHostWithAdditionalHostnames("host1", "nodeA", "nodeB");
        // Allocating nodeA and nodeB should be OK
        assertTrue(hasCapacity(resources0, host1));
        assertTrue(hasCapacity(resources0, host1, nodeA));
        // but the third node lacks hostname address
        assertFalse(hasCapacity(resources0, host1, nodeA, nodeB));

        host1 = setupHostWithAdditionalHostnames("host1", "nodeA", "nodeB", "nodeC");
        // Allocating nodeA, nodeB, and nodeC should be OK
        assertTrue(hasCapacity(resources0, host1));
        assertTrue(hasCapacity(resources0, host1, nodeA));
        assertTrue(hasCapacity(resources0, host1, nodeA, nodeB));
        // but the fourth node lacks hostname address
        assertFalse(hasCapacity(resources0, host1, nodeA, nodeB, nodeC));

        host1 = setupHostWithAdditionalHostnames("host1", "nodeA", "nodeB", "nodeC", "nodeD");
        // Allocating nodeA, nodeB, and nodeC should be OK
        assertTrue(hasCapacity(resources0, host1));
        assertTrue(hasCapacity(resources0, host1, nodeA));
        assertTrue(hasCapacity(resources0, host1, nodeA, nodeB));
        // but the fourth lacks memory (host has 100G, while 4x30G = 120G
        assertFalse(hasCapacity(resources0, host1, nodeA, nodeB, nodeC));
    }

    private Node setupHostWithAdditionalHostnames(String hostHostname, String... additionalHostnames) {
        List<HostName> hostnames = Stream.of(additionalHostnames).map(HostName::of).toList();

        doAnswer(invocation -> ((Flavor)invocation.getArguments()[0]).resources())
                .when(hostResourcesCalculator).advertisedResourcesOf(any());

        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies(
                "host",     // 7-100-120-5
                "docker"); // 2- 40- 40-0.5 = resources1

        return Node.create(hostHostname, IP.Config.of(Set.of("::1"), Set.of(), hostnames), hostHostname,
                nodeFlavors.getFlavorOrThrow("host"), NodeType.host).build();
    }

    private boolean hasCapacity(NodeResources requestedCapacity, Node host, Node... remainingNodes) {
        List<Node> nodes = Stream.concat(Stream.of(host), Stream.of(remainingNodes)).toList();
        var capacity = new HostCapacity(new LockedNodeList(nodes, () -> {}), hostResourcesCalculator);
        return capacity.hasCapacity(host, requestedCapacity);
    }

    private Set<String> createIps(int start, int count) {
        // Allow 4 containers
        Set<String> ipAddressPool = new LinkedHashSet<>();
        for (int i = start; i < (start + count); i++) {
            ipAddressPool.add("::" + i);
        }
        return ipAddressPool;
    }

}
