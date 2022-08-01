// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.createFixtureWithReportedUsages;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.exhaustion;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.forNode;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.setOf;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.usage;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResourceUsageStatsTest {

    private final double DELTA = 0.00001;

    @Test
    void disk_and_memory_utilization_is_max_among_all_content_nodes() {
        var stats = ResourceUsageStats.calculateFrom(createNodeInfo(
                forNode(1, usage("disk", 0.3), usage("memory", 0.6)),
                forNode(2, usage("disk", 0.4), usage("memory", 0.5))),
                createFeedBlockLimits(0.8, 0.9),
                Optional.empty());
        assertEquals(0.4 / 0.8, stats.getMaxDiskUtilization(), DELTA);
        assertEquals(0.6 / 0.9, stats.getMaxMemoryUtilization(), DELTA);
        assertEquals(0.8, stats.getDiskLimit(), DELTA);
        assertEquals(0.9, stats.getMemoryLimit(), DELTA);
    }

    @Test
    void disk_and_memory_utilization_is_zero_if_no_samples_are_available() {
        var stats = ResourceUsageStats.calculateFrom(createNodeInfo(
                forNode(1), forNode(2)),
                createFeedBlockLimits(0.8, 0.9),
                Optional.empty());
        assertEquals(0.0, stats.getMaxDiskUtilization(), DELTA);
        assertEquals(0.0, stats.getMaxMemoryUtilization(), DELTA);
        assertEquals(0.8, stats.getDiskLimit(), DELTA);
        assertEquals(0.9, stats.getMemoryLimit(), DELTA);
    }

    @Test
    void nodes_above_limit_is_zero_without_feed_block_status() {
        var stats = ResourceUsageStats.calculateFrom(Collections.emptyList(), Collections.emptyMap(), Optional.empty());
        assertEquals(0, stats.getNodesAboveLimit());
    }

    @Test
    void nodes_above_limit_is_equal_to_node_resource_exhaustions() {
        var stats = ResourceUsageStats.calculateFrom(Collections.emptyList(), Collections.emptyMap(),
                createFeedBlock(exhaustion(1, "disk"), exhaustion(2, "memory")));
        assertEquals(2, stats.getNodesAboveLimit());
    }

    @Test
    void nodes_above_limit_counts_each_node_only_once() {
        var stats = ResourceUsageStats.calculateFrom(Collections.emptyList(), Collections.emptyMap(),
                createFeedBlock(exhaustion(1, "disk"), exhaustion(1, "memory")));
        assertEquals(1, stats.getNodesAboveLimit());
    }

    private static Collection<NodeInfo> createNodeInfo(FeedBlockUtil.NodeAndUsages... nodeAndUsages) {
        return createFixtureWithReportedUsages(nodeAndUsages).cluster().getNodeInfos();
    }

    private static Map<String, Double> createFeedBlockLimits(double diskLimit, double memoryLimit) {
        return Map.of("disk", diskLimit, "memory", memoryLimit);
    }

    private static Optional<ClusterStateBundle.FeedBlock> createFeedBlock(NodeResourceExhaustion... exhaustions) {
        return Optional.of(new ClusterStateBundle.FeedBlock(true, "", setOf(exhaustions)));
    }
}

