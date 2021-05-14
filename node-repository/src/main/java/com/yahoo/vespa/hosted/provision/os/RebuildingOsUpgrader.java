// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.flags.IntFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * An upgrader that retires and rebuilds hosts on stale OS versions.
 *
 * - We limit the number of concurrent rebuilds to reduce impact of retiring too many hosts.
 * - We distribute rebuilds equally among all host flavors to preserve free capacity for deployments.
 *
 * Used in cases where performing an OS upgrade requires rebuilding the host, e.g. when upgrading across major versions.
 *
 * @author mpolden
 */
public class RebuildingOsUpgrader implements OsUpgrader {

    private static final Logger LOG = Logger.getLogger(RebuildingOsUpgrader.class.getName());

    private final NodeRepository nodeRepository;
    private final IntFlag maxRebuilds;

    public RebuildingOsUpgrader(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
        this.maxRebuilds = PermanentFlags.MAX_REBUILDS.bindTo(nodeRepository.flagSource());
    }

    @Override
    public void upgradeTo(OsVersionTarget target) {
        NodeList allNodesOfType = nodeRepository.nodes().list().nodeType(target.nodeType());
        Instant now = nodeRepository.clock().instant();
        List<Node> rebuildableNodes = rebuildableNodes(target.version(), allNodesOfType);
        rebuildableNodes.forEach(node -> rebuild(node, target.version(), now));
    }

    @Override
    public void disableUpgrade(NodeType type) {
        // No action needed in this implementation. Hosts that have started rebuilding cannot be halted
    }

    private List<Node> rebuildableNodes(Version target, NodeList allNodesOfType) {
        int upgradeLimit = Math.max(0, maxRebuilds.value() - allNodesOfType.rebuilding().size());

        // Nodes grouped by flavor, sorted descending by group count
        List<List<Node>> nodeGroups = allNodesOfType.state(Node.State.active)
                                                    .not().rebuilding()
                                                    .osVersionIsBefore(target)
                                                    .byIncreasingOsVersion()
                                                    .asList()
                                                    .stream()
                                                    .collect(Collectors.groupingBy(Node::flavor))
                                                    .values().stream()
                                                    .sorted(Comparator.<List<Node>, Integer>comparing(List::size).reversed())
                                                    .collect(Collectors.toList());

        // Pick one node from each group until limit is fulfilled or we exhaust nodes to upgrade
        List<Node> nodesToUpgrade = new ArrayList<>(upgradeLimit);
        int emptyNodeGroups = 0;
        while (nodesToUpgrade.size() < upgradeLimit && emptyNodeGroups < nodeGroups.size()) {
            for (List<Node> nodeGroup : nodeGroups) {
                if (nodeGroup.isEmpty()) {
                    emptyNodeGroups++;
                } else if (nodesToUpgrade.size() < upgradeLimit) {
                    nodesToUpgrade.add(nodeGroup.remove(0));
                }
            }
        }

        return Collections.unmodifiableList(nodesToUpgrade);
    }

    private void rebuild(Node host, Version target, Instant now) {
        LOG.info("Retiring and rebuilding " + host + ": On stale OS version " +
                 host.status().osVersion().current().map(Version::toFullString).orElse("<unset>") +
                 ", want " + target);
        nodeRepository.nodes().rebuild(host.hostname(), Agent.RebuildingOsUpgrader, now);
        nodeRepository.nodes().upgradeOs(NodeListFilter.from(host), Optional.of(target));
    }

}
