// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.State;
import org.junit.jupiter.api.Test;

import static com.yahoo.vespa.clustercontroller.core.ClusterFixture.storageNode;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.createFixtureWithReportedUsages;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.exhaustion;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.forNode;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.mapOf;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.setOf;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.usage;
import static org.junit.jupiter.api.Assertions.*;

public class ResourceExhaustionCalculatorTest {

    private static String decorate(ClusterFixture cf, String msg) {
        return ResourceExhaustionCalculator.decoratedMessage(cf.cluster(), msg);
    }

    @Test
    void no_feed_block_returned_when_no_resources_lower_than_limit() {
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.5), usage("memory", 0.8)));
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.49), usage("memory", 0.79)),
                forNode(2, usage("disk", 0.4), usage("memory", 0.6)));
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster());
        assertNull(feedBlock);
    }

    @Test
    void feed_block_returned_when_single_resource_beyond_limit() {
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.5), usage("memory", 0.8)));
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.51), usage("memory", 0.79)),
                forNode(2, usage("disk", 0.4), usage("memory", 0.6)));
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster());
        assertNotNull(feedBlock);
        assertTrue(feedBlock.blockFeedInCluster());
        // Manually verify message decoration in this test
        assertEquals("in content cluster 'foo': disk on node 1 [storage.1.local] is 51.0% full " +
                     "(the configured limit is 50.0%). See https://docs.vespa.ai/en/operations/feed-block.html",
                     feedBlock.getDescription());
    }

    @Test
    void feed_block_description_can_contain_optional_name_component() {
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.5), usage("memory", 0.8)));
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", "a-fancy-disk", 0.51), usage("memory", 0.79)),
                forNode(2, usage("disk", 0.4), usage("memory", 0.6)));
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster());
        assertNotNull(feedBlock);
        assertTrue(feedBlock.blockFeedInCluster());
        assertEquals(decorate(cf, "disk:a-fancy-disk on node 1 [storage.1.local] is 51.0% full (the configured limit is 50.0%)"),
                     feedBlock.getDescription());
    }

    @Test
    void missing_or_malformed_rpc_addresses_are_emitted_as_unknown_hostnames() {
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.5), usage("memory", 0.8)));
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.51), usage("memory", 0.79)),
                forNode(2, usage("disk", 0.4), usage("memory", 0.85)));
        cf.cluster().getNodeInfo(storageNode(1)).setRpcAddress(null);
        cf.cluster().getNodeInfo(storageNode(2)).setRpcAddress("max mekker");
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster());
        assertNotNull(feedBlock);
        assertTrue(feedBlock.blockFeedInCluster());
        assertEquals(decorate(cf, "disk on node 1 [unknown hostname] is 51.0% full (the configured limit is 50.0%), " +
                                  "memory on node 2 [unknown hostname] is 85.0% full (the configured limit is 80.0%)"),
                     feedBlock.getDescription());
    }

    @Test
    void feed_block_returned_when_multiple_resources_beyond_limit() {
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.4), usage("memory", 0.8)));
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.51), usage("memory", 0.85)),
                forNode(2, usage("disk", 0.45), usage("memory", 0.6)));
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster());
        assertNotNull(feedBlock);
        assertTrue(feedBlock.blockFeedInCluster());
        assertEquals(decorate(cf, "disk on node 1 [storage.1.local] is 51.0% full (the configured limit is 40.0%), " +
                                  "memory on node 1 [storage.1.local] is 85.0% full (the configured limit is 80.0%), " +
                                  "disk on node 2 [storage.2.local] is 45.0% full (the configured limit is 40.0%)"),
                     feedBlock.getDescription());
    }

    @Test
    void feed_block_description_is_bounded_in_number_of_described_resources() {
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.4), usage("memory", 0.8)));
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.51), usage("memory", 0.85)),
                forNode(2, usage("disk", 0.45), usage("memory", 0.6)),
                forNode(3, usage("disk", 0.6), usage("memory", 0.9)));
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster());
        assertNotNull(feedBlock);
        assertTrue(feedBlock.blockFeedInCluster());
        assertEquals(decorate(cf, "disk on node 1 [storage.1.local] is 51.0% full (the configured limit is 40.0%), " +
                                  "memory on node 1 [storage.1.local] is 85.0% full (the configured limit is 80.0%), " +
                                  "disk on node 2 [storage.2.local] is 45.0% full (the configured limit is 40.0%) (... and 2 more)"),
                     feedBlock.getDescription());
    }

    @Test
    void no_feed_block_returned_when_feed_block_disabled() {
        var calc = new ResourceExhaustionCalculator(false, mapOf(usage("disk", 0.5), usage("memory", 0.8)));
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.51), usage("memory", 0.79)),
                forNode(2, usage("disk", 0.4), usage("memory", 0.6)));
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster());
        assertNull(feedBlock);
    }

    @Test
    void retain_node_feed_block_status_when_within_hysteresis_window_limit_crossed_edge_case() {
        var curFeedBlock = ClusterStateBundle.FeedBlock.blockedWith("foo", setOf(exhaustion(1, "memory", 0.51)));
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.5), usage("memory", 0.5)), curFeedBlock, 0.1);
        // Node 1 goes from 0.51 to 0.49, crossing the 0.5 threshold. Should still be blocked.
        // Node 2 is at 0.49 but was not previously blocked and should not be blocked now either.
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.3), usage("memory", 0.49)),
                forNode(2, usage("disk", 0.3), usage("memory", 0.49)));
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster());
        assertNotNull(feedBlock);
        // TODO should we not change the limits themselves? Explicit mention of hysteresis state?
        assertEquals(decorate(cf, "memory on node 1 [storage.1.local] is 49.0% full (the configured limit is 40.0%)"),
                     feedBlock.getDescription());
    }

    @Test
    void retain_node_feed_block_status_when_within_hysteresis_window_under_limit_edge_case() {
        var curFeedBlock = ClusterStateBundle.FeedBlock.blockedWith("foo", setOf(exhaustion(1, "memory", 0.49)));
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.5), usage("memory", 0.5)), curFeedBlock, 0.1);
        // Node 1 goes from 0.49 to 0.48, NOT crossing the 0.5 threshold. Should still be blocked.
        // Node 2 is at 0.49 but was not previously blocked and should not be blocked now either.
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.3), usage("memory", 0.48)),
                forNode(2, usage("disk", 0.3), usage("memory", 0.49)));
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster());
        assertNotNull(feedBlock);
        assertEquals(decorate(cf, "memory on node 1 [storage.1.local] is 48.0% full (the configured limit is 40.0%)"),
                     feedBlock.getDescription());
    }

    @Test
    void retained_node_feed_block_cleared_once_hysteresis_threshold_is_passed() {
        var curFeedBlock = ClusterStateBundle.FeedBlock.blockedWith("foo", setOf(exhaustion(1, "memory", 0.48)));
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.5), usage("memory", 0.5)), curFeedBlock, 0.1);
        // Node 1 goes from 0.48 to 0.39. Should be unblocked
        // Node 2 is at 0.49 but was not previously blocked and should not be blocked now either.
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.3), usage("memory", 0.39)),
                forNode(2, usage("disk", 0.3), usage("memory", 0.49)));
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster());
        assertNull(feedBlock);
    }

    @Test
    void node_must_be_available_in_reported_state_to_trigger_feed_block() {
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.5), usage("memory", 0.8)));
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.51), usage("memory", 0.79)),
                forNode(2, usage("disk", 0.6), usage("memory", 0.6)));
        cf.reportStorageNodeState(1, State.DOWN);
        cf.reportStorageNodeState(2, State.DOWN);
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster());
        assertNull(feedBlock);
    }

    @Test
    void node_must_be_available_in_wanted_state_to_trigger_feed_block() {
        var calc = new ResourceExhaustionCalculator(true, mapOf(usage("disk", 0.5), usage("memory", 0.8)));
        var cf = createFixtureWithReportedUsages(forNode(1, usage("disk", 0.51), usage("memory", 0.79)),
                forNode(2, usage("disk", 0.6), usage("memory", 0.6)));
        cf.proposeStorageNodeWantedState(1, State.DOWN);
        cf.proposeStorageNodeWantedState(2, State.MAINTENANCE);
        var feedBlock = calc.inferContentClusterFeedBlockOrNull(cf.cluster());
        assertNull(feedBlock);
    }

}
