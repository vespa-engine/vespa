// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    private final NodeRepository nodeRepository;
    private static final int MAXIMUM_ALLOWED_EXPIRED_HOSTS = 20;

    ProvisionedExpirer(NodeRepository nodeRepository, Duration timeout, Metric metric) {
        super(Node.State.provisioned, History.Event.Type.provisioned, nodeRepository, timeout, metric);
        this.nodeRepository = nodeRepository;
    }

    @Override
    protected void expire(List<Node> expired) {
        int previouslyExpired = numberOfPreviouslyExpired();
        for (Node expiredNode : expired) {
            if (expiredNode.type() != NodeType.host)
                continue;
            nodeRepository().nodes().parkRecursively(expiredNode.hostname(), Agent.ProvisionedExpirer, "Node is stuck in provisioned");
            if (MAXIMUM_ALLOWED_EXPIRED_HOSTS < ++previouslyExpired) {
                nodeRepository.nodes().deprovision(expiredNode.hostname(), Agent.ProvisionedExpirer, nodeRepository.clock().instant());
            }
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
