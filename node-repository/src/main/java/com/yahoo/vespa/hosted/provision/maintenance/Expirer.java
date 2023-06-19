// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

/**
 * Base class for maintenance of nodes that linger in a particular state too long.
 *
 * @author bratseth
 */
public abstract class Expirer extends NodeRepositoryMaintainer {

    protected static final Logger log = Logger.getLogger(Expirer.class.getName());

    /** The state to expire from */
    private final Node.State fromState;

    /** The event record type which contains the timestamp to use for expiry */
    private final History.Event.Type eventType;

    private final Metric metric;
    private final Duration expiryTime;

    Expirer(Node.State fromState, History.Event.Type eventType, NodeRepository nodeRepository,
            Duration expiryTime, Metric metric) {
        super(nodeRepository, min(Duration.ofMinutes(10), expiryTime), metric);
        this.fromState = fromState;
        this.eventType = eventType;
        this.metric = metric;
        this.expiryTime = expiryTime;
    }

    @Override
    protected double maintain() {
        NodeList expired = nodeRepository().nodes().list(fromState).matching(this::isExpired);

        if ( ! expired.isEmpty()) {
            log.info(fromState + " expirer found " + expired.size() + " expired nodes: " + expired);
            expire(expired.asList());
        }

        metric.add("expired." + fromState, expired.size(), null);
        return 1.0;
    }

    protected boolean isExpired(Node node) {
        return isExpired(node, expiryTime);
    }

    protected final boolean isExpired(Node node, Duration expiryTime) {
        return node.history().hasEventBefore(eventType, clock().instant().minus(expiryTime));
    }

    /** Implement this callback to take action to expire these nodes */
    protected abstract void expire(List<Node> expired);

}
