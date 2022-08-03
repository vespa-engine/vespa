// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.util.Optional;

/**
 * A searcher of the space of possible allocation
 *
 * @author bratseth
 */
public class AllocationOptimizer {

    // The min and max nodes to consider when not using application supplied limits
    private static final int minimumNodes = 2; // Since this number includes redundancy it cannot be lower than 2
    private static final int maximumNodes = 150;

    private final NodeRepository nodeRepository;

    public AllocationOptimizer(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    /**
     * Searches the space of possible allocations given a target
     * and (optionally) cluster limits and returns the best alternative.
     *
     * @return the best allocation, if there are any possible legal allocations, fulfilling the target
     *         fully or partially, within the limits
     */
    public Optional<AllocatableClusterResources> findBestAllocation(ResourceTarget target,
                                                                    AllocatableClusterResources current,
                                                                    ClusterModel clusterModel,
                                                                    Limits limits) {
        int minimumNodes = AllocationOptimizer.minimumNodes;
        if (limits.isEmpty())
            limits = Limits.of(new ClusterResources(minimumNodes,    1, NodeResources.unspecified()),
                               new ClusterResources(maximumNodes, maximumNodes, NodeResources.unspecified()));
        else
            limits = atLeast(minimumNodes, limits).fullySpecified(current.clusterSpec(), nodeRepository, clusterModel.application().id());
        Optional<AllocatableClusterResources> bestAllocation = Optional.empty();
        NodeList hosts = nodeRepository.nodes().list().hosts();
        for (int groups = limits.min().groups(); groups <= limits.max().groups(); groups++) {
            for (int nodes = limits.min().nodes(); nodes <= limits.max().nodes(); nodes++) {
                if (nodes % groups != 0) continue;
                int groupSize = nodes / groups;

                // Adjust for redundancy: Node in group if groups = 1, an extra group if multiple groups
                // TODO: Make the best choice between those two based on size and redundancy setting instead
                int nodesAdjustedForRedundancy   = clusterModel.nodesAdjustedForRedundancy(nodes, groups);
                int groupsAdjustedForRedundancy  = clusterModel.groupsAdjustedForRedundancy(nodes, groups);

                ClusterResources next = new ClusterResources(nodes,
                                                             groups,
                                                             nodeResourcesWith(nodesAdjustedForRedundancy,
                                                                               groupsAdjustedForRedundancy,
                                                                               limits, target, current, clusterModel));
                var allocatableResources = AllocatableClusterResources.from(next, current.clusterSpec(), limits,
                                                                            hosts, nodeRepository);
                if (allocatableResources.isEmpty()) continue;
                if (bestAllocation.isEmpty() || allocatableResources.get().preferableTo(bestAllocation.get()))
                    bestAllocation = allocatableResources;
            }
        }
        return bestAllocation;
    }

    /**
     * For the observed load this instance is initialized with, returns the resources needed per node to be at
     * ideal load given a target node count
     */
    private NodeResources nodeResourcesWith(int nodes,
                                            int groups,
                                            Limits limits,
                                            ResourceTarget target,
                                            AllocatableClusterResources current,
                                            ClusterModel clusterModel) {
        int currentNodes = clusterModel.nodesAdjustedForRedundancy(clusterModel.nodeCount(), clusterModel.groupCount());
        int currentGroups = clusterModel.groupsAdjustedForRedundancy(clusterModel.nodeCount(), clusterModel.groupCount());

        var scaled = clusterModel.loadWith(nodes, groups)
                                 .scaled(Load.one().divide(clusterModel.loadWith(currentNodes, currentGroups)).scaled(target.resources()));
        // Combine the scaled resource values computed here
        // with the currently configured non-scaled values, given in the limits, if any
        var nonScaled = limits.isEmpty() || limits.min().nodeResources().isUnspecified()
                        ? current.advertisedResources().nodeResources()
                        : limits.min().nodeResources(); // min=max for non-scaled
        return nonScaled.withVcpu(scaled.vcpu()).withMemoryGb(scaled.memoryGb()).withDiskGb(scaled.diskGb());
    }

    /** Returns a copy of the given limits where the minimum nodes are at least the given value when allowed */
    private Limits atLeast(int min, Limits limits) {
        if (limits.max().nodes() < min) return limits; // not allowed
        return limits.withMin(limits.min().withNodes(Math.max(min, limits.min().nodes())));
    }

}
