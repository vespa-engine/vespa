// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;

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
public class DelegatingUpgrader implements Upgrader {

    private static final Logger LOG = Logger.getLogger(DelegatingUpgrader.class.getName());

    private final NodeRepository nodeRepository;

    /** The maximum number of nodes, within a single node type, that can upgrade in parallel. */
    private final int maxActiveUpgrades;

    public DelegatingUpgrader(NodeRepository nodeRepository, int maxActiveUpgrades) {
        this.nodeRepository = Objects.requireNonNull(nodeRepository);
        this.maxActiveUpgrades = maxActiveUpgrades;
    }

    @Override
    public void upgradeTo(OsVersionTarget target) {
        NodeList activeNodes = nodeRepository.nodes().list().nodeType(target.nodeType()).state(Node.State.active);
        int numberToUpgrade = Math.max(0, maxActiveUpgrades - activeNodes.changingOsVersionTo(target.version()).size());
        NodeList nodesToUpgrade = activeNodes.not().changingOsVersionTo(target.version())
                                             .osVersionIsBefore(target.version())
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
