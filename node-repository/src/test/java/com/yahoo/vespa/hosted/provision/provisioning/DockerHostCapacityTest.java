// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author smorgrav
 */
public class DockerHostCapacityTest {

    private DockerHostCapacity capacity;
    private List<Node> nodes;
    private Node host1, host2, host3;
    private Node nodeA, nodeB, nodeC, nodeD, nodeE;
    private Flavor flavorDocker, flavorDocker2;

    @Before
    public void setup() {
        // Create flavors
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("host", "docker", "docker2");
        flavorDocker = nodeFlavors.getFlavorOrThrow("docker");
        flavorDocker2 = nodeFlavors.getFlavorOrThrow("docker2");

        // Create three docker hosts
        host1 = Node.create("host1", Collections.singleton("::1"), generateIPs(2, 4), "host1", Optional.empty(), nodeFlavors.getFlavorOrThrow("host"), NodeType.host);
        host2 = Node.create("host2", Collections.singleton("::11"), generateIPs(12, 3), "host2", Optional.empty(), nodeFlavors.getFlavorOrThrow("host"), NodeType.host);
        host3 = Node.create("host3", Collections.singleton("::21"), generateIPs(22, 1), "host3", Optional.empty(), nodeFlavors.getFlavorOrThrow("host"), NodeType.host);

        // Add two containers to host1
        nodeA = Node.create("nodeA", Collections.singleton("::2"), Collections.emptySet(), "nodeA", Optional.of("host1"), flavorDocker, NodeType.tenant);
        nodeB = Node.create("nodeB", Collections.singleton("::3"), Collections.emptySet(), "nodeB", Optional.of("host1"), flavorDocker, NodeType.tenant);

        // Add two containers to host 2 (same as host 1)
        nodeC = Node.create("nodeC", Collections.singleton("::12"), Collections.emptySet(), "nodeC", Optional.of("host2"), flavorDocker, NodeType.tenant);
        nodeD = Node.create("nodeD", Collections.singleton("::13"), Collections.emptySet(), "nodeD", Optional.of("host2"), flavorDocker, NodeType.tenant);

        // Add a larger container to host3
        nodeE = Node.create("nodeE", Collections.singleton("::22"), Collections.emptySet(), "nodeE", Optional.of("host3"), flavorDocker2, NodeType.tenant);

        // init docker host capacity
        nodes = new ArrayList<>();
        Collections.addAll(nodes, host1, host2, host3, nodeA, nodeB, nodeC, nodeD, nodeE);
        capacity = new DockerHostCapacity(nodes);
    }

    @Test
    public void compare_used_to_sort_in_decending_order() {
        assertEquals(host1, nodes.get(0)); //Make sure it is unsorted here

        Collections.sort(nodes, capacity::compare);
        assertEquals(host3, nodes.get(0));
        assertEquals(host1, nodes.get(1));
        assertEquals(host2, nodes.get(2));
    }

    @Test
    public void hasCapacity() {
        assertTrue(capacity.hasCapacity(host1, ResourceCapacity.of(flavorDocker)));
        assertTrue(capacity.hasCapacity(host1, ResourceCapacity.of(flavorDocker2)));
        assertTrue(capacity.hasCapacity(host2, ResourceCapacity.of(flavorDocker)));
        assertTrue(capacity.hasCapacity(host2, ResourceCapacity.of(flavorDocker2)));
        assertFalse(capacity.hasCapacity(host3, ResourceCapacity.of(flavorDocker)));  // No ip available
        assertFalse(capacity.hasCapacity(host3, ResourceCapacity.of(flavorDocker2))); // No ip available

        // Add a new node to host1 to deplete the memory resource
        Node nodeF = Node.create("nodeF", Collections.singleton("::6"), Collections.emptySet(),
                "nodeF", Optional.of("host1"), flavorDocker, NodeType.tenant);
        nodes.add(nodeF);
        capacity = new DockerHostCapacity(nodes);
        assertFalse(capacity.hasCapacity(host1, ResourceCapacity.of(flavorDocker)));
        assertFalse(capacity.hasCapacity(host1, ResourceCapacity.of(flavorDocker2)));
    }

    @Test
    public void freeIPs() {
        assertEquals(2, capacity.freeIPs(host1));
        assertEquals(1, capacity.freeIPs(host2));
        assertEquals(0, capacity.freeIPs(host3));
    }

    @Test
    public void getCapacityTotal() {
        ResourceCapacity total = capacity.getCapacityTotal();
        assertEquals(21.0, total.getCpu(), 0.1);
        assertEquals(30.0, total.getMemory(), 0.1);
        assertEquals(36.0, total.getDisk(), 0.1);
    }

    @Test
    public void getFreeCapacityTotal() {
        ResourceCapacity totalFree = capacity.getFreeCapacityTotal();
        assertEquals(15.0, totalFree.getCpu(), 0.1);
        assertEquals(14.0, totalFree.getMemory(), 0.1);
        assertEquals(24.0, totalFree.getDisk(), 0.1);
    }

    @Test
    public void freeCapacityInFlavorEquivalence() {
        assertEquals(2, capacity.freeCapacityInFlavorEquivalence(flavorDocker));
        assertEquals(2, capacity.freeCapacityInFlavorEquivalence(flavorDocker2));
    }

    @Test
    public void getNofHostsAvailableFor() {
        assertEquals(2, capacity.getNofHostsAvailableFor(flavorDocker));
        assertEquals(2, capacity.getNofHostsAvailableFor(flavorDocker2));
    }

    private Set<String> generateIPs(int start, int count) {
        // Allow 4 containers
        Set<String> additionalIps = new HashSet<>();
        for (int i = start; i < (start + count); i++) {
            additionalIps.add("::" + i);
        }
        return additionalIps;
    }

}
