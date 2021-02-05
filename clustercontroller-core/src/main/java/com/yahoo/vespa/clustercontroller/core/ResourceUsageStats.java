// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vespa.clustercontroller.core.hostinfo.ContentNode;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Represents resource usage stats for the cluster that are exposed as metrics.
 */
public class ResourceUsageStats {

    // Max disk utilization (usage / limit) among all content nodes.
    private final double maxDiskUtilization;

    // Max memory utilization (usage / limit) among all content nodes.
    private final double maxMemoryUtilization;

    // The number of content nodes that are above at least one resource limit.
    // When this is above zero feed is blocked in the cluster.
    private final int nodesAboveLimit;

    private static final String diskResource = "disk";
    private static final String memoryResource = "memory";

    public ResourceUsageStats() {
        this.maxDiskUtilization = 0.0;
        this.maxMemoryUtilization = 0.0;
        this.nodesAboveLimit = 0;
    }

    public ResourceUsageStats(double maxDiskUtilization,
                              double maxMemoryUtilization,
                              int nodesAboveLimit) {
        this.maxDiskUtilization = maxDiskUtilization;
        this.maxMemoryUtilization = maxMemoryUtilization;
        this.nodesAboveLimit = nodesAboveLimit;
    }

    public double getMaxDiskUtilization() {
        return maxDiskUtilization;
    }

    public double getMaxMemoryUtilization() {
        return maxMemoryUtilization;
    }

    public int getNodesAboveLimit() {
        return nodesAboveLimit;
    }

    public static ResourceUsageStats calculateFrom(Collection<NodeInfo> nodeInfos,
                                                   Map<String, Double> feedBlockLimits,
                                                   Optional<ClusterStateBundle.FeedBlock> feedBlock) {
        double maxDiskUsage = 0.0;
        double maxMemoryUsage = 0.0;
        for (NodeInfo info : nodeInfos) {
            if (info.isStorage()) {
                var node = info.getHostInfo().getContentNode();
                maxDiskUsage = Double.max(maxDiskUsage, resourceUsageOf(diskResource, node));
                maxMemoryUsage = Double.max(maxMemoryUsage, resourceUsageOf(memoryResource, node));
            }
        }
        int nodesAboveLimit = (feedBlock.isPresent() ? feedBlock.get().getConcreteExhaustions().size() : 0);
        return new ResourceUsageStats(maxDiskUsage / limitOf(diskResource, feedBlockLimits),
                maxMemoryUsage / limitOf(memoryResource, feedBlockLimits),
                nodesAboveLimit);
    }

    private static double resourceUsageOf(String type, ContentNode node) {
        var result = node.resourceUsageOf(type);
        return result.isPresent() ? result.get().getUsage() : 0.0;
    }

    private static double limitOf(String type, Map<String, Double> limits) {
        var result = limits.get(type);
        return (result != null) ? result : 1.0;
    }
}

