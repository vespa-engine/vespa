// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.History.Event.Type;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static java.util.Comparator.comparing;

/**
 * This removes hosts from {@link com.yahoo.vespa.hosted.provision.Node.State#deprovisioned}, in dynamically provisioned
 * zones, after a grace period.
 *
 * @author mpolden
 */
public class DeprovisionedExpirer extends Expirer {

    private static final int maxDeprovisionedNodes = 1000;

    DeprovisionedExpirer(NodeRepository nodeRepository, Duration expiryTime, Metric metric) {
        super(Node.State.deprovisioned, History.Event.Type.deprovisioned, nodeRepository, expiryTime, metric);
    }

    @Override
    protected boolean isExpired(Node node) {
        return nodeRepository().zone().cloud().dynamicProvisioning() && super.isExpired(node);
    }

    @Override
    protected NodeList getExpiredNodes() {
        List<Node> deprovisioned = nodeRepository().nodes().list(Node.State.deprovisioned)
                                                   .sortedBy(comparing(node -> node.history().event(Type.deprovisioned)
                                                                                   .map(History.Event::at)
                                                                                   .orElse(Instant.EPOCH)))
                                                   .asList();
        Deque<Node> expired = new ArrayDeque<>(deprovisioned);
        int kept = 0;
        while ( ! expired.isEmpty()) {
            if (isExpired(expired.getLast()) || kept++ >= maxDeprovisionedNodes) break; // If we encounter an expired node, the rest are also expired.
            expired.removeLast();
        }
        return NodeList.copyOf(List.copyOf(expired));
    }

    @Override
    protected void expire(List<Node> expired) {
        nodeRepository().nodes().performOn(NodeList.copyOf(expired),
                                           (node, lock) -> { nodeRepository().nodes().forget(node); return node; });
    }

}
