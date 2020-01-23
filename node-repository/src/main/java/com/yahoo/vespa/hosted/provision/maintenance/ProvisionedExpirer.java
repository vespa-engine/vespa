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
 * This moves nodes from provisioned to parked if they have been in provisioned too long.
 *
 * @author freva
 */
public class ProvisionedExpirer extends Expirer {

    private final NodeRepository nodeRepository;

    ProvisionedExpirer(NodeRepository nodeRepository, Clock clock, Duration dirtyTimeout) {
        super(Node.State.provisioned, History.Event.Type.provisioned, nodeRepository, clock, dirtyTimeout);
        this.nodeRepository = nodeRepository;
    }

    @Override
    protected void expire(List<Node> expired) {
        for (Node expiredNode : expired)
            nodeRepository.parkRecursively(expiredNode.hostname(), Agent.ProvisionedExpirer, "Node is stuck in provisioned");
    }

}
