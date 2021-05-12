// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
 * An upgrader that retires and rebuilds hosts on stale OS versions. We limit the number of concurrent rebuilds to
 * reduce impact of retiring too many hosts at once.
 *
 * Used in cases where performing an OS upgrade requires rebuilding the host, e.g. when upgrading across major versions.
 *
 * @author mpolden
 */
public class RebuildingOsUpgrader implements OsUpgrader {

    private static final Logger LOG = Logger.getLogger(RebuildingOsUpgrader.class.getName());

    private final NodeRepository nodeRepository;
    private final int maxRebuilds;

    public RebuildingOsUpgrader(NodeRepository nodeRepository, int maxRebuilds) {
        this.nodeRepository = nodeRepository;
        this.maxRebuilds = maxRebuilds;
        if (maxRebuilds < 1) throw new IllegalArgumentException("maxRebuilds must be positive, was " + maxRebuilds);
    }

    @Override
    public void upgradeTo(OsVersionTarget target) {
        NodeList allNodesOfType = nodeRepository.nodes().list().nodeType(target.nodeType());
        NodeList activeNodes = allNodesOfType.state(Node.State.active);
        int numberToUpgrade = Math.max(0, maxRebuilds - allNodesOfType.rebuilding().size());
        NodeList nodesToUpgrade = activeNodes.not().rebuilding()
                                             .osVersionIsBefore(target.version())
                                             .byIncreasingOsVersion()
                                             .first(numberToUpgrade);
        Instant now = nodeRepository.clock().instant();
        nodesToUpgrade.forEach(node -> rebuild(node, target.version(), now));
    }

    @Override
    public void disableUpgrade(NodeType type) {
        // No action needed in this implementation. Hosts that have started rebuilding cannot be halted
    }

    private void rebuild(Node host, Version target, Instant now) {
        LOG.info("Retiring and rebuilding " + host + ": On stale OS version " +
                 host.status().osVersion().current().map(Version::toFullString).orElse("<unset>") +
                 ", want " + target);
        nodeRepository.nodes().rebuild(host.hostname(), Agent.RebuildingOsUpgrader, now);
        nodeRepository.nodes().upgradeOs(NodeListFilter.from(host), Optional.of(target));
    }

}
