// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeResources;
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
 * An upgrader that retires and deprovisions hosts on stale OS versions.
 *
 * Used in clouds where hosts must be re-provisioned to upgrade their OS.
 *
 * @author mpolden
 */
public class RetiringOsUpgrader implements OsUpgrader {

    private static final Logger LOG = Logger.getLogger(RetiringOsUpgrader.class.getName());

    protected final NodeRepository nodeRepository;

    private final boolean softRebuild;
    private final int maxActiveUpgrades;

    public RetiringOsUpgrader(NodeRepository nodeRepository, boolean softRebuild, int maxActiveUpgrades) {
        this.nodeRepository = nodeRepository;
        this.softRebuild = softRebuild;
        this.maxActiveUpgrades = maxActiveUpgrades;
        if (maxActiveUpgrades < 1) throw new IllegalArgumentException("maxActiveUpgrades must be positive, was " +
                                                                      maxActiveUpgrades);
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

    @Override
    public boolean canUpgradeAt(Instant instant, Node node) {
        return node.history().age(instant).compareTo(gracePeriod()) > 0;
    }

    /** Returns nodes that are candidates for upgrade */
    private NodeList candidates(Instant instant, OsVersionTarget target, NodeList allNodes) {
        NodeList activeNodes = allNodes.state(Node.State.active).nodeType(target.nodeType());
        if (softRebuild) {
            // Soft rebuild is enabled, so this should only act on hosts with local storage, or non-x86-64
            activeNodes = activeNodes.matching(node -> node.resources().storageType() == NodeResources.StorageType.local ||
                                                       node.resources().architecture() != NodeResources.Architecture.x86_64);
        }
        if (activeNodes.isEmpty()) return NodeList.of();

        int numberToDeprovision = Math.max(0, maxActiveUpgrades - activeNodes.deprovisioning().size());
        return activeNodes.not().deprovisioning()
                          .osVersionIsBefore(target.version())
                          .matching(node -> canUpgradeAt(instant, node))
                          .byIncreasingOsVersion()
                          .first(numberToDeprovision);
    }

    /** Upgrade given host by retiring and deprovisioning it */
    private void deprovision(Node host, Version target, Instant now) {
        LOG.info("Retiring and deprovisioning " + host + ": On stale OS version " +
                 host.status().osVersion().current().map(Version::toFullString).orElse("<unset>") +
                 ", want " + target);
        nodeRepository.nodes().deprovision(host.hostname(), Agent.RetiringOsUpgrader, now);
        nodeRepository.nodes().upgradeOs(NodeListFilter.from(host), Optional.of(target));
    }

    /** The duration this leaves new nodes alone before scheduling any upgrade */
    private Duration gracePeriod() {
        return Duration.ofDays(1);
    }

}
