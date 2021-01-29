// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import org.junit.Test;

import java.util.Arrays;

import static com.yahoo.vespa.clustercontroller.core.ClusterFixture.storageNode;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.NodeAndUsages;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.forNode;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.mapOf;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.setOf;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.usage;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.createResourceUsageJson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ResourceExhaustionCalculatorTest {

    private static ClusterFixture createFixtureWithReportedUsages(NodeAndUsages... nodeAndUsages) {
        var highestIndex = Arrays.stream(nodeAndUsages).mapToInt(u -> u.index).max();
        if (highestIndex.isEmpty()) {
            throw new IllegalArgumentException("Can't have an empty cluster");
        }
        var cf = ClusterFixture.forFlatCluster(highestIndex.getAsInt() + 1).bringEntireClusterUp();
        for (var nu : nodeAndUsages) {
            cf.cluster().getNodeInfo(storageNode(nu.index))
                    .setHostInfo(HostInfo.createHostInfo(createResourceUsageJson(nu.usages)));
        }
        return cf;
    }

    @Test
    public void no_feed_block_returned_when_no_resources_lower_than_limit() {
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.5), usage("memory", 0.8)));
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.49), usage("memory", 0.79)),
                                                 forNode(2, usage("disk", 0.4), usage("memory", 0.6)));
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster().getNodeInfo());
        assertNull(feedBlock);
    }

    @Test
    public void feed_block_returned_when_single_resource_beyond_limit() {
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.5), usage("memory", 0.8)));
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.51), usage("memory", 0.79)),
                                                 forNode(2, usage("disk", 0.4), usage("memory", 0.6)));
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster().getNodeInfo());
        assertNotNull(feedBlock);
        assertTrue(feedBlock.blockFeedInCluster());
        assertEquals("disk on node 1 (0.510 > 0.500)", feedBlock.getDescription());
    }

    @Test
    public void feed_block_description_can_contain_optional_name_component() {
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.5), usage("memory", 0.8)));
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", "a-fancy-disk", 0.51), usage("memory", 0.79)),
                forNode(2, usage("disk", 0.4), usage("memory", 0.6)));
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster().getNodeInfo());
        assertNotNull(feedBlock);
        assertTrue(feedBlock.blockFeedInCluster());
        assertEquals("disk:a-fancy-disk on node 1 (0.510 > 0.500)", feedBlock.getDescription());
    }

    @Test
    public void feed_block_returned_when_multiple_resources_beyond_limit() {
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.4), usage("memory", 0.8)));
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.51), usage("memory", 0.85)),
                                                 forNode(2, usage("disk", 0.45), usage("memory", 0.6)));
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster().getNodeInfo());
        assertNotNull(feedBlock);
        assertTrue(feedBlock.blockFeedInCluster());
        assertEquals("disk on node 1 (0.510 > 0.400), " +
                     "memory on node 1 (0.850 > 0.800), " +
                     "disk on node 2 (0.450 > 0.400)",
                     feedBlock.getDescription());
    }

    @Test
    public void feed_block_description_is_bounded_in_number_of_described_resources() {
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.4), usage("memory", 0.8)));
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.51), usage("memory", 0.85)),
                                                 forNode(2, usage("disk", 0.45), usage("memory", 0.6)),
                                                 forNode(3, usage("disk", 0.6), usage("memory", 0.9)));
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster().getNodeInfo());
        assertNotNull(feedBlock);
        assertTrue(feedBlock.blockFeedInCluster());
        assertEquals("disk on node 1 (0.510 > 0.400), " +
                     "memory on node 1 (0.850 > 0.800), " +
                     "disk on node 2 (0.450 > 0.400) (... and 2 more)",
                     feedBlock.getDescription());
    }

    @Test
    public void no_feed_block_returned_when_feed_block_disabled() {
        var calc = new ResourceExhaustionCalculator(false, mapOf(usage("disk", 0.5), usage("memory", 0.8)));
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.51), usage("memory", 0.79)),
                                                 forNode(2, usage("disk", 0.4), usage("memory", 0.6)));
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster().getNodeInfo());
        assertNull(feedBlock);
    }

}
