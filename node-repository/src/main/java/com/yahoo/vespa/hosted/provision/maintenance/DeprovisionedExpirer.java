// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Duration;
import java.util.List;

/**
 * This removes hosts from {@link com.yahoo.vespa.hosted.provision.Node.State#deprovisioned}, after a grace period.
 *
 * @author mpolden
 */
public class DeprovisionedExpirer extends Expirer {

    DeprovisionedExpirer(NodeRepository nodeRepository, Duration expiryTime, Metric metric) {
        super(Node.State.deprovisioned, History.Event.Type.deprovisioned, nodeRepository, expiryTime, metric);
    }

    @Override
    protected void expire(List<Node> expired) {
        if (!nodeRepository().zone().cloud().dynamicProvisioning()) return; // Never expire in statically provisioned zones
        for (var node : expired) {
            nodeRepository().nodes().forget(node);
        }
    }

}
