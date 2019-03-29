// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;

import java.util.List;

/**
 * Stores the total amount of resources allocated to a list of nodes
 *
 * @author leandroalves
 */
public class ResourceAllocation {

    private final double cpuCores;
    private final double memoryGb;
    private final double diskGb;

    private ResourceAllocation(double cpuCores, double memoryGb, double diskGb) {
        this.cpuCores = cpuCores;
        this.memoryGb = memoryGb;
        this.diskGb = diskGb;
    }

    public static ResourceAllocation from(List<NodeRepositoryNode> nodes) {
        return new ResourceAllocation(
                nodes.stream().mapToDouble(NodeRepositoryNode::getMinCpuCores).sum(),
                nodes.stream().mapToDouble(NodeRepositoryNode::getMinMainMemoryAvailableGb).sum(),
                nodes.stream().mapToDouble(NodeRepositoryNode::getMinDiskAvailableGb).sum()
        );
    }

    public double usageFraction(ResourceAllocation total) {
        return (cpuCores / total.cpuCores + memoryGb / total.memoryGb + diskGb / total.diskGb) / 3;
    }

    public double getCpuCores() {
        return cpuCores;
    }

    public double getMemoryGb() {
        return memoryGb;
    }

    public double getDiskGb() {
        return diskGb;
    }

}

