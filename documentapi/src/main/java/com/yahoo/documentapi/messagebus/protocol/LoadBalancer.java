// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.google.common.util.concurrent.AtomicDouble;
import com.yahoo.jrt.slobrok.api.Mirror;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Load balances over a set of nodes based on statistics gathered from those nodes.
 *
 * @author thomasg
 */
public class LoadBalancer {

    public static class NodeMetrics {
        public AtomicLong sent = new AtomicLong();
        public AtomicLong busy = new AtomicLong();
        public double weight = 1.0;
    }

    public static class Node {
        public Node(Mirror.Entry e, NodeMetrics m) { entry = e; metrics = m; }

        public Mirror.Entry entry;
        public NodeMetrics metrics;
    }

    /** Statistics on each node we are load balancing over. Populated lazily. */
    private final List<NodeMetrics> nodeWeights = new CopyOnWriteArrayList<>();

    private final String cluster;
    private final AtomicDouble safePosition = new AtomicDouble(0.0);

    public LoadBalancer(String cluster) {
        this.cluster = cluster;
    }

    public List<NodeMetrics> getNodeWeights() {
        return nodeWeights;
    }

    /** Returns the index from a node name string */
    public int getIndex(String nodeName) {
        try {
            String s = nodeName.substring(cluster.length() + 1);
            s = s.substring(0, s.indexOf("/"));
            s = s.substring(s.lastIndexOf(".") + 1);
            return Integer.parseInt(s);
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            String err = "Expected recipient on the form '" + cluster + "/x/[y.]number/z', got '" + nodeName + "'.";
            throw new IllegalArgumentException(err, e);
        }
    }

    /**
     * The load balancing operation: Returns a node choice from the given choices,
     * based on previously gathered statistics on the nodes, and a running "position"
     * which is increased by 1 on each call to this.
     *
     * @param choices the node choices, represented as Slobrok entries
     * @return the chosen node, or null only if the given choices were zero
     */
    public Node getRecipient(List<Mirror.Entry> choices) {
        if (choices.isEmpty()) return null;

        double weightSum = 0.0;
        Node selectedNode = null;
        double position = safePosition.get();
        for (Mirror.Entry entry : choices) {
            NodeMetrics nodeMetrics = getNodeMetrics(entry);

            weightSum += nodeMetrics.weight;

            if (weightSum > position) {
                selectedNode = new Node(entry, nodeMetrics);
                break;
            }
        }
        if (selectedNode == null) { // Position>sum of all weights: Wrap around (but keep the remainder for some reason)
            position -= weightSum;
            selectedNode = new Node(choices.get(0), getNodeMetrics(choices.get(0)));
        }
        position += 1.0;
        safePosition.set(position);
        selectedNode.metrics.sent.incrementAndGet();
        return selectedNode;
    }

    /**
     * Returns the node metrics at a given index.
     * If there is no entry at the given index it is created by this call.
     */
    private NodeMetrics getNodeMetrics(Mirror.Entry entry) {
        int index = getIndex(entry.getName());
        // expand node array as needed
        while (nodeWeights.size() < (index + 1))
            nodeWeights.add(null);

        NodeMetrics nodeMetrics = nodeWeights.get(index);
        if (nodeMetrics == null) { // initialize statistics for this node
            nodeMetrics = new NodeMetrics();
            nodeWeights.set(index, nodeMetrics);
        }
        return nodeMetrics;
    }

    /** Scale weights such that ratios are preserved */
    private void increaseWeights() {
        for (NodeMetrics n : nodeWeights) {
            if (n == null) continue;
            double want = n.weight * 1.01010101010101010101;
            if (want >= 1.0) {
                n.weight = want;
            } else {
                n.weight = 1.0;
            }
        }
    }

    public void received(Node node, boolean busy) {
        if (busy) {
            double wantWeight = node.metrics.weight - 0.01;
            if (wantWeight < 1.0) {
                increaseWeights();
                node.metrics.weight = 1.0;
            } else {
                node.metrics.weight = wantWeight;
            }
            node.metrics.busy.incrementAndGet();
        }
    }

}
