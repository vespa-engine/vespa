// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;

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

    private final boolean wantToDeprovisionOnExpiry;

    DirtyExpirer(NodeRepository nodeRepository, Duration dirtyTimeout, Metric metric) {
        super(Node.State.dirty, History.Event.Type.deallocated, nodeRepository, dirtyTimeout, metric);
        // Deprovision hosts on expiry if dynamically provisioned
        this.wantToDeprovisionOnExpiry = nodeRepository.zone().cloud().dynamicProvisioning();
    }

    @Override
    protected void expire(List<Node> expired) {
        for (Node expiredNode : expired)
            nodeRepository().nodes().fail(expiredNode.hostname(), wantToDeprovisionOnExpiry, Agent.DirtyExpirer, "Node is stuck in dirty");
    }

}
