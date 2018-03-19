// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClusterStatsChangeTrackerTest {

    private static class StatsBuilder {
        private final ContentClusterStatsBuilder builder = new ContentClusterStatsBuilder();

        public StatsBuilder bucketsPending(int contentNodeIndex) {
            builder.add(contentNodeIndex, "global", 5, 1);
            return this;
        }

        public StatsBuilder inSync(int contentNodeIndex) {
            builder.add(contentNodeIndex, "global", 5, 0);
            return this;
        }

        public ContentClusterStats build() {
            return builder.build();
        }
    }

    private static StatsBuilder stats() {
        return new StatsBuilder();
    }

    private static class Fixture {
        private final Set<Integer> contentNodeIndices;
        private ClusterStatsAggregator aggregator;
        private final ClusterStatsChangeTracker tracker;

        private Fixture(Integer... contentNodeIndices) {
            this.contentNodeIndices = Sets.newHashSet(contentNodeIndices);
            aggregator = new ClusterStatsAggregator(Sets.newHashSet(1), this.contentNodeIndices);
            tracker = new ClusterStatsChangeTracker(aggregator.getAggregatedStats());
        }

        public static Fixture empty() {
            return new Fixture(0, 1);
        }

        public static Fixture fromStats(StatsBuilder builder) {
            Fixture result = new Fixture(0, 1);
            result.updateStats(builder);
            return result;
        }

        public void newAggregatedStats(StatsBuilder builder) {
            aggregator = new ClusterStatsAggregator(Sets.newHashSet(1), contentNodeIndices);
            updateStats(builder);
            tracker.updateAggregatedStats(aggregator.getAggregatedStats());
        }

        private void updateStats(StatsBuilder builder) {
            aggregator.updateForDistributor(1, builder.build());
        }

        public boolean statsHaveChanged() {
            return tracker.statsHaveChanged();
        }

    }

    @Test
    public void stats_have_not_changed_if_not_all_distributors_are_updated() {
        Fixture f = Fixture.empty();
        assertFalse(f.statsHaveChanged());
    }

    @Test
    public void stats_have_changed_if_in_sync_node_not_found_in_previous_stats() {
        Fixture f = Fixture.fromStats(stats().inSync(0));
        assertTrue(f.statsHaveChanged());
    }

    @Test
    public void stats_have_changed_if_buckets_pending_node_not_found_in_previous_stats() {
        Fixture f = Fixture.fromStats(stats().bucketsPending(0));
        assertTrue(f.statsHaveChanged());
    }

    @Test
    public void stats_have_changed_if_one_node_has_in_sync_to_buckets_pending_transition() {
        Fixture f = Fixture.fromStats(stats().bucketsPending(0).inSync(1));
        f.newAggregatedStats(stats().bucketsPending(0).bucketsPending(1));
        assertTrue(f.statsHaveChanged());
    }

    @Test
    public void stats_have_changed_if_one_node_has_buckets_pending_to_in_sync_transition() {
        Fixture f = Fixture.fromStats(stats().bucketsPending(0).bucketsPending(1));
        f.newAggregatedStats(stats().bucketsPending(0).inSync(1));
        assertTrue(f.statsHaveChanged());
    }

    @Test
    public void stats_have_not_changed_if_no_nodes_have_changed_state() {
        Fixture f = Fixture.fromStats(stats().bucketsPending(0).bucketsPending(1));
        f.newAggregatedStats(stats().bucketsPending(0).bucketsPending(1));
        assertFalse(f.statsHaveChanged());
    }

}
