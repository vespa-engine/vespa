// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Duration;
import java.time.Clock;
import java.util.List;

/**
 * Maintenance job which moves reserved nodes to dirty after timeout.
 * Nodes need to time out in case someone reserves nodes (by calling prepare) but never commits.
 * reserved nodes may in some cases come from the inactive state, in which case they are dirty.
 * For this reason, all reserved nodes go through the dirty state before going back to ready.
 *
 * @author bratseth
 */
public class ReservationExpirer extends Expirer {

    private final NodeRepository nodeRepository;

    public ReservationExpirer(NodeRepository nodeRepository, Clock clock, Duration reservationPeriod, 
                              JobControl jobControl) {
        super(Node.State.reserved, History.Event.Type.reserved, nodeRepository, clock, reservationPeriod, jobControl);
        this.nodeRepository = nodeRepository;
    }

    @Override
    protected void expire(List<Node> expired) { nodeRepository.setDirty(expired, Agent.system, "Expired by ReservationExpirer"); }

}
