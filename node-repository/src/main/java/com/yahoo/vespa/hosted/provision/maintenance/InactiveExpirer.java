// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Maintenance job which moves inactive nodes to dirty or parked after timeout.
 *
 * The timeout is in place for two reasons:
 * <ul>
 * <li>To ensure that the new application configuration has time to
 * propagate before the node is used for something else
 * <li>To provide a grace period in which nodes can be brought back to active
 * if they were deactivated in error. As inactive nodes retain their state
 * they can be brought back to active and correct state faster than a new node.
 * </ul>
 *
 * Nodes with the retired flag should not be reused and will be moved to parked instead of dirty.
 *
 * @author bratseth
 * @author mpolden
 */
public class InactiveExpirer extends Expirer {

    private final NodeRepository nodeRepository;

    public InactiveExpirer(NodeRepository nodeRepository, Clock clock, Duration inactiveTimeout, JobControl jobControl) {
        super(Node.State.inactive, History.Event.Type.deactivated, nodeRepository, clock, inactiveTimeout, jobControl);
        this.nodeRepository = nodeRepository;
    }

    @Override
    protected void expire(List<Node> expired) {
        expired.forEach(node -> {
            if (node.status().wantToRetire()) {
                nodeRepository.park(node.hostname(), Agent.system, "Expired by InactiveExpirer");
            } else {
                nodeRepository.setDirty(node, Agent.system, "Expired by InactiveExprier");
            }
        });
    }

}
