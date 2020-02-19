// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.NodeResources;

/**
 * Provides iteration over possible cluster resource allocations given a target total load
 * and current groups/nodes allocation.
 */
public class ResourceIterator {

    // Configured min and max nodes TODO: These should come from the application package
    private static final int minimumNodesPerCluster = 3;
    private static final int maximumNodesPerCluster = 10;

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
        resourcesPrototype = currentAllocation.resources();

        // What number of nodes is it effective to add or remove at the time from this cluster?
        // This is the group size, since we (for now) assume the group size is decided by someone wiser than us
        // and we decide tyhe number of groups.
        // The exception is when we only have one group, where we can add and remove single nodes in it.
        singleGroupMode = currentAllocation.groups() == 1;
        nodeIncrement = singleGroupMode ? 1 : groupSize;

        currentNodes = currentAllocation.nodes();
        while (currentNodes - nodeIncrement >= minimumNodesPerCluster)
            currentNodes -= nodeIncrement;
        if (currentNodes - nodeIncrement > 0) // Decrease once more since we'll increment for redundancy later
            currentNodes -= nodeIncrement;
    }

    public ClusterResources next() {
        ClusterResources next = new ClusterResources(currentNodes,
                                                     singleGroupMode ? 1 : currentNodes / groupSize,
                                                     resourcesFor(currentNodes));
        currentNodes += nodeIncrement;
        return next;
    }

    public boolean hasNext() {
        // Add node increment for redundancy
        return currentNodes + nodeIncrement <= maximumNodesPerCluster;
    }

    /** Returns the resources needed per node to be at ideal load given a target node count and total resource allocation */
    private NodeResources resourcesFor(int nodeCount) {
        return resourcesPrototype.withVcpu(totalCpu / nodeCount / Resource.cpu.idealAverageLoad())
                                 .withMemoryGb(totalMemory / nodeCount / Resource.memory.idealAverageLoad())
                                 .withDiskGb(totalDisk / nodeCount / Resource.disk.idealAverageLoad());
    }

    public ClusterResources addRedundancyTo(ClusterResources resources) {
        return new ClusterResources(resources.nodes() + nodeIncrement,
                                    singleGroupMode ? 1 : resources.groups() + 1,
                                    resources.resources());
    }


}
