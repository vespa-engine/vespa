// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AggregatedStatsMergePendingCheckerTest {

    private static class Fixture {

        private final AggregatedClusterStats mockAggregatedStats = mock(AggregatedClusterStats.class);
        private final AggregatedStatsMergePendingChecker checker;

        Fixture(ContentClusterStatsBuilder builder,
                boolean hasUpdatesFromAllDistributors,
                double minMergeCompletionRatio) {
            when(mockAggregatedStats.getStats()).thenReturn(builder.build());
            when(mockAggregatedStats.hasUpdatesFromAllDistributors()).thenReturn(hasUpdatesFromAllDistributors);
            this.checker = new AggregatedStatsMergePendingChecker(mockAggregatedStats, minMergeCompletionRatio);
        }

        static Fixture fromBucketsPending(long bucketsPending) {
            return new Fixture(new ContentClusterStatsBuilder()
                    .add(1, "default", 5, bucketsPending),
                    true, 1.0);
        }

        static Fixture fromBucketsPending(long bucketsPending, double minMergeCompletionRatio) {
            return new Fixture(new ContentClusterStatsBuilder()
                    .add(1, "default", 5, bucketsPending),
                    true, minMergeCompletionRatio);
        }

        static Fixture fromInvalidBucketStats() {
            return new Fixture(new ContentClusterStatsBuilder()
                    .add(1, "default"),
                    true, 1.0);
        }

        static Fixture fromIncompleteStats() {
            return new Fixture(new ContentClusterStatsBuilder(), false, 1.0);
        }

        boolean mayHaveMergesPending(String bucketSpace, int contentNodeIndex) {
            return checker.mayHaveMergesPending(bucketSpace, contentNodeIndex);
        }

    }

    @Test
    void unknown_content_node_may_have_merges_pending() {
        Fixture f = Fixture.fromBucketsPending(1);
        assertTrue(f.mayHaveMergesPending("default", 2));
    }

    @Test
    void unknown_bucket_space_has_no_merges_pending() {
        Fixture f = Fixture.fromBucketsPending(1);
        assertFalse(f.mayHaveMergesPending("global", 1));
    }

    @Test
    void valid_bucket_space_stats_can_have_no_merges_pending() {
        Fixture f = Fixture.fromBucketsPending(0);
        assertFalse(f.mayHaveMergesPending("default", 1));
    }

    @Test
    void valid_bucket_space_stats_may_have_merges_pending() {
        Fixture f = Fixture.fromBucketsPending(1);
        assertTrue(f.mayHaveMergesPending("default", 1));
    }

    @Test
    void invalid_bucket_space_stats_may_have_merges_pending() {
        Fixture f = Fixture.fromInvalidBucketStats();
        assertTrue(f.mayHaveMergesPending("default", 1));
    }

    @Test
    void cluster_without_updates_from_all_distributors_may_have_merges_pending() {
        Fixture f = Fixture.fromIncompleteStats();
        assertTrue(f.mayHaveMergesPending("default", 1));
    }

    @Test
    void min_merge_completion_ratio_is_used_when_calculating_may_have_merges_pending() {
        // Completion ratio is (5-3)/5 = 0.4
        assertTrue(Fixture.fromBucketsPending(3, 0.6).mayHaveMergesPending("default", 1));
        // Completion ratio is (5-2)/5 = 0.6
        assertFalse(Fixture.fromBucketsPending(2, 0.6).mayHaveMergesPending("default", 1));
    }

}
