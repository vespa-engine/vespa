// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;

import java.time.Instant;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * An upgrader that retires and deprovisions hosts on stale OS versions.
 *
 * Used in clouds where hosts must be re-provisioned to upgrade their OS.
 *
 * @author mpolden
 */
public class RetiringOsUpgrader extends OsUpgrader {

    private static final Logger LOG = Logger.getLogger(RetiringOsUpgrader.class.getName());

    private final boolean softRebuild;

    public RetiringOsUpgrader(NodeRepository nodeRepository, boolean softRebuild) {
        super(nodeRepository);
        this.softRebuild = softRebuild;
    }

    @Override
    public void upgradeTo(OsVersionTarget target) {
        NodeList allNodes = nodeRepository.nodes().list();
        Instant now = nodeRepository.clock().instant();
        for (var candidate : candidates(now, target, allNodes)) {
            deprovision(candidate, target.version(), now);
        }
    }

    @Override
    public void disableUpgrade(NodeType type) {
        // No action needed in this implementation.
    }

    /** Returns nodes that are candidates for upgrade */
    private NodeList candidates(Instant instant, OsVersionTarget target, NodeList allNodes) {
        NodeList activeNodes = allNodes.state(Node.State.active).nodeType(target.nodeType());
        if (softRebuild) {
            // Retire only hosts which do not have a replaceable root disk
            activeNodes = activeNodes.not().replaceableRootDisk();
        }
        return activeNodes.not().deprovisioning()
                          .osVersionIsBefore(target.version())
                          .matching(node -> canUpgradeAt(instant, node))
                          .byIncreasingOsVersion()
                          .first(upgradeSlots(target, activeNodes.deprovisioning()));
    }

    /** Upgrade given host by retiring and deprovisioning it */
    private void deprovision(Node host, Version target, Instant now) {
        LOG.info("Retiring and deprovisioning " + host + ": On stale OS version " +
                 host.status().osVersion().current().map(Version::toFullString).orElse("<unset>") +
                 ", want " + target);
        nodeRepository.nodes().deprovision(host.hostname(), Agent.RetiringOsUpgrader, now);
        nodeRepository.nodes().upgradeOs(NodeListFilter.from(host), Optional.of(target));
    }

}
