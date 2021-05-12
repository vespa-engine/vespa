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
 * An upgrader that retires and deprovisions hosts on stale OS versions. Retirement of each host is spread out in time,
 * according to a time budget, to avoid potential service impact of retiring too many hosts close together.
 *
 * Used in clouds where hosts must be re-provisioned to upgrade their OS.
 *
 * @author mpolden
 */
public class RetiringOsUpgrader implements OsUpgrader {

    private static final Logger LOG = Logger.getLogger(RetiringOsUpgrader.class.getName());

    protected final NodeRepository nodeRepository;

    public RetiringOsUpgrader(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    @Override
    public final void upgradeTo(OsVersionTarget target) {
        NodeList allNodes = nodeRepository.nodes().list();
        Instant now = nodeRepository.clock().instant();
        NodeList candidates = candidates(now, target, allNodes);
        candidates.not().deprovisioning()
                  .byIncreasingOsVersion()
                  .first(1)
                  .forEach(node -> deprovision(node, target.version(), now));
    }

    @Override
    public final void disableUpgrade(NodeType type) {
        // No action needed in this implementation.
    }

    /** Returns nodes that are candidates for upgrade */
    private NodeList candidates(Instant instant, OsVersionTarget target, NodeList allNodes) {
        NodeList activeNodes = allNodes.state(Node.State.active).nodeType(target.nodeType());
        if (activeNodes.isEmpty()) return NodeList.of();

        Duration nodeBudget = target.upgradeBudget().dividedBy(activeNodes.size());
        Instant retiredAt = target.lastRetiredAt().orElse(Instant.EPOCH);
        if (instant.isBefore(retiredAt.plus(nodeBudget))) return NodeList.of(); // Budget has not been spent yet

        return activeNodes.osVersionIsBefore(target.version());
    }

    /** Upgrade given host by retiring and deprovisioning it */
    private void deprovision(Node host, Version target, Instant now) {
        LOG.info("Retiring and deprovisioning " + host + ": On stale OS version " +
                 host.status().osVersion().current().map(Version::toFullString).orElse("<unset>") +
                 ", want " + target);
        nodeRepository.nodes().deprovision(host.hostname(), Agent.RetiringUpgrader, now);
        nodeRepository.nodes().upgradeOs(NodeListFilter.from(host), Optional.of(target));
        nodeRepository.osVersions().writeChange((change) -> change.withRetirementAt(now, host.type()));
    }

}
