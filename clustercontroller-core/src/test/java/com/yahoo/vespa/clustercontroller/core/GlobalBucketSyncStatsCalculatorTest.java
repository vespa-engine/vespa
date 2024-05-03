// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GlobalBucketSyncStatsCalculatorTest {

    private static ContentNodeStatsBuilder globalStatsBuilder() {
        return ContentNodeStatsBuilder.forNode(-1);
    }

    private static void assertComputedRatio(double expected, ContentNodeStatsBuilder statsBuilder) {
        var maybeRatio = GlobalBucketSyncStatsCalculator.clusterBucketsOutOfSyncRatio(statsBuilder.build());
        if (maybeRatio.isEmpty()) {
            throw new IllegalArgumentException("Expected calculation to yield a value, but was empty");
        }
        assertEquals(expected, maybeRatio.get(), 0.00001);
    }

    private static void assertEmptyComputedRatio(ContentNodeStatsBuilder statsBuilder) {
        var maybeRatio = GlobalBucketSyncStatsCalculator.clusterBucketsOutOfSyncRatio(statsBuilder.build());
        assertTrue(maybeRatio.isEmpty());
    }

    @Test
    void no_buckets_imply_fully_in_sync() {
        // Can't have anything out of sync if you don't have anything to be out of sync with *taps side of head*
        assertComputedRatio(0.0, globalStatsBuilder().add("default", 0, 0));
    }

    @Test
    void no_pending_buckets_implies_fully_in_sync() {
        assertComputedRatio(0.0, globalStatsBuilder().add("default", 100, 0));
        assertComputedRatio(0.0, globalStatsBuilder().add("default", 100, 0).add("global", 50, 0));
    }

    @Test
    void invalid_stats_returns_empty() {
        assertEmptyComputedRatio(globalStatsBuilder().add("default", ContentNodeStats.BucketSpaceStats.invalid()));
        assertEmptyComputedRatio(globalStatsBuilder()
                .add("default", 100, 0)
                .add("global", ContentNodeStats.BucketSpaceStats.invalid()));
    }

    @Test
    void pending_buckets_return_expected_ratio() {
        assertComputedRatio(0.50, globalStatsBuilder().add("default", 10, 5));
        assertComputedRatio(0.80, globalStatsBuilder().add("default", 10, 8));
        assertComputedRatio(0.10, globalStatsBuilder().add("default", 100, 10));
        assertComputedRatio(0.01, globalStatsBuilder().add("default", 100, 1));
        assertComputedRatio(0.05, globalStatsBuilder().add("default", 50, 5).add("global", 50, 0));
        assertComputedRatio(0.05, globalStatsBuilder().add("default", 50, 0).add("global", 50, 5));
        assertComputedRatio(0.10, globalStatsBuilder().add("default", 50, 5).add("global", 50, 5));
    }

}
