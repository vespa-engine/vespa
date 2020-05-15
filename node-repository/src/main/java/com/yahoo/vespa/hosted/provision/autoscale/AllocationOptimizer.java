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
        Optional<AllocatableClusterResources> bestAllocation = Optional.empty();
        for (ResourceIterator i = new ResourceIterator(target, current, limits); i.hasNext(); ) {
            var allocatableResources = AllocatableClusterResources.from(i.next(), current.clusterType(), limits, nodeRepository);
            if (allocatableResources.isEmpty()) continue;
            if (bestAllocation.isEmpty() || allocatableResources.get().preferableTo(bestAllocation.get()))
                bestAllocation = allocatableResources;
        }
        return bestAllocation;
    }

    /**
     * Provides iteration over possible cluster resource allocations given a target total load
     * and current groups/nodes allocation.
     */
    private static class ResourceIterator {

        // The min and max nodes to consider when not using application supplied limits
        private static final int minimumNodes = 3; // Since this number includes redundancy it cannot be lower than 2
        private static final int maximumNodes = 150;

        // When a query is issued on a node the cost is the sum of a fixed cost component and a cost component
        // proportional to document count. We must account for this when comparing configurations with more or fewer nodes.
        // TODO: Measure this, and only take it into account with queries
        private static final double fixedCpuCostFraction = 0.1;

        // Given state
        private final Limits limits;
        private final AllocatableClusterResources current;
        private final ResourceTarget target;

        // Derived from the observed state
        private final int nodeIncrement;
        private final boolean singleGroupMode;

        // Iterator state
        private int currentNodes;

        public ResourceIterator(ResourceTarget target, AllocatableClusterResources current, Limits limits) {
            this.target = target;
            this.current = current;
            this.limits = limits;

            // What number of nodes is it effective to add or remove at the time from this cluster?
            // This is the group size, since we (for now) assume the group size is decided by someone wiser than us
            // and we decide the number of groups.
            // The exception is when we only have one group, where we can add and remove single nodes in it.
            singleGroupMode = current.groups() == 1;
            nodeIncrement = singleGroupMode ? 1 : current.groupSize();

            // Step to the right starting point
            currentNodes = current.nodes();
            if (currentNodes < minNodes()) { // step up
                while (currentNodes < minNodes()
                       && (singleGroupMode || currentNodes + nodeIncrement > current.groupSize())) // group level redundancy
                    currentNodes += nodeIncrement;
            }
            else { // step down
                while (currentNodes - nodeIncrement >= minNodes()
                       && (singleGroupMode || currentNodes - nodeIncrement > current.groupSize())) // group level redundancy
                    currentNodes -= nodeIncrement;
            }
        }

        public ClusterResources next() {
            ClusterResources next = resourcesWith(currentNodes);
            currentNodes += nodeIncrement;
            return next;
        }

        public boolean hasNext() {
            return currentNodes <= maxNodes();
        }

        private int minNodes() {
            if (limits.isEmpty()) return minimumNodes;
            if (singleGroupMode) return limits.min().nodes();
            return Math.max(limits.min().nodes(), limits.min().groups() * current.groupSize() );
        }

        private int maxNodes() {
            if (limits.isEmpty()) return maximumNodes;
            if (singleGroupMode) return limits.max().nodes();
            return Math.min(limits.max().nodes(), limits.max().groups() * current.groupSize() );
        }

        private ClusterResources resourcesWith(int nodes) {
            int nodesAdjustedForRedundancy = nodes;
            if (target.adjustForRedundancy())
                nodesAdjustedForRedundancy = nodes - (singleGroupMode ? 1 : current.groupSize());
            return new ClusterResources(nodes,
                                        singleGroupMode ? 1 : nodes / current.groupSize(),
                                        nodeResourcesWith(nodesAdjustedForRedundancy));
        }

        /**
         * For the observed load this instance is initialized with, returns the resources needed per node to be at
         * ideal load given a target node count
         */
        private NodeResources nodeResourcesWith(int nodes) {
            int groups = singleGroupMode ? 1 : nodes / current.groupSize();

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
}
