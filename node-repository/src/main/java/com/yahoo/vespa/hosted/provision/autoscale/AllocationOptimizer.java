// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.util.Optional;

/**
 * A searcher of the space of possible allocation
 *
 * @author bratseth
 */
public class AllocationOptimizer {

    // The min and max nodes to consider when not using application supplied limits
    private static final int minimumNodes = 3; // Since this number includes redundancy it cannot be lower than 2
    private static final int maximumNodes = 150;

    // When a query is issued on a node the cost is the sum of a fixed cost component and a cost component
    // proportional to document count. We must account for this when comparing configurations with more or fewer nodes.
    // TODO: Measure this, and only take it into account with queries
    private static final double fixedCpuCostFraction = 0.1;

    private final NodeRepository nodeRepository;

    public AllocationOptimizer(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    /**
     * An AllocationSearcher searches the space of possible allocations given a target
     * and (optionally) cluster limits and returns the best alternative.
     *
     * @return the best allocation, if there are any possible legal allocations, fulfilling the target
     *         fully or partially, within the limits
     */
    public Optional<AllocatableClusterResources> findBestAllocation(ResourceTarget target,
                                                                    AllocatableClusterResources current,
                                                                    Limits limits) {
        // What number of nodes is it effective to add or remove at the time from this cluster?
        // This is the group size, since we (for now) assume the group size is decided by someone wiser than us
        // and we decide the number of groups.
        // The exception is when we only have one group, where we can add and remove single nodes in it.

        Optional<AllocatableClusterResources> bestAllocation = Optional.empty();
        for (int nodes = minNodes(limits); nodes <= maxNodes(limits); nodes++) {
            boolean singleGroupMode = current.groups() == 1 && ( limits.isEmpty() || limits.min().groups() == 1 );
            int groups = singleGroupMode ? 1 : nodes / current.groupSize();
            if ( ! limits.isEmpty() && ( groups < limits.min().groups() || groups > limits.max().groups())) continue;

            if (nodes % groups != 0) continue;
            int groupSize = nodes / groups;
            if (groups != 1 && groupSize != current.groupSize()) continue; // TODO: Remove this line


            // Adjust for redundancy: Node in group if groups = 1, an extra group if multiple groups
            // TODO: Make the best choice based on size and redundancy setting instead
            int nodesAdjustedForRedundancy = target.adjustForRedundancy() ? ( groups == 1 ? nodes - 1 : nodes - groupSize ) : nodes;
            if (nodesAdjustedForRedundancy < 1) continue;
            int groupsAdjustedForRedundancy = target.adjustForRedundancy() ? ( groups == 1 ? 1 : groups - 1 ) : groups;

            ClusterResources next = new ClusterResources(nodes,
                                                         groups,
                                                         nodeResourcesWith(nodesAdjustedForRedundancy, groupsAdjustedForRedundancy, limits, current, target));

            var allocatableResources = AllocatableClusterResources.from(next, current.clusterType(), limits, nodeRepository);
            if (allocatableResources.isEmpty()) continue;
            if (bestAllocation.isEmpty() || allocatableResources.get().preferableTo(bestAllocation.get()))
                bestAllocation = allocatableResources;
        }

        return bestAllocation;
    }

    private int minNodes(Limits limits) {
        if (limits.isEmpty()) return minimumNodes;
        return limits.min().nodes();
    }

    private int maxNodes(Limits limits) {
        if (limits.isEmpty()) return maximumNodes;
        return limits.max().nodes();
    }

    /**
     * For the observed load this instance is initialized with, returns the resources needed per node to be at
     * ideal load given a target node count
     */
    private NodeResources nodeResourcesWith(int nodes, int groups, Limits limits, AllocatableClusterResources current, ResourceTarget target) {
        // Cpu: Scales with cluster size (TODO: Only reads, writes scales with group size)
        // Memory and disk: Scales with group size
        double cpu, memory, disk;

        int groupSize = nodes / groups;

        if (current.clusterType().isContent()) { // load scales with node share of content
            // The fixed cost portion of cpu does not scale with changes to the node count
            // TODO: Only for the portion of cpu consumed by queries
            double cpuPerGroup = fixedCpuCostFraction * target.nodeCpu() +
                                 (1 - fixedCpuCostFraction) * target.nodeCpu() * current.groupSize() / groupSize;
            cpu = cpuPerGroup * current.groups() / groups;
            memory = target.nodeMemory() * current.groupSize() / groupSize;
            disk = target.nodeDisk() * current.groupSize() / groupSize;
        }
        else {
            cpu = target.nodeCpu() * current.nodes() / nodes;
            memory = target.nodeMemory();
            disk = target.nodeDisk();
        }

        // Combine the scaled resource values computed here
        // with the currently configured non-scaled values, given in the limits, if any
        NodeResources nonScaled = limits.isEmpty() || limits.min().nodeResources().isUnspecified()
                                  ? current.toAdvertisedClusterResources().nodeResources()
                                  : limits.min().nodeResources(); // min=max for non-scaled
        return nonScaled.withVcpu(cpu).withMemoryGb(memory).withDiskGb(disk);
    }

}
