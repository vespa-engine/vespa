// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClusterStatsChangeTrackerTest {

    private static class StatsBuilder {
        private final ContentClusterStatsBuilder builder = new ContentClusterStatsBuilder();

        StatsBuilder bucketsPending(int contentNodeIndex) {
            builder.add(contentNodeIndex, "global", 5, 1);
            return this;
        }

        StatsBuilder inSync(int contentNodeIndex) {
            builder.add(contentNodeIndex, "global", 5, 0);
            return this;
        }

        ContentClusterStats build() {
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
        private final double minMergeCompletionRatio;

        private Fixture(Set<Integer> contentNodeIndices,
                        double minMergeCompletionRatio) {
            this.contentNodeIndices = contentNodeIndices;
            this.minMergeCompletionRatio = minMergeCompletionRatio;
            aggregator = new ClusterStatsAggregator(Sets.newHashSet(1), this.contentNodeIndices);
            tracker = new ClusterStatsChangeTracker(aggregator.getAggregatedStats(), minMergeCompletionRatio);
        }

        static Fixture empty() {
            return new Fixture(Sets.newHashSet(0, 1), 1.0);
        }

        static Fixture fromStats(StatsBuilder builder) {
            Fixture result = new Fixture(Sets.newHashSet(0, 1), 1.0);
            result.updateStats(builder);
            return result;
        }

        void newAggregatedStats(StatsBuilder builder) {
            aggregator = new ClusterStatsAggregator(Sets.newHashSet(1), contentNodeIndices);
            updateStats(builder);
            tracker.updateAggregatedStats(aggregator.getAggregatedStats(), minMergeCompletionRatio);
        }

        private void updateStats(StatsBuilder builder) {
            aggregator.updateForDistributor(1, builder.build());
        }

        boolean statsHaveChanged() {
            return tracker.statsHaveChanged();
        }

    }

    @Test
    void stats_have_not_changed_if_not_all_distributors_are_updated() {
        Fixture f = Fixture.empty();
        assertFalse(f.statsHaveChanged());
    }

    @Test
    void stats_have_changed_if_in_sync_node_not_found_in_previous_stats() {
        Fixture f = Fixture.fromStats(stats().inSync(0));
        assertTrue(f.statsHaveChanged());
    }

    @Test
    void stats_have_changed_if_buckets_pending_node_not_found_in_previous_stats() {
        Fixture f = Fixture.fromStats(stats().bucketsPending(0));
        assertTrue(f.statsHaveChanged());
    }

    @Test
    void stats_have_changed_if_one_node_has_in_sync_to_buckets_pending_transition() {
        Fixture f = Fixture.fromStats(stats().bucketsPending(0).inSync(1));
        f.newAggregatedStats(stats().bucketsPending(0).bucketsPending(1));
        assertTrue(f.statsHaveChanged());
    }

    @Test
    void stats_have_changed_if_one_node_has_buckets_pending_to_in_sync_transition() {
        Fixture f = Fixture.fromStats(stats().bucketsPending(0).bucketsPending(1));
        f.newAggregatedStats(stats().bucketsPending(0).inSync(1));
        assertTrue(f.statsHaveChanged());
    }

    @Test
    void stats_have_not_changed_if_no_nodes_have_changed_state() {
        Fixture f = Fixture.fromStats(stats().bucketsPending(0).bucketsPending(1));
        f.newAggregatedStats(stats().bucketsPending(0).bucketsPending(1));
        assertFalse(f.statsHaveChanged());
    }

}
