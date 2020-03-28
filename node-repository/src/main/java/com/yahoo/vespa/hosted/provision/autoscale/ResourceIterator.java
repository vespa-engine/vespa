// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

/**
 * Provides iteration over possible cluster resource allocations given a target total load
 * and current groups/nodes allocation.
 */
public class ResourceIterator {

    // Configured min and max nodes for suggestions for apps which have not activated autoscaling
    private static final int minimumNodes = 3; // Since this is with redundancy it cannot be lower than 2
    private static final int maximumNodes = 150;

    // When a query is issued on a node the cost is the sum of a fixed cost component and a cost component
    // proportional to document count. We must account for this when comparing configurations with more or fewer nodes.
    // TODO: Measure this, and only take it into account with queries
    private static final double fixedCpuCostFraction = 0.1;

    // Prescribed state
    private final Cluster cluster;

    // Observed state
    private final AllocatableClusterResources allocation;
    private final double cpuLoad;
    private final double memoryLoad;
    private final double diskLoad;
    private final int groupSize;

    // Derived from the observed state
    private final int nodeIncrement;
    private final boolean singleGroupMode;

    // Iterator state
    private int currentNodes;

    public ResourceIterator(double cpuLoad, double memoryLoad, double diskLoad,
                            AllocatableClusterResources currentAllocation,
                            Cluster cluster) {
        this.cpuLoad = cpuLoad;
        this.memoryLoad = memoryLoad;
        this.diskLoad = diskLoad;

        // ceil: If the division does not produce a whole number we assume some node is missing
        groupSize = (int)Math.ceil((double)currentAllocation.nodes() / currentAllocation.groups());
        allocation = currentAllocation;

        this.cluster = cluster;

        // What number of nodes is it effective to add or remove at the time from this cluster?
        // This is the group size, since we (for now) assume the group size is decided by someone wiser than us
        // and we decide the number of groups.
        // The exception is when we only have one group, where we can add and remove single nodes in it.
        singleGroupMode = currentAllocation.groups() == 1;
        nodeIncrement = singleGroupMode ? 1 : groupSize;

        // Step down to the right starting point
        currentNodes = currentAllocation.nodes();
        while (currentNodes - nodeIncrement >= minNodes()
               && ( singleGroupMode || currentNodes - nodeIncrement > groupSize)) // group level redundancy
            currentNodes -= nodeIncrement;
    }

    /** If autoscaling is not enabled (meaning max and min resources are the same), we want to suggest */
    private boolean suggestMode() {
        return cluster.minResources().equals(cluster.maxResources());
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
        if (suggestMode()) return minimumNodes;
        if (singleGroupMode) return cluster.minResources().nodes();
        return Math.max(cluster.minResources().nodes(), cluster.minResources().groups() * groupSize );
    }

    private int maxNodes() {
        if (suggestMode()) return maximumNodes;
        if (singleGroupMode) return cluster.maxResources().nodes();
        return Math.min(cluster.maxResources().nodes(), cluster.maxResources().groups() * groupSize );
    }

    private ClusterResources resourcesWith(int nodes) {
        int nodesWithRedundancy = nodes - (singleGroupMode ? 1 : groupSize);
        return new ClusterResources(nodes,
                                    singleGroupMode ? 1 : nodes / groupSize,
                                    nodeResourcesWith(nodesWithRedundancy));
    }

    /**
     * For the observed load this instance is initialized with, returns the resources needed per node to be at
     * ideal load given a target node count
     */
    private NodeResources nodeResourcesWith(int nodeCount) {
        // Cpu: Scales with cluster size (TODO: Only reads, writes scales with group size)
        // Memory and disk: Scales with group size

        double cpu, memory, disk;
        if (singleGroupMode) {
            // The fixed cost portion of cpu does not scale with changes to the node count
            // TODO: Only for the portion of cpu consumed by queries
            double totalCpu = clusterUsage(Resource.cpu, cpuLoad);
            cpu = fixedCpuCostFraction       * totalCpu / groupSize / Resource.cpu.idealAverageLoad() +
                  (1 - fixedCpuCostFraction) * totalCpu / nodeCount / Resource.cpu.idealAverageLoad();
            if (allocation.clusterType().isContent()) { // load scales with node share of content
                memory = groupUsage(Resource.memory, memoryLoad) / nodeCount / Resource.memory.idealAverageLoad();
                disk = groupUsage(Resource.disk, diskLoad) / nodeCount / Resource.disk.idealAverageLoad();
            }
            else {
                memory = nodeUsage(Resource.memory, memoryLoad) / Resource.memory.idealAverageLoad();
                disk = nodeUsage(Resource.disk, diskLoad) / Resource.disk.idealAverageLoad();
            }
        }
        else {
            cpu = clusterUsage(Resource.cpu, cpuLoad) / nodeCount / Resource.cpu.idealAverageLoad();
            if (allocation.clusterType().isContent()) { // load scales with node share of content
                memory = groupUsage(Resource.memory, memoryLoad) / groupSize / Resource.memory.idealAverageLoad();
                disk = groupUsage(Resource.disk, diskLoad) / groupSize / Resource.disk.idealAverageLoad();
            }
            else {
                memory = nodeUsage(Resource.memory, memoryLoad) / Resource.memory.idealAverageLoad();
                disk = nodeUsage(Resource.disk, diskLoad) / Resource.disk.idealAverageLoad();
            }
        }

        return allocation.realResources().withVcpu(cpu).withMemoryGb(memory).withDiskGb(disk);
    }

    private double clusterUsage(Resource resource, double load) {
        return nodeUsage(resource, load) * allocation.nodes();
    }

    private double groupUsage(Resource resource, double load) {
        return nodeUsage(resource, load) * groupSize;
    }

    private double nodeUsage(Resource resource, double load) {
        return load * resource.valueFrom(allocation.realResources());
    }

}
