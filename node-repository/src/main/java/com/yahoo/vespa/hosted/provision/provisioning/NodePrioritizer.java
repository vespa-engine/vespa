// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    private final Map<Node, PrioritizableNode> nodes = new HashMap<>();
    private final List<Node> allNodes;
    private final DockerHostCapacity capacity;
    private final NodeSpec requestedNodes;
    private final ApplicationId appId;
    private final ClusterSpec clusterSpec;
    private final NameResolver nameResolver;
    private final boolean isDocker;
    private final boolean isAllocatingForReplacement;
    private final Set<Node> spareHosts;
    private final Map<Node, ResourceCapacity> headroomHosts;

    NodePrioritizer(List<Node> allNodes, ApplicationId appId, ClusterSpec clusterSpec, NodeSpec nodeSpec, NodeFlavors nodeFlavors, int spares, NameResolver nameResolver) {
        this.allNodes = Collections.unmodifiableList(allNodes);
        this.requestedNodes = nodeSpec;
        this.clusterSpec = clusterSpec;
        this.appId = appId;
        this.nameResolver = nameResolver;
        this.spareHosts = findSpareHosts(allNodes, spares);
        this.headroomHosts = findHeadroomHosts(allNodes, spareHosts, nodeFlavors);

        this.capacity = new DockerHostCapacity(allNodes);

        long nofFailedNodes = allNodes.stream()
                .filter(node -> node.state().equals(Node.State.failed))
                .filter(node -> node.allocation().isPresent())
                .filter(node -> node.allocation().get().owner().equals(appId))
                .filter(node -> node.allocation().get().membership().cluster().id().equals(clusterSpec.id()))
                .count();

        long nofNodesInCluster = allNodes.stream()
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
    private static Set<Node> findSpareHosts(List<Node> nodes, int spares) {
        DockerHostCapacity capacity = new DockerHostCapacity(new ArrayList<>(nodes));
        return nodes.stream()
                .filter(node -> node.type().equals(NodeType.host))
                .filter(dockerHost -> dockerHost.state().equals(Node.State.active))
                .filter(dockerHost -> capacity.freeIPs(dockerHost) > 0)
                .sorted(capacity::compareWithoutInactive)
                .limit(spares)
                .collect(Collectors.toSet());
    }

    /**
     * Headroom hosts are the host with the least but sufficient capacity for the requested headroom.
     *
     * If not enough headroom - the headroom violating hosts are the once that are closest to fulfill
     * a headroom request.
     */
    private static Map<Node, ResourceCapacity> findHeadroomHosts(List<Node> nodes, Set<Node> spareNodes, NodeFlavors flavors) {
        DockerHostCapacity capacity = new DockerHostCapacity(nodes);
        Map<Node, ResourceCapacity> headroomHosts = new HashMap<>();

        List<Node> hostsSortedOnLeastCapacity = nodes.stream()
                .filter(n -> !spareNodes.contains(n))
                .filter(node -> node.type().equals(NodeType.host))
                .filter(dockerHost -> dockerHost.state().equals(Node.State.active))
                .filter(dockerHost -> capacity.freeIPs(dockerHost) > 0)
                .sorted((a, b) -> capacity.compareWithoutInactive(b, a))
                .collect(Collectors.toList());

        // For all flavors with ideal headroom - find which hosts this headroom should be allocated to
        for (Flavor flavor : flavors.getFlavors().stream().filter(f -> f.getIdealHeadroom() > 0).collect(Collectors.toList())) {
            Set<Node> tempHeadroom = new HashSet<>();
            Set<Node> notEnoughCapacity = new HashSet<>();

            ResourceCapacity headroomCapacity = ResourceCapacity.of(flavor);

            // Select hosts that has available capacity for both headroom and for new allocations
            for (Node host : hostsSortedOnLeastCapacity) {
                if (headroomHosts.containsKey(host)) continue;
                if (capacity.hasCapacityWhenRetiredAndInactiveNodesAreGone(host, headroomCapacity)) {
                    headroomHosts.put(host, headroomCapacity);
                    tempHeadroom.add(host);
                } else {
                    notEnoughCapacity.add(host);
                }

                if (tempHeadroom.size() == flavor.getIdealHeadroom()) {
                    break;
                }
            }

            // Now check if we have enough headroom - if not choose the nodes that almost has it
            if (tempHeadroom.size() < flavor.getIdealHeadroom()) {
                List<Node> violations = notEnoughCapacity.stream()
                        .sorted((a, b) -> capacity.compare(b, a))
                        .limit(flavor.getIdealHeadroom() - tempHeadroom.size())
                        .collect(Collectors.toList());

                for (Node hostViolatingHeadrom : violations) {
                    headroomHosts.put(hostViolatingHeadrom, headroomCapacity);
                }
            }
        }

        return headroomHosts;
    }

    /**
     * @return The list of nodes sorted by PrioritizableNode::compare
     */
    List<PrioritizableNode> prioritize() {
        List<PrioritizableNode> priorityList = new ArrayList<>(nodes.values());
        Collections.sort(priorityList);
        return priorityList;
    }

    /**
     * Add nodes that have been previously reserved to the same application from
     * an earlier downsizing of a cluster
     */
    void addSurplusNodes(List<Node> surplusNodes) {
        for (Node node : surplusNodes) {
            PrioritizableNode nodePri = toNodePriority(node, true, false);
            if (!nodePri.violatesSpares || isAllocatingForReplacement) {
                nodes.put(node, nodePri);
            }
        }
    }

    /**
     * Add a node on each docker host with enough capacity for the requested flavor
     */
    void addNewDockerNodes() {
        if (!isDocker) return;
        DockerHostCapacity capacity = new DockerHostCapacity(allNodes);
        ResourceCapacity wantedResourceCapacity = ResourceCapacity.of(getFlavor(requestedNodes));
        NodeList list = new NodeList(allNodes);

        for (Node node : allNodes) {
            if (node.type() != NodeType.host) continue;
            if (node.state() != Node.State.active) continue;
            if (node.status().wantToRetire()) continue;

            boolean hostHasCapacityForWantedFlavor = capacity.hasCapacity(node, wantedResourceCapacity);
            boolean conflictingCluster = list.childNodes(node).owner(appId).asList().stream()
                    .anyMatch(child -> child.allocation().get().membership().cluster().id().equals(clusterSpec.id()));

            if (!hostHasCapacityForWantedFlavor || conflictingCluster) continue;

            Set<String> ipAddresses = DockerHostCapacity.findFreeIps(node, allNodes);
            if (ipAddresses.isEmpty()) continue;
            String ipAddress = ipAddresses.stream().findFirst().get();
            Optional<String> hostname = nameResolver.getHostname(ipAddress);
            if (!hostname.isPresent()) continue;
            Node newNode = Node.createDockerNode("fake-" + hostname.get(),
                                                 Collections.singleton(ipAddress),
                                                 Collections.emptySet(), hostname.get(),
                                                 Optional.of(node.hostname()), getFlavor(requestedNodes),
                                                 NodeType.tenant);
            PrioritizableNode nodePri = toNodePriority(newNode, false, true);
            if (!nodePri.violatesSpares || isAllocatingForReplacement) {
                nodes.put(newNode, nodePri);
            }
        }
    }

    /**
     * Add existing nodes allocated to the application
     */
    void addApplicationNodes() {
        List<Node.State> legalStates = Arrays.asList(Node.State.active, Node.State.inactive, Node.State.reserved);
        allNodes.stream()
                .filter(node -> node.type().equals(requestedNodes.type()))
                .filter(node -> legalStates.contains(node.state()))
                .filter(node -> node.allocation().isPresent())
                .filter(node -> node.allocation().get().owner().equals(appId))
                .map(node -> toNodePriority(node, false, false))
                .forEach(prioritizableNode -> nodes.put(prioritizableNode.node, prioritizableNode));
    }

    /**
     * Add nodes already provisioned, but not allocated to any application
     */
    void addReadyNodes() {
        allNodes.stream()
                .filter(node -> node.type().equals(requestedNodes.type()))
                .filter(node -> node.state().equals(Node.State.ready))
                .map(node -> toNodePriority(node, false, false))
                .filter(n -> !n.violatesSpares || isAllocatingForReplacement)
                .forEach(prioritizableNode -> nodes.put(prioritizableNode.node, prioritizableNode));
    }

    /**
     * Convert a list of nodes to a list of node priorities. This includes finding, calculating
     * parameters to the priority sorting procedure.
     */
    private PrioritizableNode toNodePriority(Node node, boolean isSurplusNode, boolean isNewNode) {
        PrioritizableNode pri = new PrioritizableNode();
        pri.node = node;
        pri.isSurplusNode = isSurplusNode;
        pri.isNewNode = isNewNode;
        pri.preferredOnFlavor = requestedNodes.specifiesNonStockFlavor() && node.flavor().equals(getFlavor(requestedNodes));
        pri.parent = findParentNode(node);

        if (pri.parent.isPresent()) {
            Node parent = pri.parent.get();
            pri.freeParentCapacity = capacity.freeCapacityOf(parent, false);

            if (spareHosts.contains(parent)) {
                pri.violatesSpares = true;
            }

            if (headroomHosts.containsKey(parent) && isPreferredNodeToBeReloacted(allNodes, node, parent)) {
                ResourceCapacity neededCapacity = headroomHosts.get(parent);

                // If the node is new then we need to check the headroom requirement after it has been added
                if (isNewNode) {
                    neededCapacity = ResourceCapacity.composite(neededCapacity, new ResourceCapacity(node));
                }
                pri.violatesHeadroom = !capacity.hasCapacity(parent, neededCapacity);
            }
        }

        return pri;
    }

    static boolean isPreferredNodeToBeReloacted(List<Node> nodes, Node node, Node parent) {
        NodeList list = new NodeList(nodes);
        return list.childNodes(parent).asList().stream()
                .sorted(NodePrioritizer::compareForRelocation)
                .findFirst()
                .filter(n -> n.equals(node))
                .isPresent();
    }

    private boolean isReplacement(long nofNodesInCluster, long nodeFailedNodes) {
        if (nodeFailedNodes == 0) return false;

        int wantedCount = 0;
        if (requestedNodes instanceof NodeSpec.CountNodeSpec) {
            NodeSpec.CountNodeSpec countSpec = (NodeSpec.CountNodeSpec) requestedNodes;
            wantedCount = countSpec.getCount();
        }

        return (wantedCount > nofNodesInCluster - nodeFailedNodes);
    }

    private static Flavor getFlavor(NodeSpec requestedNodes) {
        if (requestedNodes instanceof NodeSpec.CountNodeSpec) {
            NodeSpec.CountNodeSpec countSpec = (NodeSpec.CountNodeSpec) requestedNodes;
            return countSpec.getFlavor();
        }
        return null;
    }

    private boolean isDocker() {
        Flavor flavor = getFlavor(requestedNodes);
        return (flavor != null) && flavor.getType().equals(Flavor.Type.DOCKER_CONTAINER);
    }

    private Optional<Node> findParentNode(Node node) {
        if (!node.parentHostname().isPresent()) return Optional.empty();
        return allNodes.stream()
                .filter(n -> n.hostname().equals(node.parentHostname().orElse(" NOT A NODE")))
                .findAny();
    }

    private static int compareForRelocation(Node a, Node b) {
        // Choose smallest node
        int capacity = ResourceCapacity.of(a).compare(ResourceCapacity.of(b));
        if (capacity != 0) return capacity;

        // Choose unallocated over allocated (this case is when we have ready docker nodes)
        if (!a.allocation().isPresent() && b.allocation().isPresent()) return -1;
        if (a.allocation().isPresent() && !b.allocation().isPresent()) return 1;

        // Choose container over content nodes
        if (a.allocation().isPresent() && b.allocation().isPresent()) {
            if (a.allocation().get().membership().cluster().type().equals(ClusterSpec.Type.container) &&
                    !b.allocation().get().membership().cluster().type().equals(ClusterSpec.Type.container))
                return -1;
            if (!a.allocation().get().membership().cluster().type().equals(ClusterSpec.Type.container) &&
                    b.allocation().get().membership().cluster().type().equals(ClusterSpec.Type.container))
                return 1;
        }

        // To get a stable algorithm - choose lexicographical from hostname
        return a.hostname().compareTo(b.hostname());
    }
}
