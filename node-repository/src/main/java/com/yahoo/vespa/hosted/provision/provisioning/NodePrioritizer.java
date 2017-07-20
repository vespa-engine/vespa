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
import java.util.stream.Collectors;

/**
 * Builds up a priority queue of which nodes should be offered to the allocation.
 *
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

    private final List<Node> spareHosts;
    private final List<Node> headroomViolatedHosts;
    private final long failedNodesInCluster;

    int nofViolations = 0;

    NodePrioritizer(List<Node> allNodes, ApplicationId appId, ClusterSpec clusterSpec, NodeSpec nodeSpec, NodeFlavors nodeFlavors, int maxRetires, int spares) {
        this.allNodes = Collections.unmodifiableList(allNodes);
        this.requestedNodes = nodeSpec;
        this.maxRetires = maxRetires;
        this.clusterSpec = clusterSpec;
        this.appId = appId;

        // Add spare and headroom allocations
        spareHosts = DockerCapacityConstraints.findSpareHosts(allNodes, spares);
        List<Node> nodesWithHeadroomAndSpares =
                DockerCapacityConstraints.addHeadroomAndSpareNodes(allNodes, nodeFlavors, spares);

        this.capacity = new DockerHostCapacity(nodesWithHeadroomAndSpares);

        failedNodesInCluster = allNodes.stream()
                .filter(node -> node.state().equals(Node.State.failed))
                .filter(node -> node.allocation().isPresent())
                .filter(node -> node.allocation().get().owner().equals(appId))
                .filter(node -> node.allocation().get().membership().cluster().id().equals(clusterSpec.id()))
                .count();

        // TODO Find hosts where we have headroom violations
        headroomViolatedHosts = new ArrayList<>();


    }

    void initNodes(List<Node> surplusNodes, boolean dynamicAllocationEnabled) {
        addApplicationNodes();
        addSurplusNodes(surplusNodes);
        addReadyNodes();
        if (dynamicAllocationEnabled && getDockerFlavor() != null) {
            addNewDockerNodes();
        }
    }

    private void addSurplusNodes(List<Node> surplusNodes) {
        //TODO change group index if content
        for (Node node : surplusNodes) {
            nodes.put(node, toNodePriority(node, true, false));
        }
    }

    private void addNewDockerNodes() {
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

                if (!conflictingCluster && capacity.hasCapacity(node, getDockerFlavor())) {
                    Set<String> ipAddresses = DockerHostCapacity.findFreeIps(node, allNodes);
                    if (ipAddresses.isEmpty()) continue;
                    String ipAddress = ipAddresses.stream().findFirst().get();
                    String hostname = lookupHostname(ipAddress);
                    if (hostname == null) continue;
                    Node newNode = Node.createDockerNode("fake-" + hostname, Collections.singleton(ipAddress),
                            Collections.emptySet(), hostname, Optional.of(node.hostname()), getDockerFlavor(), NodeType.tenant);
                    nodes.put(newNode, toNodePriority(newNode, false, true));
                }
            }
        }
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

    private void addReadyNodes() {
        allNodes.stream()
                .filter(node -> node.type().equals(requestedNodes.type()))
                .filter(node -> node.state().equals(Node.State.ready))
                .map(node -> toNodePriority(node, false, false))
                .forEach(nodePriority -> nodes.put(nodePriority.node, nodePriority));
    }

    void addApplicationNodes() {
        List<Node.State> legalStates = Arrays.asList(Node.State.active,Node.State.inactive, Node.State.reserved);
        allNodes.stream()
                .filter(node -> node.type().equals(requestedNodes.type()))
                .filter(node -> legalStates.contains(node.state()))
                .filter(node -> node.allocation().isPresent())
                .filter(node -> node.allocation().get().owner().equals(appId))
                .map(node -> toNodePriority(node, false, false))
                .forEach(nodePriority -> nodes.put(nodePriority.node, nodePriority));
    }

    List<Node> filterNewNodes(Set<Node> acceptedNodes) {
        List<Node> newNodes = new ArrayList<>();
        for (Node node : acceptedNodes) {
            if (nodes.get(node).isNewNode) {
                newNodes.add(node);
            }
        }
        return newNodes;
    }

    List<Node> filterSurplusNodes(Set<Node> acceptedNodes) {
        List<Node> surplusNodes = new ArrayList<>();
        for (Node node : acceptedNodes) {
            if (nodes.get(node).isSurplusNode) {
                surplusNodes.add(node);
            }
        }
        return surplusNodes;
    }

    List<Node> filterInactiveAndReadyNodes(Set<Node> acceptedNodes) {
        List<Node> inactiveAndReady = new ArrayList<>();
        for (Node node : acceptedNodes) {
            if (node.state().equals(Node.State.inactive) || node.state().equals(Node.State.ready)) {
                inactiveAndReady.add(node);
            }
        }
        return inactiveAndReady;
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
            pri.preferredOnFlavor = requestedNodes.specifiesNonStockFlavor() && node.flavor().equals(getDockerFlavor());
            pri.parent = findParentNode(node);

            if (pri.parent.isPresent()) {
                Node parent = pri.parent.get();
                pri.freeParentCapacity = capacity.freeCapacityOf(parent, true);

                /**
                 * To be conservative we have a restriction of how many nodes we can retire for each cluster,
                 * pr. allocation iteration. TODO also account for previously retired nodes? (thus removing the pr iteration restriction)
                 */
                if (nofViolations <= maxRetires) {
                    // Spare violation
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

    void offer(NodeAllocation allocation) {
        List<NodePriority> prioritizedNodes = nodes.values().stream().collect(Collectors.toList());
        Collections.sort(prioritizedNodes, (a,b) -> NodePriority.compare(a,b));

        List<Node> result = new ArrayList<>();
        for (NodePriority nodePriority : prioritizedNodes) {

            // The replacement heuristic assumes that new nodes are offered after already existing nodes
            boolean isReplacement = isReplacement(allocation.getAcceptedNodes().size());

            // Only add new allocations that violates the spare constraint if this is a replacement
            if (!nodePriority.violatesSpares || isReplacement || !nodePriority.isNewNode) {
                allocation.offer(Collections.singletonList(nodePriority.node), nodePriority.isSurplusNode);
            }
        }
    }

    private boolean isReplacement(int nodesAccepted) {
        if (failedNodesInCluster == 0) return false;

        int wantedCount = 0;
        if (requestedNodes instanceof NodeSpec.CountNodeSpec) {
            NodeSpec.CountNodeSpec countSpec = (NodeSpec.CountNodeSpec) requestedNodes;
            wantedCount = countSpec.getCount();
        }

        return (wantedCount <= nodesAccepted + failedNodesInCluster);
    }

    private Flavor getDockerFlavor() {
        if (requestedNodes instanceof NodeSpec.CountNodeSpec) {
            NodeSpec.CountNodeSpec countSpec = (NodeSpec.CountNodeSpec) requestedNodes;
            return countSpec.getFlavor();
        }
        return null;
    }

    private Optional<Node> findParentNode(Node node) {
        if (!node.parentHostname().isPresent()) return Optional.empty();
        return allNodes.stream()
                .filter(n -> n.hostname().equals(node.parentHostname().orElse(" NOT A NODE")))
                .findAny();
    }
}