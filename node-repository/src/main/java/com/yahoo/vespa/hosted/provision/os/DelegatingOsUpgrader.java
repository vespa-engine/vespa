// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * An upgrader that delegates the upgrade to the node itself, triggered by changing its wanted OS version. This
 * implementation limits the number of parallel upgrades to avoid overloading the orchestrator with suspension requests.
 *
 * Used in clouds where nodes can upgrade themselves in-place, without data loss.
 *
 * @author mpolden
 */
public class DelegatingOsUpgrader implements OsUpgrader {

    private static final Logger LOG = Logger.getLogger(DelegatingOsUpgrader.class.getName());

    private final NodeRepository nodeRepository;

    /** The maximum number of nodes, within a single node type, that can upgrade in parallel. */
    private final int maxActiveUpgrades;

    public DelegatingOsUpgrader(NodeRepository nodeRepository, int maxActiveUpgrades) {
        this.nodeRepository = Objects.requireNonNull(nodeRepository);
        this.maxActiveUpgrades = maxActiveUpgrades;
        if (maxActiveUpgrades < 1) throw new IllegalArgumentException("maxActiveUpgrades must be positive, was " +
                                                                      maxActiveUpgrades);
    }

    @Override
    public void upgradeTo(OsVersionTarget target) {
        NodeList activeNodes = nodeRepository.nodes().list(Node.State.active).nodeType(target.nodeType());
        int numberToUpgrade = Math.max(0, maxActiveUpgrades - activeNodes.changingOsVersionTo(target.version()).size());
        Instant now = nodeRepository.clock().instant();
        NodeList nodesToUpgrade = activeNodes.not().changingOsVersionTo(target.version())
                                             .osVersionIsBefore(target.version())
                                             .matching(node -> canUpgradeAt(now, node))
                                             .byIncreasingOsVersion()
                                             .first(numberToUpgrade);
        if (nodesToUpgrade.size() == 0) return;
        LOG.info("Upgrading " + nodesToUpgrade.size() + " nodes of type " + target.nodeType() + " to OS version " +
                 target.version().toFullString());
        nodeRepository.nodes().upgradeOs(NodeListFilter.from(nodesToUpgrade.asList()), Optional.of(target.version()));
    }

    @Override
    public void disableUpgrade(NodeType type) {
        NodeList nodesUpgrading = nodeRepository.nodes().list()
                                                .nodeType(type)
                                                .changingOsVersion();
        if (nodesUpgrading.size() == 0) return;
        LOG.info("Disabling OS upgrade of all " + type + " nodes");
        nodeRepository.nodes().upgradeOs(NodeListFilter.from(nodesUpgrading.asList()), Optional.empty());
    }

}
