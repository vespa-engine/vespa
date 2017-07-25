package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds up a priority queue of which nodes should be offered to the allocation.
 * <p>
 * Builds up a list of NodePriority objects and sorts them according to the
 * NodePriority::compare method.
 *
 * @author smorgrav
 */
public class NodePrioritizer {

    private final Map<Node, NodePriority> nodes = new HashMap<>();
    private final List<Node> allNodes;
    private final DockerHostCapacity capacity;
    private final NodeSpec requestedNodes;
    private final ApplicationId appId;
    private final int maxRetires;
    private final ClusterSpec clusterSpec;

    private final boolean isAllocatingForReplacement;
    private final List<Node> spareHosts;
    private final List<Node> headroomViolatedHosts;
    private final boolean isDocker;

    int nofViolations = 0;

    NodePrioritizer(List<Node> allNodes, ApplicationId appId, ClusterSpec clusterSpec, NodeSpec nodeSpec, NodeFlavors nodeFlavors, int maxRetires, int spares) {
        this.allNodes = Collections.unmodifiableList(allNodes);
        this.requestedNodes = nodeSpec;
        this.maxRetires = maxRetires;
        this.clusterSpec = clusterSpec;
        this.appId = appId;

        // Add spare and headroom allocations
        spareHosts = DockerCapacityConstraints.findSpareHosts(allNodes, spares);
        headroomViolatedHosts = new ArrayList<>();

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

        isAllocatingForReplacement = isReplacement(nofNodesInCluster, nofFailedNodes);
        isDocker = isDocker();
    }

    List<NodePriority> prioritize() {
        List<NodePriority> priorityList = new ArrayList<>(nodes.values());
        Collections.sort(priorityList, (a, b) -> NodePriority.compare(a, b));
        return priorityList;
    }

    void addSurplusNodes(List<Node> surplusNodes) {
        for (Node node : surplusNodes) {
            NodePriority nodePri = toNodePriority(node, true, false);
            if (!nodePri.violatesSpares || isAllocatingForReplacement) {
                nodes.put(node, nodePri);
            }
        }
    }

    void addNewDockerNodes() {
        if (!isDocker) return;
        DockerHostCapacity capacity = new DockerHostCapacity(allNodes);

        for (Node node : allNodes) {
            if (node.type() == NodeType.host) {
                boolean conflictingCluster = false;
                NodeList list = new NodeList(allNodes);
                NodeList childrenWithSameApp = list.childNodes(node).owner(appId);
                for (Node child : childrenWithSameApp.asList()) {
                    // Look for nodes from the same cluster
                    if (child.allocation().get().membership().cluster().id().equals(clusterSpec.id())) {
                        conflictingCluster = true;
                        break;
                    }
                }

                if (!conflictingCluster && capacity.hasCapacity(node, getFlavor())) {
                    Set<String> ipAddresses = DockerHostCapacity.findFreeIps(node, allNodes);
                    if (ipAddresses.isEmpty()) continue;
                    String ipAddress = ipAddresses.stream().findFirst().get();
                    String hostname = lookupHostname(ipAddress);
                    if (hostname == null) continue;
                    Node newNode = Node.createDockerNode("fake-" + hostname, Collections.singleton(ipAddress),
                            Collections.emptySet(), hostname, Optional.of(node.hostname()), getFlavor(), NodeType.tenant);
                    NodePriority nodePri = toNodePriority(newNode, false, true);
                    if (!nodePri.violatesSpares || isAllocatingForReplacement) {
                        nodes.put(newNode, nodePri);
                    }
                }
            }
        }
    }

    void addApplicationNodes() {
        List<Node.State> legalStates = Arrays.asList(Node.State.active, Node.State.inactive, Node.State.reserved);
        allNodes.stream()
                .filter(node -> node.type().equals(requestedNodes.type()))
                .filter(node -> legalStates.contains(node.state()))
                .filter(node -> node.allocation().isPresent())
                .filter(node -> node.allocation().get().owner().equals(appId))
                .map(node -> toNodePriority(node, false, false))
                .filter(n -> !n.violatesSpares || isAllocatingForReplacement)
                .forEach(nodePriority -> nodes.put(nodePriority.node, nodePriority));
    }

    void addReadyNodes() {
        allNodes.stream()
                .filter(node -> node.type().equals(requestedNodes.type()))
                .filter(node -> node.state().equals(Node.State.ready))
                .map(node -> toNodePriority(node, false, false))
                .filter(n -> !n.violatesSpares || isAllocatingForReplacement)
                .forEach(nodePriority -> nodes.put(nodePriority.node, nodePriority));
    }

    /**
     * Convert a list of nodes to a list of node priorities. This includes finding, calculating
     * parameters to the priority sorting procedure.
     */
    private NodePriority toNodePriority(Node node, boolean isSurplusNode, boolean isNewNode) {
        NodePriority pri = new NodePriority();
        pri.node = node;
        pri.isSurplusNode = isSurplusNode;
        pri.isNewNode = isNewNode;
        pri.preferredOnFlavor = requestedNodes.specifiesNonStockFlavor() && node.flavor().equals(getFlavor());
        pri.parent = findParentNode(node);

        if (pri.parent.isPresent()) {
            Node parent = pri.parent.get();
            pri.freeParentCapacity = capacity.freeCapacityOf(parent, true);

            /**
             * To be conservative we have a restriction of how many nodes we can retire for each cluster,
             * pr. allocation iteration. TODO also account for previously retired nodes? (thus removing the pr iteration restriction)
             */
            if (nofViolations <= maxRetires) {
                if (spareHosts.contains(parent)) {
                    pri.violatesSpares = true;
                    nofViolations++;
                }

                // Headroom violation
                if (headroomViolatedHosts.contains(parent)) {
                    pri.violatesHeadroom = true;
                    nofViolations++;
                }
            }
        }
        return pri;
    }

    /**
     * From ipAddress - get hostname
     *
     * @return hostname or null if not able to do the loopup
     */
    private static String lookupHostname(String ipAddress) {
        try {
            return InetAddress.getByName(ipAddress).getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return null;
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

    private Flavor getFlavor() {
        if (requestedNodes instanceof NodeSpec.CountNodeSpec) {
            NodeSpec.CountNodeSpec countSpec = (NodeSpec.CountNodeSpec) requestedNodes;
            return countSpec.getFlavor();
        }
        return null;
    }

    private boolean isDocker() {
        Flavor flavor = getFlavor();
        if (flavor != null) {
            return flavor.getType().equals(Flavor.Type.DOCKER_CONTAINER);
        }
        return false;
    }

    private Optional<Node> findParentNode(Node node) {
        if (!node.parentHostname().isPresent()) return Optional.empty();
        return allNodes.stream()
                .filter(n -> n.hostname().equals(node.parentHostname().orElse(" NOT A NODE")))
                .findAny();
    }
}