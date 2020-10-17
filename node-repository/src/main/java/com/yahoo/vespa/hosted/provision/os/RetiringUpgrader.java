// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * An upgrader that retires and deprovisions nodes on stale OS versions. Retirement of each node is spread out in time,
 * according to a time budget, to avoid potential service impact of retiring too many nodes close together.
 *
 * Used in clouds where nodes must be re-provisioned to upgrade their OS.
 *
 * @author mpolden
 */
public class RetiringUpgrader implements Upgrader {

    private static final Logger LOG = Logger.getLogger(RetiringUpgrader.class.getName());

    private final NodeRepository nodeRepository;

    public RetiringUpgrader(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    @Override
    public void upgradeTo(OsVersionTarget target) {
        NodeList allNodes = nodeRepository.list();
        NodeList activeNodes = allNodes.state(Node.State.active).nodeType(target.nodeType());
        if (activeNodes.size() == 0) return; // No nodes eligible for upgrade

        Instant now = nodeRepository.clock().instant();
        Duration nodeBudget = target.upgradeBudget()
                                    .orElseThrow(() -> new IllegalStateException("OS upgrades in this zone requires " +
                                                                                 "a time budget, but none is set"))
                                    .dividedBy(activeNodes.size());
        Instant retiredAt = target.lastRetiredAt().orElse(Instant.EPOCH);
        if (now.isBefore(retiredAt.plus(nodeBudget))) return; // Budget has not been spent yet

        activeNodes.osVersionIsBefore(target.version())
                   .not().deprovisioning()
                   .byIncreasingOsVersion()
                   .first(1)
                   .forEach(node -> retire(node, target.version(), now, allNodes));
    }

    @Override
    public void disableUpgrade(NodeType type) {
        // No action needed in this implementation.
    }

    /** Retire and deprovision given host and its children */
    private void retire(Node host, Version target, Instant now, NodeList allNodes) {
        if (!host.type().isHost()) throw new IllegalArgumentException("Cannot retire non-host " + host);
        try (var lock = nodeRepository.lock(host)) {
            Optional<Node> currentNode = nodeRepository.getNode(host.hostname());
            if (currentNode.isEmpty()) return;
            host = currentNode.get();
            NodeType nodeType = host.type();

            LOG.info("Retiring and deprovisioning " + host + ": On stale OS version " +
                     host.status().osVersion().current().map(Version::toFullString).orElse("<unset>") +
                     ", want " + target);
            NodeList children = allNodes.childrenOf(host);
            nodeRepository.retire(NodeListFilter.from(children.asList()), Agent.RetiringUpgrader, now);
            host = host.withWantToRetire(true, true, Agent.RetiringUpgrader, now);
            host = host.with(host.status().withOsVersion(host.status().osVersion().withWanted(Optional.of(target))));
            nodeRepository.write(host, lock);
            nodeRepository.osVersions().writeChange((change) -> change.withRetirementAt(now, nodeType));
        }
    }

}
