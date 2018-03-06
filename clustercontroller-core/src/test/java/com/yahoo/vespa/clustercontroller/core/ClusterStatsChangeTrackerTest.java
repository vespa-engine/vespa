// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.google.common.collect.Sets;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClusterStatsChangeTrackerTest {

    private static class Fixture {
        private ClusterStatsAggregator aggregator;
        private ClusterStatsChangeTracker tracker;

        public Fixture() {
            aggregator = new ClusterStatsAggregator(Sets.newHashSet(1), Sets.newHashSet(2));
            tracker = new ClusterStatsChangeTracker(aggregator.getAggregatedStats());
        }

        public void setBucketsPendingStats() {
            updateStats(1);
        }

        public void setInSyncStats() {
            updateStats(0);
        }

        public void updateStats(long bucketsPending) {
            aggregator.updateForDistributor(1, new ContentClusterStatsBuilder()
                    .add(2, "global", 5, bucketsPending).build());
        }

        public void updateAggregatedStats() {
            aggregator = new ClusterStatsAggregator(Sets.newHashSet(1), Sets.newHashSet(2));
            tracker.updateAggregatedStats(aggregator.getAggregatedStats());
        }

        public boolean statsHaveChanged() {
            return tracker.statsHaveChanged();
        }

    }

    @Test
    public void stats_have_not_changed_if_not_all_distributors_are_updated() {
        Fixture f = new Fixture();
        assertFalse(f.statsHaveChanged());
    }

    @Test
    public void stats_have_changed_if_previous_buckets_pending_stats_are_different_from_current() {
        Fixture f = new Fixture();

        f.setInSyncStats();
        assertFalse(f.statsHaveChanged());
        f.setBucketsPendingStats();
        assertTrue(f.statsHaveChanged());

        f.updateAggregatedStats(); // previous stats may now have buckets pending

        f.setInSyncStats();
        assertTrue(f.statsHaveChanged());
        f.setBucketsPendingStats();
        assertFalse(f.statsHaveChanged());
    }

}
