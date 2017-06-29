// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.common.collect.ComparisonChain;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.lang.MutableInteger;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
     * @param application the application we are allocating to
     * @param cluster the cluster and group we are allocating to
     * @param requestedNodes a specification of the requested nodes
     * @param surplusActiveNodes currently active nodes which are available to be assigned to this group.
     *        This method will remove from this list if it finds it needs additional nodes
     * @param highestIndex the current highest node index among all active nodes in this cluster.
     *        This method will increase this number when it allocates new nodes to the cluster.
     * @param nofSpares The number of spare docker hosts we want when dynamically allocate docker containers
     * @param debugRecorder Debug facility to step through the allocation process after the fact
     * @return the list of nodes this cluster group will have allocated if activated
     */
     // Note: This operation may make persisted changes to the set of reserved and inactive nodes,
     // but it may not change the set of active nodes, as the active nodes must stay in sync with the
     // active config model which is changed on activate
    public List<Node> prepare(ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes,
                              List<Node> surplusActiveNodes, MutableInteger highestIndex, int nofSpares, BiConsumer<List<Node>, String> debugRecorder) {
        try (Mutex lock = nodeRepository.lock(application)) {

            // A snapshot of nodes before we start the process - used to determine if this is a replacement
            List<Node> nodesBefore = nodeRepository.getNodes(application, Node.State.values());
            NodeAllocation allocation = new NodeAllocation(application, cluster, requestedNodes, highestIndex, clock);

            // Use active nodes
            allocation.offer(nodeRepository.getNodes(application, Node.State.active), !canChangeGroup);
            if (allocation.saturated()) return allocation.finalNodes(surplusActiveNodes);

            // Use active nodes from other groups that will otherwise be retired
            List<Node> accepted = allocation.offer(prioritizeNodes(surplusActiveNodes, requestedNodes), canChangeGroup);
            surplusActiveNodes.removeAll(accepted);
            if (allocation.saturated()) return allocation.finalNodes(surplusActiveNodes);

            // Use previously reserved nodes
            allocation.offer(nodeRepository.getNodes(application, Node.State.reserved), !canChangeGroup);
            if (allocation.saturated()) return allocation.finalNodes(surplusActiveNodes);

            // Use inactive nodes
            accepted = allocation.offer(prioritizeNodes(nodeRepository.getNodes(application, Node.State.inactive), requestedNodes), !canChangeGroup);
            allocation.update(nodeRepository.reserve(accepted));
            if (allocation.saturated()) return allocation.finalNodes(surplusActiveNodes);

            // Use new, ready nodes. Lock ready pool to ensure that nodes are not grabbed by others.
            try (Mutex readyLock = nodeRepository.lockUnallocated()) {

                // Check if we have ready nodes that we can allocate
                List<Node> readyNodes = nodeRepository.getNodes(requestedNodes.type(), Node.State.ready);
                accepted = allocation.offer(prioritizeNodes(readyNodes, requestedNodes), !canChangeGroup);
                allocation.update(nodeRepository.reserve(accepted));

                if (nodeRepository.dynamicAllocationEnabled()) {
                    // Check if we have available capacity on docker hosts that we can allocate
                    if (!allocation.fullfilled()) {
                        // The new dynamic allocation method
                        Optional<Flavor> flavor = getFlavor(requestedNodes);
                        if (flavor.isPresent() && flavor.get().getType().equals(Flavor.Type.DOCKER_CONTAINER)) {
                            List<Node> allNodes = nodeRepository.getNodes(Node.State.values());
                            NodeFlavors flavors = nodeRepository.getAvailableFlavors();
                            accepted = DockerAllocator.allocateNewDockerNodes(allocation, requestedNodes, allNodes,
                                    nodesBefore, flavors, flavor.get(), nofSpares, debugRecorder);

                            // Add nodes to the node repository
                            if (allocation.fullfilled()) {
                                List<Node> nodesAddedToNodeRepo = nodeRepository.addDockerNodes(accepted);
                                allocation.update(nodesAddedToNodeRepo);
                            }
                        }
                    }
                }
            }

            if (allocation.fullfilled())
                return allocation.finalNodes(surplusActiveNodes);
            else
                throw new OutOfCapacityException("Could not satisfy " + requestedNodes + " for " + cluster + 
                                                 outOfCapacityDetails(allocation));
        }
    }

    private Optional<Flavor> getFlavor(NodeSpec nodeSpec) {
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
     * Returns the node list in prioritized order, where the nodes we would most prefer the application 
     * to use comes first 
     */
    private List<Node> prioritizeNodes(List<Node> nodeList, NodeSpec nodeSpec) {
        if ( nodeSpec.specifiesNonStockFlavor()) { // prefer exact flavor, docker hosts, lower cost, tie break by hostname
            Collections.sort(nodeList, (n1, n2) -> ComparisonChain.start()
                    .compareTrueFirst(nodeSpec.matchesExactly(n1.flavor()), nodeSpec.matchesExactly(n2.flavor()))
                    .compareTrueFirst(n1.parentHostname().isPresent(), n2.parentHostname().isPresent())
                    .compare(n1.flavor().cost(), n2.flavor().cost())
                    .compare(n1.hostname(), n2.hostname())
                    .result()
            );
        }
        else { // prefer docker hosts, lower cost, tie break by hostname
            Collections.sort(nodeList, (n1, n2) -> ComparisonChain.start()
                    .compareTrueFirst(n1.parentHostname().isPresent(), n2.parentHostname().isPresent())
                    .compare(n1.flavor().cost(), n2.flavor().cost())
                    .compare(n1.hostname(), n2.hostname())
                    .result()
            );
        }
        return nodeList;
    }
}
