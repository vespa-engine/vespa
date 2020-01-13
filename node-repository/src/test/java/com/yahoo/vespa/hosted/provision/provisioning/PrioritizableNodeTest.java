// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.Reports;
import com.yahoo.vespa.hosted.provision.node.Status;
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
public class PrioritizableNodeTest {

    @Test
    public void test_order() {
        List<PrioritizableNode> expected = List.of(
                new PrioritizableNode(node("01", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.empty(), false, true, false, false),
                new PrioritizableNode(node("02", Node.State.active), new NodeResources(2, 2, 2, 2), Optional.empty(), true, false, false, false),
                new PrioritizableNode(node("03", Node.State.inactive), new NodeResources(2, 2, 2, 2), Optional.empty(), true, false, false, false),
                new PrioritizableNode(node("04", Node.State.reserved), new NodeResources(2, 2, 2, 2), Optional.empty(), true, false, false, false),
                new PrioritizableNode(node("05", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.of(node("host1", Node.State.active)), true, false, true, false),
                new PrioritizableNode(node("06", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.of(node("host1", Node.State.ready)), true, false, true, false),
                new PrioritizableNode(node("07", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.of(node("host1", Node.State.provisioned)), true, false, true, false),
                new PrioritizableNode(node("08", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.of(node("host1", Node.State.failed)), true, false, true, false),
                new PrioritizableNode(node("09", Node.State.ready), new NodeResources(1, 1, 1, 1), Optional.empty(), true, false, true, false),
                new PrioritizableNode(node("10", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.empty(), true, false, true, false),
                new PrioritizableNode(node("11", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.empty(), true, false, true, false)
        );
        assertOrder(expected);
    }

    @Test
    public void testOrderingByAllocationSkew1() {
        List<PrioritizableNode> expected = List.of(
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
        List<PrioritizableNode> expected = List.of(
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
        List<PrioritizableNode> expected = List.of(
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
        List<PrioritizableNode> expected = List.of(
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
        List<PrioritizableNode> expected = List.of(
                node("1", node(1, 5), host(21, 10), host(40, 40)),
                node("2", node(1, 5), host(21, 20), host(40, 40)),
                node("3", node(1, 5), host(20, 20), host(40, 40)),
                node("4", node(1, 5), host(20, 22), host(40, 40))
        );
        assertOrder(expected);
    }

    private void assertOrder(List<PrioritizableNode> expected) {
        List<PrioritizableNode> copy = new ArrayList<>(expected);
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
        return new Node(hostname, new IP.Config(Set.of("::1"), Set.of()), hostname, Optional.empty(), new Flavor(new NodeResources(2, 2, 2, 2)),
                        Status.initial(), state, Optional.empty(), History.empty(), NodeType.tenant, new Reports(), Optional.empty());
    }

    private static PrioritizableNode node(String hostname,
                                          NodeResources nodeResources,
                                          NodeResources allocatedHostResources, // allocated before adding nodeResources
                                          NodeResources totalHostResources) {
        Node node = new Node(hostname, new IP.Config(Set.of("::1"), Set.of()), hostname, Optional.of(hostname + "parent"), new Flavor(nodeResources),
                             Status.initial(), Node.State.ready, Optional.empty(), History.empty(), NodeType.tenant, new Reports(), Optional.empty());
        Node parent = new Node(hostname + "parent", new IP.Config(Set.of("::1"), Set.of()), hostname, Optional.empty(), new Flavor(totalHostResources),
                               Status.initial(), Node.State.ready, Optional.empty(), History.empty(), NodeType.host, new Reports(), Optional.empty());
        return new PrioritizableNode(node, totalHostResources.subtract(allocatedHostResources), Optional.of(parent), false, false, true, false);
    }
}
