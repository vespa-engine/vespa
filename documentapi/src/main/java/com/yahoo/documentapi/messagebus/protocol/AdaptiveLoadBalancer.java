// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.jrt.slobrok.api.Mirror;

import java.util.List;
import java.util.Random;

/**
 * Will pick 2 random candidates and select the one with the least pending operations.
 *
 * @author baldersheim
 */
class AdaptiveLoadBalancer extends LoadBalancer {
    private final Random random;
    AdaptiveLoadBalancer(String cluster) {
        this(cluster, new Random());
    }
    AdaptiveLoadBalancer(String cluster, Random random) {
        super(cluster);
        this.random = random;
    }

    @Override
    Node getRecipient(List<Mirror.Entry> choices) {
        if (choices.isEmpty()) return null;
        Mirror.Entry entry;
        NodeMetrics metrics;
        if (choices.size() == 1) {
            entry = choices.get(0);
            metrics = getNodeMetrics(entry);
        } else {
            int candA = random.nextInt(choices.size());
            int candB = random.nextInt(choices.size());
            while (candB == candA) {
                candB = random.nextInt(choices.size());
            }
            entry = choices.get(candA);
            Mirror.Entry entryB = choices.get(candB);
            metrics = getNodeMetrics(entry);
            NodeMetrics metricsB = getNodeMetrics(entryB);
            if (metrics.pending() > metricsB.pending()) {
                entry = entryB;
                metrics = metricsB;
            }
        }
        metrics.incSend();
        return new Node(entry, metrics);
    }

    @Override
    void received(Node node, boolean busy) {
        node.metrics.incReceived();
        if (busy) {
            node.metrics.incBusy();
        }
    }
}
