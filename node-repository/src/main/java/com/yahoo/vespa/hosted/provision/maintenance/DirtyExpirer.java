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
 * This moves nodes from dirty to failed if they have been in dirty too long
 * with the assumption that a node is stuck in dirty because it has failed.
 * <p>
 * As the nodes are moved back to dirty their failure count is increased,
 * and if the count is sufficiently low they will be attempted recycled to dirty again.
 * The upshot is nodes may get multiple attempts at clearing through dirty, but they will
 * eventually stay in failed.
 *
 * @author bratseth
 */
public class DirtyExpirer extends Expirer {

    private final NodeRepository nodeRepository;

    DirtyExpirer(NodeRepository nodeRepository, Clock clock, Duration dirtyTimeout) {
        super(Node.State.dirty, History.Event.Type.deallocated, nodeRepository, clock, dirtyTimeout);
        this.nodeRepository = nodeRepository;
    }

    @Override
    protected void expire(List<Node> expired) {
        for (Node expiredNode : expired)
            nodeRepository.fail(expiredNode.hostname(), Agent.DirtyExpirer, "Node is stuck in dirty");
    }

}
