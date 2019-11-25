// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Builds up data structures necessary for node prioritization. It wraps each node
 * up in a PrioritizableNode object with attributes used in sorting.
 *
 * The actual sorting/prioritization is implemented in the PrioritizableNode class as a compare method.
 *
 * @author smorgrav
 */
public class NodePrioritizer {

    /** Node states in which host can get new nodes allocated in, ordered by preference (ascending) */
    public static final List<Node.State> ALLOCATABLE_HOST_STATES =
            List.of(Node.State.provisioned, Node.State.ready, Node.State.active);
    private final static Logger log = Logger.getLogger(NodePrioritizer.class.getName());

    private final Map<Node, PrioritizableNode> nodes = new HashMap<>();
    private final LockedNodeList allNodes;
    private final DockerHostCapacity capacity;
    private final NodeSpec requestedNodes;
    private final ApplicationId appId;
    private final ClusterSpec clusterSpec;
    private final NameResolver nameResolver;
    private final boolean isDocker;
    private final boolean isAllocatingForReplacement;
    private final Set<Node> spareHosts;

    NodePrioritizer(LockedNodeList allNodes, ApplicationId appId, ClusterSpec clusterSpec, NodeSpec nodeSpec,
                    int spares, NameResolver nameResolver, HostResourcesCalculator hostResourcesCalculator) {
        this.allNodes = allNodes;
        this.capacity = new DockerHostCapacity(allNodes, hostResourcesCalculator);
        this.requestedNodes = nodeSpec;
        this.clusterSpec = clusterSpec;
        this.appId = appId;
        this.nameResolver = nameResolver;
        this.spareHosts = findSpareHosts(allNodes, capacity, spares);

        int nofFailedNodes = (int) allNodes.asList().stream()
                                           .filter(node -> node.state().equals(Node.State.failed))
                                           .filter(node -> node.allocation().isPresent())
                                           .filter(node -> node.allocation().get().owner().equals(appId))
                                           .filter(node -> node.allocation().get().membership().cluster().id().equals(clusterSpec.id()))
                                           .count();

        int nofNodesInCluster = (int) allNodes.asList().stream()
                                              .filter(node -> node.allocation().isPresent())
                                              .filter(node -> node.allocation().get().owner().equals(appId))
                                              .filter(node -> node.allocation().get().membership().cluster().id().equals(clusterSpec.id()))
                                              .count();

        this.isAllocatingForReplacement = isReplacement(nofNodesInCluster, nofFailedNodes);
        this.isDocker = isDocker();
    }

    /**
     * Spare hosts are the two hosts in the system with the most free capacity.
     *
     * We do not count retired or inactive nodes as used capacity (as they could have been
     * moved to create space for the spare node in the first place).
     */
    private static Set<Node> findSpareHosts(LockedNodeList nodes, DockerHostCapacity capacity, int spares) {
        return nodes.asList().stream()
                    .filter(node -> node.type().equals(NodeType.host))
                    .filter(dockerHost -> dockerHost.state().equals(Node.State.active))
                    .filter(dockerHost -> capacity.freeIPs(dockerHost) > 0)
                    .sorted(capacity::compareWithoutInactive)
                    .limit(spares)
                    .collect(Collectors.toSet());
    }

    /** Returns the list of nodes sorted by PrioritizableNode::compare */
    List<PrioritizableNode> prioritize() {
        return nodes.values().stream().sorted().collect(Collectors.toList());
    }

    /**
     * Add nodes that have been previously reserved to the same application from
     * an earlier downsizing of a cluster
     */
    void addSurplusNodes(List<Node> surplusNodes) {
        for (Node node : surplusNodes) {
            PrioritizableNode nodePri = toPrioritizable(node, true, false);
            if (!nodePri.violatesSpares || isAllocatingForReplacement) {
                nodes.put(node, nodePri);
            }
        }
    }

    /**
     * Add a node on each docker host with enough capacity for the requested flavor
     *
     * @param exclusively whether the ready docker nodes should only be added on hosts that
     *                    already have nodes allocated to this tenant
     */
    void addNewDockerNodes(boolean exclusively) {
        if ( ! isDocker) return;

        LockedNodeList candidates = allNodes
                .filter(node -> node.type() != NodeType.host || ALLOCATABLE_HOST_STATES.contains(node.state()));

        if (exclusively) {
            Set<String> candidateHostnames = candidates.asList().stream()
                                                       .filter(node -> node.type() == NodeType.tenant)
                                                       .filter(node -> node.allocation()
                                                                           .map(a -> a.owner().tenant().equals(appId.tenant()))
                                                                           .orElse(false))
                                                       .flatMap(node -> node.parentHostname().stream())
                                                       .collect(Collectors.toSet());

            candidates = candidates.filter(node -> candidateHostnames.contains(node.hostname()));
        }

        addNewDockerNodesOn(candidates);
    }

    private void addNewDockerNodesOn(LockedNodeList candidates) {
        NodeResources wantedResources = resources(requestedNodes);

        for (Node host : candidates) {
            if (!host.type().supportsChild(requestedNodes.type())) continue;
            if (host.status().wantToRetire()) continue;

            boolean hostHasCapacityForWantedFlavor = capacity.hasCapacity(host, wantedResources);
            boolean conflictingCluster = allNodes.childrenOf(host).owner(appId).asList().stream()
                                                 .anyMatch(child -> child.allocation().get().membership().cluster().id().equals(clusterSpec.id()));

            if (!hostHasCapacityForWantedFlavor || conflictingCluster) continue;

            log.log(LogLevel.DEBUG, "Trying to add new Docker node on " + host);
            Optional<IP.Allocation> allocation;
            try {
                allocation = host.ipConfig().pool().findAllocation(allNodes, nameResolver);
                if (allocation.isEmpty()) continue; // No free addresses in this pool
            } catch (Exception e) {
                log.log(LogLevel.WARNING, "Failed allocating IP address on " + host.hostname(), e);
                continue;
            }

            Node newNode = Node.createDockerNode(allocation.get().addresses(),
                                                 allocation.get().hostname(),
                                                 host.hostname(),
                                                 resources(requestedNodes).with(host.flavor().resources().diskSpeed())
                                                                          .with(host.flavor().resources().storageType()),
                                                 NodeType.tenant);
            PrioritizableNode nodePri = toPrioritizable(newNode, false, true);
            if ( ! nodePri.violatesSpares || isAllocatingForReplacement) {
                log.log(LogLevel.DEBUG, "Adding new Docker node " + newNode);
                nodes.put(newNode, nodePri);
            }
        }
    }

    /** Add existing nodes allocated to the application */
    void addApplicationNodes() {
        EnumSet<Node.State> legalStates = EnumSet.of(Node.State.active, Node.State.inactive, Node.State.reserved);
        allNodes.asList().stream()
                .filter(node -> node.type().equals(requestedNodes.type()))
                .filter(node -> legalStates.contains(node.state()))
                .filter(node -> node.allocation().isPresent())
                .filter(node -> node.allocation().get().owner().equals(appId))
                .map(node -> toPrioritizable(node, false, false))
                .forEach(prioritizableNode -> nodes.put(prioritizableNode.node, prioritizableNode));
    }

    /** Add nodes already provisioned, but not allocated to any application */
    void addReadyNodes() {
        allNodes.asList().stream()
                .filter(node -> node.type().equals(requestedNodes.type()))
                .filter(node -> node.state().equals(Node.State.ready))
                .map(node -> toPrioritizable(node, false, false))
                .filter(n -> !n.violatesSpares || isAllocatingForReplacement)
                .forEach(prioritizableNode -> nodes.put(prioritizableNode.node, prioritizableNode));
    }

    /**
     * Convert a list of nodes to a list of node priorities. This includes finding, calculating
     * parameters to the priority sorting procedure.
     */
    private PrioritizableNode toPrioritizable(Node node, boolean isSurplusNode, boolean isNewNode) {
        PrioritizableNode.Builder builder = new PrioritizableNode.Builder(node)
                .withSurplusNode(isSurplusNode)
                .withNewNode(isNewNode);

        allNodes.parentOf(node).ifPresent(parent -> {
            builder.withParent(parent).withFreeParentCapacity(capacity.freeCapacityOf(parent, false));
            if (spareHosts.contains(parent)) {
                builder.withViolatesSpares(true);
            }
        });

        return builder.build();
    }

    private boolean isReplacement(int nofNodesInCluster, int nodeFailedNodes) {
        if (nodeFailedNodes == 0) return false;

        return requestedNodes.fulfilledBy(nofNodesInCluster - nodeFailedNodes);
    }

    private static NodeResources resources(NodeSpec requestedNodes) {
        if ( ! (requestedNodes instanceof NodeSpec.CountNodeSpec)) return null;
        return requestedNodes.resources().get();
    }

    private boolean isDocker() {
        return resources(requestedNodes) != null;
    }

}
