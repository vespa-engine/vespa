// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.NodeResources;

/**
 * Provides iteration over possible cluster resource allocations given a target total load
 * and current groups/nodes allocation.
 */
public class ResourceIterator {

    // Configured min and max nodes TODO: These should come from the application package
    private static final int minimumNodesPerCluster = 3; // Since this is with redundancy it cannot be lower than 2
    private static final int maximumNodesPerCluster = 150;

    // When a query is issued on a node the cost is the sum of a fixed cost component and a cost component
    // proportional to document count. We must account for this when comparing configurations with more or fewer nodes.
    // TODO: Measure this, and only take it into account with queries
    private static final double fixedCpuCostFraction = 0.1;

    private final double totalCpu;
    private final double totalMemory;
    private final double totalDisk;
    private final int nodeIncrement;
    private final int groupSize;
    private final boolean singleGroupMode;
    private final NodeResources resourcesPrototype;

    private int currentNodes;

    public ResourceIterator(double totalCpu, double totalMemory, double totalDisk, ClusterResources currentAllocation) {
        this.totalCpu = totalCpu;
        this.totalMemory = totalMemory;
        this.totalDisk = totalDisk;

        // ceil: If the division does not produce a whole number we assume some node is missing
        groupSize = (int)Math.ceil((double)currentAllocation.nodes() / currentAllocation.groups());
        resourcesPrototype = currentAllocation.nodeResources();

        // What number of nodes is it effective to add or remove at the time from this cluster?
        // This is the group size, since we (for now) assume the group size is decided by someone wiser than us
        // and we decide tyhe number of groups.
        // The exception is when we only have one group, where we can add and remove single nodes in it.
        singleGroupMode = currentAllocation.groups() == 1;
        nodeIncrement = singleGroupMode ? 1 : groupSize;

        currentNodes = currentAllocation.nodes();
        while (currentNodes - nodeIncrement >= minimumNodesPerCluster
               && (singleGroupMode || currentNodes - nodeIncrement > groupSize)) // group level redundancy
            currentNodes -= nodeIncrement;
    }

    public ClusterResources next() {
        int nodesWithRedundancy = currentNodes - (singleGroupMode ? 1 : groupSize);
        ClusterResources next = new ClusterResources(currentNodes,
                                                     singleGroupMode ? 1 : currentNodes / groupSize,
                                                     resourcesFor(nodesWithRedundancy));
        currentNodes += nodeIncrement;
        return next;
    }

    public boolean hasNext() {
        return currentNodes <= maximumNodesPerCluster;
    }

    /** Returns the resources needed per node to be at ideal load given a target node count and total resource allocation */
    private NodeResources resourcesFor(int nodeCount) {
        double cpu;
        if (singleGroupMode) {
            // Since we're changing fan-out, adjust cpu to higher/lower target load when we decrease/increase it respectively.
            // Specifically, we're not scaling the fixed portion of the cpu cost with the added/removed nodes
            // since each node incurs it, achieved by dividing the fixed fraction by the original group size instead
            cpu = fixedCpuCostFraction       * totalCpu / groupSize / Resource.cpu.idealAverageLoad() +
                  (1 - fixedCpuCostFraction) * totalCpu / nodeCount / Resource.cpu.idealAverageLoad();
        }
        else {
            cpu = totalCpu / nodeCount / Resource.cpu.idealAverageLoad();
        }
        return resourcesPrototype.withVcpu(cpu)
                                 .withMemoryGb(totalMemory / nodeCount / Resource.memory.idealAverageLoad())
                                 .withDiskGb(totalDisk / nodeCount / Resource.disk.idealAverageLoad());
    }

}
