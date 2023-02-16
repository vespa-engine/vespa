// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.IP;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class NodeCandidateTest {

    @Test
    public void testOrdering() {
        List<NodeCandidate> expected = List.of(
                new NodeCandidate.ConcreteNodeCandidate(node("01", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.empty(), false, true, true, false, false),
                new NodeCandidate.ConcreteNodeCandidate(node("02", Node.State.active), new NodeResources(2, 2, 2, 2), Optional.empty(), true, true, false, false, false),
                new NodeCandidate.ConcreteNodeCandidate(node("04", Node.State.reserved), new NodeResources(2, 2, 2, 2), Optional.empty(), true, true, false, false, false),
                new NodeCandidate.ConcreteNodeCandidate(node("03", Node.State.inactive), new NodeResources(2, 2, 2, 2), Optional.empty(), true, true, false, false, false),
                new NodeCandidate.ConcreteNodeCandidate(node("05", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.of(node("host1", Node.State.active)), true, true, false, true, false),
                new NodeCandidate.ConcreteNodeCandidate(node("06", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.of(node("host1", Node.State.ready)), true, true, false, true, false),
                new NodeCandidate.ConcreteNodeCandidate(node("07", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.of(node("host1", Node.State.provisioned)), true, true, false, true, false),
                new NodeCandidate.ConcreteNodeCandidate(node("08", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.of(node("host1", Node.State.failed)), true, true, false, true, false),
                new NodeCandidate.ConcreteNodeCandidate(node("09", Node.State.ready), new NodeResources(1, 1, 1, 1), Optional.empty(), true, true, false, true, false),
                new NodeCandidate.ConcreteNodeCandidate(node("10", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.empty(), true, true, false, true, false),
                new NodeCandidate.ConcreteNodeCandidate(node("11", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.empty(), true, true, false, true, false)
        );
        assertOrder(expected);
    }

    @Test
    public void testOrderingByAllocationSkew1() {
        List<NodeCandidate> expected = List.of(
                node("1", node(4, 4), host(20, 20), host(40, 40)),
                node("2", node(4, 4), host(21, 20), host(40, 40)),
                node("3", node(4, 4), host(22, 20), host(40, 40)),
                node("4", node(4, 4), host(21, 22), host(40, 40)),
                node("5", node(4, 4), host(21, 21), host(40, 80))
        );
        assertOrder(expected);
    }

    @Test
    public void testOrderingByAllocationSkew2() {
        // The same as testOrderingByAllocationSkew1, but deviating from mean (20) in the other direction.
        // Since we don't choose the node with the lowest skew, but with the largest skew *reduction*
        // this causes the opposite order.
        List<NodeCandidate> expected = List.of(
                node("4", node(4, 4), host(19, 18), host(40, 40)),
                node("3", node(4, 4), host(18, 20), host(40, 40)),
                node("2", node(4, 4), host(19, 20), host(40, 40)),
                node("1", node(4, 4), host(20, 20), host(40, 40)),
                node("5", node(4, 4), host(19, 19), host(40, 80))
        );
        assertOrder(expected);
    }

    @Test
    public void testOrderingByAllocationSkew3() {
        // The same as testOrderingByAllocationSkew1, but allocating skewed towards cpu
        List<NodeCandidate> expected = List.of(
                node("1", node(4, 2), host(20, 20), host(40, 40)),
                node("2", node(4, 2), host(21, 20), host(40, 40)),
                node("4", node(4, 2), host(21, 22), host(40, 40)),
                node("3", node(4, 2), host(22, 20), host(40, 40)),
                node("5", node(4, 2), host(21, 21), host(40, 80))
        );
        assertOrder(expected);
    }

    @Test
    public void testOrderingByAllocationSkew4() {
        // The same as testOrderingByAllocationSkew1, but allocating skewed towards memory
        List<NodeCandidate> expected = List.of(
                node("5", node(2, 10), host(21, 21), host(40, 80)),
                node("3", node(2, 10), host(22, 20), host(40, 40)),
                node("2", node(2, 10), host(21, 20), host(40, 40)),
                node("1", node(2, 10), host(20, 20), host(40, 40)),
                node("4", node(2, 10), host(21, 22), host(40, 40))
        );
        assertOrder(expected);
    }

    @Test
    public void testOrderingByAllocationSkew5() {
        // node1 is skewed towards cpu (without this allocation), allocation is skewed towards memory, therefore
        // node 1 is preferred (even though it is still most skewed)
        List<NodeCandidate> expected = List.of(
                node("1", node(1, 5), host(21, 10), host(40, 40)),
                node("2", node(1, 5), host(21, 20), host(40, 40)),
                node("3", node(1, 5), host(20, 20), host(40, 40)),
                node("4", node(1, 5), host(20, 22), host(40, 40))
        );
        assertOrder(expected);
    }

    @Test
    public void testOrderingByExclusiveSwitch() {
        List<NodeCandidate> expected = List.of(
                node("1", true),
                node("2", true),
                node("3", false),
                node("4", false),
                node("5", false)
        );
        assertOrder(expected);
    }

    private void assertOrder(List<NodeCandidate> expected) {
        List<NodeCandidate> copy = new ArrayList<>(expected);
        Collections.shuffle(copy);
        Collections.sort(copy);
        assertEquals(expected, copy);
    }

    private static NodeResources node(double vcpu, double mem) {
        return new NodeResources(vcpu, mem, 0, 0);
    }

    private static NodeResources host(double vcpu, double mem) {
        return new NodeResources(vcpu, mem, 10, 10);
    }

    private static Node node(String hostname, Node.State state) {
        return Node.create(hostname, hostname, new Flavor(new NodeResources(2, 2, 2, 2)), state, NodeType.tenant)
                .ipConfigWithEmptyPool(Set.of("::1")).build();
    }

    private static NodeCandidate node(String hostname,
                                      NodeResources nodeResources,
                                      NodeResources allocatedHostResources, // allocated before adding nodeResources
                                      NodeResources totalHostResources,
                                      boolean exclusiveSwitch) {
        Node node = Node.create(hostname, hostname, new Flavor(nodeResources), Node.State.ready, NodeType.tenant)
                .parentHostname(hostname + "parent")
                .ipConfigWithEmptyPool(Set.of("::1")).build();
        Node parent = Node.create(hostname + "parent", hostname, new Flavor(totalHostResources), Node.State.ready, NodeType.host)
                          .ipConfig(IP.Config.of(Set.of("::1"), Set.of("::2")))
                          .build();
        return new NodeCandidate.ConcreteNodeCandidate(node, totalHostResources.subtract(allocatedHostResources), Optional.of(parent),
                                                       false, exclusiveSwitch, false, true, false);
    }

    private static NodeCandidate node(String hostname, NodeResources nodeResources,
                                      NodeResources allocatedHostResources, NodeResources totalHostResources) {
        return node(hostname, nodeResources, allocatedHostResources, totalHostResources, false);
    }

    private static NodeCandidate node(String hostname, boolean exclusiveSwitch) {
        return node(hostname,
                    node(2, 10),
                    host(20, 20),
                    host(40, 40),
                    exclusiveSwitch);
    }

}
