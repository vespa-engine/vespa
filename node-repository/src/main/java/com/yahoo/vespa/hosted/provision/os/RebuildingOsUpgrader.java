// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.flags.IntFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * An upgrader that retires and rebuilds hosts on stale OS versions.
 *
 * - We limit the number of concurrent rebuilds to reduce impact of retiring too many hosts.
 * - We limit rebuilds by cluster so that at most one node per stateful cluster per application is retired at a time.
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
        NodeList allNodes = nodeRepository.nodes().list();
        Instant now = nodeRepository.clock().instant();
        rebuildableHosts(target, allNodes).forEach(host -> rebuild(host, target.version(), now));
    }

    @Override
    public void disableUpgrade(NodeType type) {
        // No action needed in this implementation. Hosts that have started rebuilding cannot be halted
    }

    private List<Node> rebuildableHosts(OsVersionTarget target, NodeList allNodes) {
        NodeList hostsOfTargetType = allNodes.nodeType(target.nodeType());
        NodeList activeHosts = hostsOfTargetType.state(Node.State.active);
        int upgradeLimit = Math.max(0, maxRebuilds.value() - hostsOfTargetType.rebuilding().size());

        // Find stateful clusters with retiring nodes
        NodeList activeNodes = allNodes.state(Node.State.active);
        Set<ClusterKey> retiringClusters = statefulClustersOf(activeNodes.nodeType(target.nodeType().childNodeType())
                                                                         .retiring());

        // Upgrade hosts not running stateful clusters that are already retiring
        List<Node> hostsToUpgrade = new ArrayList<>(upgradeLimit);
        NodeList candidates = activeHosts.not().rebuilding()
                                         .osVersionIsBefore(target.version())
                                         .byIncreasingOsVersion();
        for (Node host : candidates) {
            if (hostsToUpgrade.size() == upgradeLimit) break;
            Set<ClusterKey> clustersOnHost = statefulClustersOf(activeNodes.childrenOf(host));
            boolean canUpgrade = Collections.disjoint(retiringClusters, clustersOnHost);
            if (canUpgrade) {
                hostsToUpgrade.add(host);
                retiringClusters.addAll(clustersOnHost);
            }
        }
        return Collections.unmodifiableList(hostsToUpgrade);
    }

    private void rebuild(Node host, Version target, Instant now) {
        LOG.info("Retiring and rebuilding " + host + ": On stale OS version " +
                 host.status().osVersion().current().map(Version::toFullString).orElse("<unset>") +
                 ", want " + target);
        nodeRepository.nodes().rebuild(host.hostname(), Agent.RebuildingOsUpgrader, now);
        nodeRepository.nodes().upgradeOs(NodeListFilter.from(host), Optional.of(target));
    }

    private static Set<ClusterKey> statefulClustersOf(NodeList nodes) {
        Set<ClusterKey> clusters = new HashSet<>();
        for (Node node : nodes) {
            if (node.type().isHost()) throw new IllegalArgumentException("All nodes must be children, got host " + node);
            if (node.allocation().isEmpty()) continue;
            Allocation allocation = node.allocation().get();
            if (!allocation.membership().cluster().isStateful()) continue;
            clusters.add(new ClusterKey(allocation.owner(), allocation.membership().cluster().id()));
        }
        return clusters;
    }

    private static class ClusterKey {

        private final ApplicationId application;
        private final ClusterSpec.Id cluster;

        public ClusterKey(ApplicationId application, ClusterSpec.Id cluster) {
            this.application = Objects.requireNonNull(application);
            this.cluster = Objects.requireNonNull(cluster);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClusterKey that = (ClusterKey) o;
            return application.equals(that.application) && cluster.equals(that.cluster);
        }

        @Override
        public int hashCode() {
            return Objects.hash(application, cluster);
        }

        @Override
        public String toString() {
            return cluster + " of " + application;
        }

    }

}
