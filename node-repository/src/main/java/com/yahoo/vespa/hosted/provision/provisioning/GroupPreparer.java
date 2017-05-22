// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.common.collect.ComparisonChain;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.lang.MutableInteger;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Clock;
import java.util.Collections;
import java.util.List;

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
     * @return the list of nodes this cluster group will have allocated if activated
     */
     // Note: This operation may make persisted changes to the set of reserved and inactive nodes,
     // but it may not change the set of active nodes, as the active nodes must stay in sync with the
     // active config model which is changed on activate
    public List<Node> prepare(ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes, 
                              List<Node> surplusActiveNodes, MutableInteger highestIndex) {
        try (Mutex lock = nodeRepository.lock(application)) {
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
                List<Node> readyNodes = nodeRepository.getNodes(requestedNodes.type(), Node.State.ready);
                accepted = allocation.offer(prioritizeNodes(readyNodes, requestedNodes), !canChangeGroup);
                allocation.update(nodeRepository.reserve(accepted));
            }

            if (allocation.fullfilled())
                return allocation.finalNodes(surplusActiveNodes);
            else
                throw new OutOfCapacityException("Could not satisfy " + requestedNodes + " for " + cluster + 
                                                 outOfCapacityDetails(allocation));
        }
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
