// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.lang.MutableInteger;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Performs preparation of node activation changes for a single host group in an application.
 *
 * @author bratseth
 */
class GroupPreparer {

    private final NodeRepository nodeRepository;
    private final Clock clock;

    private static final boolean canChangeGroup = true;

    public GroupPreparer(NodeRepository nodeRepository, Clock clock) {
        this.nodeRepository = nodeRepository;
        this.clock = clock;
    }

    /**
     * Ensure sufficient nodes are reserved or active for the given application, group and cluster
     *
     * @param application        the application we are allocating to
     * @param cluster            the cluster and group we are allocating to
     * @param requestedNodes     a specification of the requested nodes
     * @param surplusActiveNodes currently active nodes which are available to be assigned to this group.
     *                           This method will remove from this list if it finds it needs additional nodes
     * @param highestIndex       the current highest node index among all active nodes in this cluster.
     *                           This method will increase this number when it allocates new nodes to the cluster.
     * @param nofSpares          The number of spare docker hosts we want when dynamically allocate docker containers
     * @param debugRecorder      Debug facility to step through the allocation process after the fact
     * @return the list of nodes this cluster group will have allocated if activated
     */
    // Note: This operation may make persisted changes to the set of reserved and inactive nodes,
    // but it may not change the set of active nodes, as the active nodes must stay in sync with the
    // active config model which is changed on activate
    public List<Node> prepare(ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes,
                              List<Node> surplusActiveNodes, MutableInteger highestIndex, int nofSpares, BiConsumer<List<Node>, String> debugRecorder) {
        try (Mutex lock = nodeRepository.lock(application)) {

            // Use new, ready nodes. Lock ready pool to ensure that nodes are not grabbed by others.
            try (Mutex readyLock = nodeRepository.lockUnallocated()) {

                NodePrioritizer prioritizer = new NodePrioritizer(
                        nodeRepository.getNodes(),
                        nodeRepository.getAvailableFlavors(),
                        a -> nodeRepository.getNode(a.parentHostname().orElse(" NOT-A-NODE !"), Node.State.values()),
                        getDockerFlavor(requestedNodes),
                        requestedNodes.specifiesNonStockFlavor(),
                        1,
                        nofSpares);
                prioritizer.addApplicationNodes(nodeRepository.getNodes(application, Node.State.active, Node.State.inactive, Node.State.reserved));
                prioritizer.addSurplusNodes(surplusActiveNodes);
                prioritizer.addReadyNodes(nodeRepository.getNodes(Node.State.ready));
                prioritizer.addNewNodes(createNewNodes(nodeRepository.getNodes(), getDockerFlavor(requestedNodes), cluster));

                NodeAllocation allocation = new NodeAllocation(application, cluster, requestedNodes, highestIndex, clock);
                List<Node> acceptedNodes = allocation.offer(prioritizer.toPrioritizedNodeList(false), false); //TODO replacement

                if (allocation.fullfilled()) {
                    nodeRepository.reserve(prioritizer.filterInactiveAndReadyNodes(acceptedNodes));
                    nodeRepository.addDockerNodes(prioritizer.filterNewNodes(acceptedNodes));
                    surplusActiveNodes.removeAll(prioritizer.filterSurplusNodes(acceptedNodes));
                    return allocation.finalNodes(surplusActiveNodes);
                } else {
                    throw new OutOfCapacityException("Could not satisfy " + requestedNodes + " for " + cluster +
                            outOfCapacityDetails(allocation));
                }
            }
        }
    }

    private Optional<Flavor> getDockerFlavor(NodeSpec nodeSpec) {
        if (nodeSpec instanceof NodeSpec.CountNodeSpec) {
            NodeSpec.CountNodeSpec countSpec = (NodeSpec.CountNodeSpec) nodeSpec;
            return Optional.of(countSpec.getFlavor());
        }
        return Optional.empty();
    }

    private String outOfCapacityDetails(NodeAllocation allocation) {
        if (allocation.wouldBeFulfilledWithClashingParentHost()) {
            return ": Not enough nodes available on separate physical hosts.";
        }
        if (allocation.wouldBeFulfilledWithRetiredNodes()) {
            return ": Not enough nodes available due to retirement.";
        }
        return ".";
    }

    /**
     * Create new nodes on all hosts that have capacity for the requested flavor
     * (regardless of headroom and spare constraints) and where the parent nodes does not have
     * a conflicting node (a node in the same cluster).
     *
     * @param allNodes The existing nodes in the node-repository (thus no headroom and spares here)
     */
    private List<Node> createNewNodes(List<Node> allNodes, Optional<Flavor> dockerFlavor, ClusterSpec clusterSpec) {
        List<Node> newNodes = new ArrayList<>();
        // Only create nodes if this is docker and the dynamic allocation is enabled
        if (!nodeRepository.dynamicAllocationEnabled() || !dockerFlavor.isPresent()) return newNodes;

        Flavor flavor = dockerFlavor.get();
        DockerHostCapacity capacity = new DockerHostCapacity(allNodes);
        for (Node node : allNodes) {
            // For each host
            if (node.type() == NodeType.host) {
                NodeList list = new NodeList(allNodes);
                NodeList childrenWithSameApp = list.childNodes(node).owner(null);
                for (Node child : childrenWithSameApp.asList()) {
                    // Look for nodes from the same cluster
                    if (child.allocation().get().membership().cluster().id().equals(clusterSpec.id())) {

                    }
                }

                if (capacity.hasCapacity(node, flavor)) {
                    Set<String> ipAddresses = DockerHostCapacity.findFreeIps(node, allNodes);
                    if (ipAddresses.isEmpty()) continue;
                    String ipAddress = ipAddresses.stream().findFirst().get();
                    String hostname = lookupHostname(ipAddress);
                    if (hostname == null) continue;
                    Node newNode = Node.createDockerNode("fake-" + hostname, Collections.singleton(ipAddress),
                            Collections.emptySet(), hostname, Optional.of(node.hostname()), flavor, NodeType.tenant);
                    newNodes.add(newNode);
                }
            }
        }
        return newNodes;
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
}
