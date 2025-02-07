// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Duration;
import java.util.List;

/**
 * This moves nodes of type {@link NodeType#host} from provisioned to parked if they have been in provisioned too long.
 * Parked hosts are deprovisioned as well, if too many hosts are being expired.
 *
 * Only {@link NodeType#host} is moved because any number of nodes of that type can exist. Other node types such as
 * {@link NodeType#confighost} have a fixed number and thus cannot be replaced while the fixed number of nodes exist in
 * any state.
 *
 * @author freva
 */
public class ProvisionedExpirer extends Expirer {

    private static final int MAXIMUM_ALLOWED_EXPIRED_HOSTS = 5;

    private final NodeRepository nodeRepository;

    ProvisionedExpirer(NodeRepository nodeRepository, Duration expiryTime, Metric metric) {
        super(Node.State.provisioned, History.Event.Type.provisioned, nodeRepository, expiryTime, metric);
        this.nodeRepository = nodeRepository;
    }

    @Override
    protected void expire(List<Node> expired) {
        int previouslyExpired = numberOfPreviouslyExpired();
        for (Node expiredNode : expired) {
            if (expiredNode.type() != NodeType.host)
                continue;
            boolean forceDeprovision = MAXIMUM_ALLOWED_EXPIRED_HOSTS < ++previouslyExpired;
            // Deallocate all children of the expired node to avoid them being stuck in dirty
            nodeRepository.nodes().list().childrenOf(expiredNode.hostname())
                    .forEach(child -> nodeRepository.nodes().deallocate(child, Agent.ProvisionedExpirer,
                            "Parent host is stuck in provisioned"));
            nodeRepository().nodes().parkRecursively(expiredNode.hostname(), Agent.ProvisionedExpirer,
                    forceDeprovision, "Node is stuck in provisioned");
        }
    }

    private int numberOfPreviouslyExpired() {
        return nodeRepository.nodes()
                .list(Node.State.parked)
                .nodeType(NodeType.host)
                .matching(this::parkedByProvisionedExpirer)
                .not().deprovisioning()
                .size();
    }

    private boolean parkedByProvisionedExpirer(Node node) {
        return node.history().event(History.Event.Type.parked)
                .map(History.Event::agent)
                .map(Agent.ProvisionedExpirer::equals)
                .orElse(false);
    }
}
