// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Duration;
import java.util.List;

/**
 * This removes hosts from {@link com.yahoo.vespa.hosted.provision.Node.State#deprovisioned}, in dynamically provisioned
 * zones, after a grace period.
 *
 * @author mpolden
 */
public class DeprovisionedExpirer extends Expirer {

    DeprovisionedExpirer(NodeRepository nodeRepository, Duration expiryTime, Metric metric) {
        super(Node.State.deprovisioned, History.Event.Type.deprovisioned, nodeRepository, expiryTime, metric);
    }

    @Override
    protected boolean isExpired(Node node) {
        return nodeRepository().zone().cloud().dynamicProvisioning() &&
               super.isExpired(node);
    }

    @Override
    protected void expire(List<Node> expired) {
        for (var node : expired) {
            nodeRepository().nodes().forget(node);
        }
    }

}
