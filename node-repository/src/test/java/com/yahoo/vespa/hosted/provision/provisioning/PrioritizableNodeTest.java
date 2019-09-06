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

public class PrioritizableNodeTest {

    @Test
    public void test_order() {
        List<PrioritizableNode> expected = List.of(
                new PrioritizableNode(node("abc123", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.empty(), false, true, false),
                new PrioritizableNode(node("abc123", Node.State.active), new NodeResources(2, 2, 2, 2), Optional.empty(), true, false, false),
                new PrioritizableNode(node("abc123", Node.State.inactive), new NodeResources(2, 2, 2, 2), Optional.empty(), true, false, false),
                new PrioritizableNode(node("abc123", Node.State.reserved), new NodeResources(2, 2, 2, 2), Optional.empty(), true, false, false),
                new PrioritizableNode(node("abc123", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.of(node("host1", Node.State.active)), true, false, true),
                new PrioritizableNode(node("abc123", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.of(node("host1", Node.State.ready)), true, false, true),
                new PrioritizableNode(node("abc123", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.of(node("host1", Node.State.provisioned)), true, false, true),
                new PrioritizableNode(node("abc123", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.of(node("host1", Node.State.failed)), true, false, true),
                new PrioritizableNode(node("abc123", Node.State.ready), new NodeResources(1, 1, 1, 1), Optional.empty(), true, false, true),
                new PrioritizableNode(node("abc123", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.empty(), true, false, true),
                new PrioritizableNode(node("xyz789", Node.State.ready), new NodeResources(2, 2, 2, 2), Optional.empty(), true, false, true)
        );

        List<PrioritizableNode> copy = new ArrayList<>(expected);
        Collections.shuffle(copy);
        Collections.sort(copy);
        assertEquals(expected, copy);
    }

    private static Node node(String hostname, Node.State state) {
        return new Node(hostname, new IP.Config(Set.of("::1"), Set.of()), hostname, Optional.empty(), new Flavor(new NodeResources(2, 2, 2, 2)),
                Status.initial(), state, Optional.empty(), History.empty(), NodeType.tenant, new Reports(), Optional.empty());
    }
}