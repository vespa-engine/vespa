// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.Status;

import java.time.Duration;
import java.util.List;

/**
 * Maintenance job which moves inactive nodes to dirty or parked after timeout.
 *
 * The timeout is in place for two reasons:
 *
 * - To ensure that the new application configuration has time to
 *   propagate before the node is used for something else.
 *
 * - To provide a grace period in which nodes can be brought back to active
 *   if they were deactivated in error. As inactive nodes retain their state
 *   they can be brought back to active and correct state faster than a new node.
 *
 * Nodes with following flags set are not reusable and will be moved to parked
 * instead of dirty:
 *
 * - {@link Status#wantToRetire()} (when set by an operator)
 * - {@link Status#wantToDeprovision()}
 *
 * @author bratseth
 * @author mpolden
 */
public class InactiveExpirer extends Expirer {

    private final NodeRepository nodeRepository;

    InactiveExpirer(NodeRepository nodeRepository, Duration inactiveTimeout, Metric metric) {
        super(Node.State.inactive, History.Event.Type.deactivated, nodeRepository, inactiveTimeout, metric);
        this.nodeRepository = nodeRepository;
    }

    @Override
    protected void expire(List<Node> expired) {
        expired.forEach(node -> {
            if (node.status().wantToDeprovision() || retiredByOperator(node)) {
                nodeRepository.park(node.hostname(), false, Agent.InactiveExpirer, "Expired by InactiveExpirer");
            } else {
                nodeRepository.setDirty(node, Agent.InactiveExpirer, "Expired by InactiveExpirer");
            }
        });
    }

    @Override
    protected boolean isExpired(Node node) {
        return    super.isExpired(node)
               || node.allocation().get().owner().instance().isTester();
    }

    private static boolean retiredByOperator(Node node) {
        return node.status().wantToRetire() && node.history().event(History.Event.Type.wantToRetire)
                                                   .map(History.Event::agent)
                                                   .map(agent -> agent == Agent.operator)
                                                   .orElse(false);
    }

}
