// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.ClusterId;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author mpolden
 */
public class HostRenamer extends NodeRepositoryMaintainer {

    private static final Logger LOG = Logger.getLogger(HostRenamer.class.getName());

    private final StringFlag hostnameSchemeFlag;

    public HostRenamer(NodeRepository nodeRepository, Duration interval, Metric metric) {
        super(nodeRepository, interval, metric);
        this.hostnameSchemeFlag = Flags.HOSTNAME_SCHEME.bindTo(nodeRepository.flagSource());
    }

    record ClusterGroup(ClusterId clusterId, Integer groupIndex){}

    @Override
    protected double maintain() {
        if (!nodeRepository().nodes().isWorking()) return 0.0;
        NodeList allNodes = nodeRepository().nodes().list();
        NodeList activeHosts = allNodes.nodeType(NodeType.host).state(Node.State.active);
        Set<ClusterGroup> retiringClusterGroups = applicationsOnRetiringHosts(activeHosts, allNodes);
        for (var host : activeHosts) {
            Set<ClusterGroup> clusterGroupsOnHost = applicationsGroupsOn(host, allNodes);
            if (!changeHostname(host, clusterGroupsOnHost)) continue;

            if (Collections.disjoint(retiringClusterGroups, clusterGroupsOnHost)) {
                LOG.info("Deprovisioning " + host + " to change its hostname");
                nodeRepository().nodes().deprovision(host.hostname(), Agent.system, nodeRepository().clock().instant());
                retiringClusterGroups.addAll(clusterGroupsOnHost);
            }
        }
        return 1.0;
    }

    private Set<ClusterGroup> applicationsGroupsOn(Node host, NodeList allNodes) {
        Set<ClusterGroup> clusterGroups = new HashSet<>();
        for (var child : allNodes.childrenOf(host)) {
            Allocation allocation = child.allocation().orElseThrow();
            clusterGroups.add(new ClusterGroup(
                    new ClusterId(allocation.owner(), allocation.membership().cluster().id()),
                    allocation.membership().cluster().group().map(ClusterSpec.Group::index).orElse(0)));
        }
        return clusterGroups;
    }

    private Set<ClusterGroup> applicationsOnRetiringHosts(NodeList activeHosts, NodeList allNodes) {
        Set<ClusterGroup> applications = new HashSet<>();
        for (var host : activeHosts.retiring()) {
            applications.addAll(applicationsGroupsOn(host, allNodes));
        }
        return applications;
    }

    private boolean changeHostname(Node node, Set<ClusterGroup> clusterGroups) {
        if (node.hostname().endsWith(".vespa-cloud.net")) {
            return false;
        }
        Set<String> wantedSchemes;
        if (clusterGroups.isEmpty()) {
            wantedSchemes = Set.of(hostnameSchemeFlag.value());
        } else {
            wantedSchemes = clusterGroups.stream()
                                     .map(clusterGroup -> hostnameSchemeFlag.withApplicationId(
                                             Optional.of(clusterGroup.clusterId().application())).value())
                                     .collect(Collectors.toSet());
        }
        return wantedSchemes.size() == 1 && wantedSchemes.iterator().next().equals("standard");
    }

}
