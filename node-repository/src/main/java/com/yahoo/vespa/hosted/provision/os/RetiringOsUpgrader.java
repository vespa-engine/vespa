// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ClusterSpec;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * An upgrader that retires and deprovisions hosts on stale OS versions. For hosts containing stateful clusters, this
 * upgrader limits node retirement so that at most one group per cluster is affected at a time.
 *
 * Used in clouds where the host configuration (e.g. local disk) requires re-provisioning to upgrade OS.
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
        for (Node host : deprovisionable(now, target, allNodes)) {
            deprovision(host, target.version(), now);
        }
    }

    @Override
    public void disableUpgrade(NodeType type) {
        // No action needed in this implementation.
    }

    /** Returns nodes that can be deprovisioned at given instant */
    private List<Node> deprovisionable(Instant instant, OsVersionTarget target, NodeList allNodes) {
        NodeList nodes = allNodes.state(Node.State.active, Node.State.provisioned).nodeType(target.nodeType());
        if (softRebuild) {
            // Consider only hosts which do not have a replaceable root disk
            nodes = nodes.not().remoteStorage();
        }
        // Retire hosts up to slot limit while ensuring that only one group is retired at a time
        NodeList activeNodes = allNodes.state(Node.State.active);
        Map<ClusterId, Set<ClusterSpec.Group>> retiringGroupsByCluster = groupsOf(activeNodes.retiring());
        int limit = upgradeSlots(target, nodes.deprovisioning());
        List<Node> result = new ArrayList<>();
        NodeList candidates = nodes.not().deprovisioning()
                                   .not().onOsVersion(target.version())
                                   .matching(node -> canUpgradeTo(target.version(), instant, node))
                                   .byIncreasingOsVersion();
        for (Node host : candidates) {
            if (result.size() == limit) break;
            // For all clusters residing on this host: Determine if deprovisioning the host would imply retiring nodes
            // in additional groups beyond those already having retired nodes. If true, defer deprovisioning the host
            boolean canDeprovision = true;
            Map<ClusterId, Set<ClusterSpec.Group>> groupsOnHost = groupsOf(activeNodes.childrenOf(host));
            for (var clusterAndGroups : groupsOnHost.entrySet()) {
                Set<ClusterSpec.Group> groups = clusterAndGroups.getValue();
                Set<ClusterSpec.Group> retiringGroups = retiringGroupsByCluster.get(clusterAndGroups.getKey());
                if (retiringGroups != null && !groups.equals(retiringGroups)) {
                    canDeprovision = false;
                    break;
                }
            }
            // Deprovision host and count all cluster groups on the host as being retired
            if (canDeprovision) {
                result.add(host);
                groupsOnHost.forEach((cluster, groups) -> retiringGroupsByCluster.merge(cluster, groups, (oldVal, newVal) -> {
                    oldVal.addAll(newVal);
                    return oldVal;
                }));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Upgrade given host by retiring and deprovisioning it */
    private void deprovision(Node host, Version target, Instant now) {
        LOG.info("Retiring and deprovisioning " + host + ": On stale OS version " +
                 host.status().osVersion().current().map(Version::toFullString).orElse("<unset>") +
                 ", want " + target);
        nodeRepository.nodes().deprovision(host.hostname(), Agent.RetiringOsUpgrader, now);
        nodeRepository.nodes().upgradeOs(NodeListFilter.from(host), Optional.of(target));
    }

    /** Returns the stateful groups present on given nodes, grouped by their cluster ID */
    private static Map<ClusterId, Set<ClusterSpec.Group>> groupsOf(NodeList nodes) {
        return nodes.stream()
                    .filter(node -> node.allocation().isPresent() &&
                                    node.allocation().get().membership().cluster().isStateful() &&
                                    node.allocation().get().membership().cluster().group().isPresent())
                    .collect(Collectors.groupingBy(node -> new ClusterId(node.allocation().get().owner(),
                                                                         node.allocation().get().membership().cluster().id()),
                                                   HashMap::new,
                                                   Collectors.mapping(n -> n.allocation().get().membership().cluster().group().get(),
                                                                      Collectors.toCollection(HashSet::new))));
    }

}
