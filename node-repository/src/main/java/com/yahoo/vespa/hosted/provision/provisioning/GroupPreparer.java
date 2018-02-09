// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.lang.MutableInteger;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Clock;
import java.util.List;

/**
 * Performs preparation of node activation changes for a single host group in an application.
 *
 * @author bratseth
 */
public class GroupPreparer {

    private final NodeRepository nodeRepository;
    private final Clock clock;

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
     * @param spareCount         The number of spare docker hosts we want when dynamically allocate docker containers
     * @return the list of nodes this cluster group will have allocated if activated
     */
    // Note: This operation may make persisted changes to the set of reserved and inactive nodes,
    // but it may not change the set of active nodes, as the active nodes must stay in sync with the
    // active config model which is changed on activate
    public List<Node> prepare(ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes,
                              List<Node> surplusActiveNodes, MutableInteger highestIndex, int spareCount) {
        try (Mutex lock = nodeRepository.lock(application)) {

            // Lock ready pool to ensure that ready nodes are not simultaneously grabbed by others
            try (Mutex readyLock = nodeRepository.lockUnallocated()) {

                // Create a prioritized set of nodes
                NodePrioritizer prioritizer = new NodePrioritizer(nodeRepository.getNodes(),
                                                                  application,
                                                                  cluster,
                                                                  requestedNodes,
                                                                  nodeRepository.getAvailableFlavors(),
                                                                  spareCount,
                                                                  nodeRepository.nameResolver());

                prioritizer.addApplicationNodes();
                prioritizer.addSurplusNodes(surplusActiveNodes);
                prioritizer.addReadyNodes();
                prioritizer.addNewDockerNodes();

                // Allocate from the prioritized list
                NodeAllocation allocation = new NodeAllocation(application, cluster, requestedNodes, highestIndex, clock);
                allocation.offer(prioritizer.prioritize());
                if (! allocation.fullfilled())
                    throw new OutOfCapacityException("Could not satisfy " + requestedNodes + " for " + cluster +
                                                     outOfCapacityDetails(allocation));

                // Carry out and return allocation
                nodeRepository.reserve(allocation.reservableNodes());
                nodeRepository.addDockerNodes(allocation.newNodes());
                surplusActiveNodes.removeAll(allocation.surplusNodes());
                return allocation.finalNodes(surplusActiveNodes);
            }
        }
    }

    private String outOfCapacityDetails(NodeAllocation allocation) {
        if (allocation.wouldBeFulfilledWithClashingParentHost())
            return ": Not enough nodes available on separate physical hosts.";
        if (allocation.wouldBeFulfilledWithRetiredNodes())
            return ": Not enough nodes available due to retirement.";
        else
            return ".";
    }
}
