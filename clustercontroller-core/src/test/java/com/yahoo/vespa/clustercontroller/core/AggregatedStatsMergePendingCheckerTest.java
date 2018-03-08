// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AggregatedStatsMergePendingCheckerTest {

    private static class Fixture {

        private final AggregatedClusterStats mockAggregatedStats = mock(AggregatedClusterStats.class);
        private final AggregatedStatsMergePendingChecker checker;

        public Fixture(ContentClusterStatsBuilder builder, boolean hasUpdatesFromAllDistributors) {
            when(mockAggregatedStats.getStats()).thenReturn(builder.build());
            when(mockAggregatedStats.hasUpdatesFromAllDistributors()).thenReturn(hasUpdatesFromAllDistributors);
            this.checker = new AggregatedStatsMergePendingChecker(mockAggregatedStats);
        }

        public static Fixture fromBucketsPending(long bucketsPending) {
            return new Fixture(new ContentClusterStatsBuilder()
                    .add(1, "default", 5, bucketsPending), true);
        }

        public static Fixture fromInvalidBucketStats() {
            return new Fixture(new ContentClusterStatsBuilder()
                    .add(1, "default"), true);
        }

        public static Fixture fromIncompleteStats() {
            return new Fixture(new ContentClusterStatsBuilder(), false);
        }

        public boolean mayHaveMergesPending(String bucketSpace, int contentNodeIndex) {
            return checker.mayHaveMergesPending(bucketSpace, contentNodeIndex);
        }

    }

    @Test
    public void unknown_content_node_may_have_merges_pending() {
        Fixture f = Fixture.fromBucketsPending(1);
        assertTrue(f.mayHaveMergesPending("default", 2));
    }

    @Test
    public void unknown_bucket_space_has_no_merges_pending() {
        Fixture f = Fixture.fromBucketsPending(1);
        assertFalse(f.mayHaveMergesPending("global", 1));
    }

    @Test
    public void valid_bucket_space_stats_can_have_no_merges_pending() {
        Fixture f = Fixture.fromBucketsPending(0);
        assertFalse(f.mayHaveMergesPending("default", 1));
    }

    @Test
    public void valid_bucket_space_stats_may_have_merges_pending() {
        Fixture f = Fixture.fromBucketsPending(1);
        assertTrue(f.mayHaveMergesPending("default", 1));
    }

    @Test
    public void invalid_bucket_space_stats_may_have_merges_pending() {
        Fixture f = Fixture.fromInvalidBucketStats();
        assertTrue(f.mayHaveMergesPending("default", 1));
    }

    @Test
    public void cluster_without_updates_from_all_distributors_may_have_merges_pending() {
        Fixture f = Fixture.fromIncompleteStats();
        assertTrue(f.mayHaveMergesPending("default", 1));
    }

}
