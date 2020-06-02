// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.jrt.slobrok.api.Mirror;

import java.util.List;

/**
 * Load balances over a set of nodes based on statistics gathered from those nodes.
 *
 * @author thomasg
 */
class LegacyLoadBalancer extends LoadBalancer {

    static class LegacyNodeMetrics extends NodeMetrics {
        double weight = 1.0;
    }

    private double position = 0.0;

    public LegacyLoadBalancer(String cluster) {
        super(cluster);
    }

    /**
     * The load balancing operation: Returns a node choice from the given choices,
     * based on previously gathered statistics on the nodes, and a running "position"
     * which is increased by 1 on each call to this.
     *
     * @param choices the node choices, represented as Slobrok entries
     * @return the chosen node, or null only if the given choices were zero
     */
    @Override
    Node getRecipient(List<Mirror.Entry> choices) {
        if (choices.isEmpty()) return null;

        double weightSum = 0.0;
        Node selectedNode = null;
        synchronized (this) {
            for (Mirror.Entry entry : choices) {
                LegacyNodeMetrics nodeMetrics = (LegacyNodeMetrics)getNodeMetrics(entry);

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
            selectedNode.metrics.incSend();
        }
        return selectedNode;
    }

    @Override
    protected NodeMetrics createNodeMetrics() {
        return new LegacyNodeMetrics();
    }

    /** Scale weights such that ratios are preserved */
    private void increaseWeights() {
        for (NodeMetrics nodeMetrics : getNodeWeights()) {
            LegacyNodeMetrics n = (LegacyNodeMetrics) nodeMetrics;
            if (n == null) continue;
            double want = n.weight * 1.01010101010101010101;
            n.weight = Math.max(1.0, want);
        }
    }

    @Override
    void received(Node node, boolean busy) {
        if (busy) {
            synchronized (this) {
                LegacyNodeMetrics n = (LegacyNodeMetrics) node.metrics;
                double wantWeight = n.weight - 0.01;
                if (wantWeight < 1.0) {
                    increaseWeights();
                    n.weight = 1.0;
                } else {
                    n.weight = wantWeight;
                }
                node.metrics.incBusy();
            }
        }
    }

}
