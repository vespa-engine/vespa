// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.jrt.slobrok.api.Mirror;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

abstract class LoadBalancer {
    static class NodeMetrics {
        private AtomicLong sent = new AtomicLong(0);
        private AtomicLong received = new AtomicLong(0);
        private AtomicLong busy = new AtomicLong(0);
        long pending() { return sent.get() - received.get(); }
        void incSend() { sent.incrementAndGet(); }
        void incReceived() { received.incrementAndGet(); }
        void incBusy() { busy.incrementAndGet(); }
        long sent() { return sent.get(); }
        void reset() {
            sent.set(0);
            received.set(0);
            busy.set(0);
        }
    }
    static class Node {
        Node(Mirror.Entry e, NodeMetrics m) { entry = e; metrics = m; }

        Mirror.Entry entry;
        NodeMetrics metrics;
    }

    private final Map<String, Integer> cachedIndex = new HashMap<>();
    /** Statistics on each node we are load balancing over. Populated lazily. */
    private final List<NodeMetrics> nodeWeights = new ArrayList<>();
    private final String cluster;

    public LoadBalancer(String cluster) {
        this.cluster = cluster;
    }
    List<NodeMetrics> getNodeWeights() {
        return nodeWeights;
    }
    /** Returns the index from a node name string */
    int getIndex(String nodeName) {
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
    int getCachedIndex(String nodeName) {
        return cachedIndex.computeIfAbsent(nodeName, key -> getIndex(key));
    }
    /**
     * Returns the node metrics at a given index.
     * If there is no entry at the given index it is created by this call.
     */
    protected final synchronized NodeMetrics getNodeMetrics(Mirror.Entry entry) {
        int index = getCachedIndex(entry.getName());
        // expand node array as needed
        while (nodeWeights.size() < (index + 1))
            nodeWeights.add(null);

        NodeMetrics nodeMetrics = nodeWeights.get(index);
        if (nodeMetrics == null) { // initialize statistics for this node
            nodeMetrics = createNodeMetrics();
            nodeWeights.set(index, nodeMetrics);
        }
        return nodeMetrics;
    }

    protected NodeMetrics createNodeMetrics() {
        return new NodeMetrics();
    }
    abstract Node getRecipient(List<Mirror.Entry> choices);
    abstract void received(Node node, boolean busy);
}
