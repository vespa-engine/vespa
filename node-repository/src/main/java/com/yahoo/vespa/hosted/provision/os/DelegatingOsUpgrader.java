// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;

import java.time.Instant;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * An upgrader that delegates the upgrade to the node itself, triggered by changing its wanted OS version. Downgrades
 * are not supported.
 *
 * Used in clouds where nodes can upgrade themselves in-place, without data loss.
 *
 * @author mpolden
 */
public class DelegatingOsUpgrader extends OsUpgrader {

    private static final Logger LOG = Logger.getLogger(DelegatingOsUpgrader.class.getName());

    public DelegatingOsUpgrader(NodeRepository nodeRepository) {
        super(nodeRepository);
    }

    @Override
    public void upgradeTo(OsVersionTarget target) {
        NodeList activeNodes = nodeRepository.nodes().list(Node.State.active).nodeType(target.nodeType());
        Instant now = nodeRepository.clock().instant();
        NodeList nodesToUpgrade = activeNodes.not().changingOsVersionTo(target.version())
                                             // This upgrader cannot downgrade nodes. We therefore consider only nodes
                                             // on a lower version than the target
                                             .osVersionIsBefore(target.version())
                                             .matching(node -> canUpgradeAt(now, node))
                                             .byIncreasingOsVersion()
                                             .first(upgradeSlots(target, activeNodes));
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
