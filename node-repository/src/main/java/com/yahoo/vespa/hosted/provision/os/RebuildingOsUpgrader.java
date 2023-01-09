// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.ClusterId;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * An upgrader that rebuilds hosts on stale OS versions.
 *
 * - We limit the number of concurrent rebuilds to reduce impact of suspending or retiring too many hosts.
 * - We limit rebuilds by cluster so that at most one node per stateful cluster per application is rebuilt at a time.
 *
 * Used in cases where performing an OS upgrade requires rebuilding the host, e.g. when upgrading across major versions.
 *
 * @author mpolden
 */
public class RebuildingOsUpgrader extends OsUpgrader {

    private static final Logger LOG = Logger.getLogger(RebuildingOsUpgrader.class.getName());

    private final boolean softRebuild;

    public RebuildingOsUpgrader(NodeRepository nodeRepository, boolean softRebuild) {
        super(nodeRepository);
        this.softRebuild = softRebuild;
    }

    @Override
    public void upgradeTo(OsVersionTarget target) {
        NodeList allNodes = nodeRepository.nodes().list();
        Instant now = nodeRepository.clock().instant();
        rebuildableHosts(target, allNodes, now).forEach(host -> rebuild(host, target.version(), now));
    }

    @Override
    public void disableUpgrade(NodeType type) {
        // No action needed in this implementation. Hosts that have started rebuilding cannot be halted
    }

    private List<Node> rebuildableHosts(OsVersionTarget target, NodeList allNodes, Instant now) {
        NodeList hostsOfTargetType = allNodes.nodeType(target.nodeType());
        if (softRebuild) {
            // Soft rebuild is enabled so this should act on hosts having replacable root disk
            hostsOfTargetType = hostsOfTargetType.replaceableRootDisk();
        }

        // Find stateful clusters with retiring nodes
        NodeList activeNodes = allNodes.state(Node.State.active);
        Set<ClusterId> retiringClusters = new HashSet<>(activeNodes.nodeType(target.nodeType().childNodeType())
                                                                   .retiring()
                                                                   .statefulClusters());

        // Rebuild hosts not containing stateful clusters with retiring nodes, up to rebuild limit
        NodeList activeHosts = hostsOfTargetType.state(Node.State.active);
        int rebuildLimit = upgradeSlots(target, activeHosts.rebuilding(softRebuild));
        List<Node> hostsToRebuild = new ArrayList<>(rebuildLimit);
        NodeList candidates = activeHosts.not().rebuilding(softRebuild)
                                         .osVersionIsBefore(target.version())
                                         .matching(node -> canUpgradeAt(now, node))
                                         .byIncreasingOsVersion();
        for (Node host : candidates) {
            if (hostsToRebuild.size() == rebuildLimit) break;
            Set<ClusterId> clustersOnHost = activeNodes.childrenOf(host).statefulClusters();
            boolean canRebuild = Collections.disjoint(retiringClusters, clustersOnHost);
            if (canRebuild) {
                hostsToRebuild.add(host);
                retiringClusters.addAll(clustersOnHost);
            }
        }
        return Collections.unmodifiableList(hostsToRebuild);
    }

    private void rebuild(Node host, Version target, Instant now) {
        LOG.info((softRebuild ? "Soft-rebuilding " : "Retiring and rebuilding ") + host + ": On stale OS version " +
                 host.status().osVersion().current().map(Version::toFullString).orElse("<unset>") +
                 ", want " + target);
        nodeRepository.nodes().rebuild(host.hostname(), softRebuild, Agent.RebuildingOsUpgrader, now);
        nodeRepository.nodes().upgradeOs(NodeListFilter.from(host), Optional.of(target));
    }

}
