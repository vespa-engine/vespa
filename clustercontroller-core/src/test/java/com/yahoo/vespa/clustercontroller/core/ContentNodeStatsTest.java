// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import org.junit.Test;

import static com.yahoo.vespa.clustercontroller.core.ContentNodeStats.BucketSpaceStats;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContentNodeStatsTest {

    @Test
    public void valid_bucket_space_stats_can_be_invalid_after_merge() {
        BucketSpaceStats stats = BucketSpaceStats.of(5,1);
        assertTrue(stats.valid());

        stats.merge(BucketSpaceStats.invalid(), 1);
        assertFalse(stats.valid());
        assertEquals(5, stats.getBucketsTotal());
        assertEquals(1, stats.getBucketsPending());
    }

    @Test
    public void invalid_bucket_space_stats_is_still_invalid_after_merge() {
        BucketSpaceStats stats = BucketSpaceStats.invalid();
        assertFalse(stats.valid());

        stats.merge(BucketSpaceStats.of(5, 1), 1);
        assertFalse(stats.valid());
        assertEquals(5, stats.getBucketsTotal());
        assertEquals(1, stats.getBucketsPending());
    }

    @Test
    public void invalid_bucket_space_stats_may_have_pending_buckets() {
        assertTrue(BucketSpaceStats.invalid().mayHaveBucketsPending());
    }

    @Test
    public void valid_bucket_space_stats_may_have_pending_buckets() {
        assertTrue(BucketSpaceStats.of(5, 1).mayHaveBucketsPending());
    }

    @Test
    public void valid_bucket_space_stats_may_have_no_pending_buckets() {
        assertFalse(BucketSpaceStats.of(5, 0).mayHaveBucketsPending());
    }
}
