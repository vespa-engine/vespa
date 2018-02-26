// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import org.junit.Test;

import static com.yahoo.vespa.clustercontroller.core.ContentNodeStats.BucketSpaceStats;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContentNodeStatsTest {

    @Test
    public void bucket_space_stats_can_transition_from_valid_to_invalid() {
        BucketSpaceStats stats = BucketSpaceStats.of(5,1);
        assertTrue(stats.valid());

        stats.merge(BucketSpaceStats.invalid(), 1);
        assertFalse(stats.valid());
        assertEquals(BucketSpaceStats.invalid(5, 1), stats);
    }

    @Test
    public void bucket_space_stats_can_transition_from_invalid_to_valid() {
        BucketSpaceStats stats = BucketSpaceStats.invalid();
        assertFalse(stats.valid());

        stats.merge(BucketSpaceStats.of(5, 1), 1);
        assertFalse(stats.valid());
        stats.merge(BucketSpaceStats.invalid(), -1);
        assertTrue(stats.valid());
        assertEquals(BucketSpaceStats.of(5, 1), stats);
    }

    @Test
    public void bucket_space_stats_tracks_multiple_layers_of_invalid() {
        BucketSpaceStats stats = BucketSpaceStats.invalid();
        stats.merge(BucketSpaceStats.invalid(), 1);
        assertFalse(stats.valid());
        stats.merge(BucketSpaceStats.invalid(), 1);
        assertFalse(stats.valid());
        stats.merge(BucketSpaceStats.of(5, 1), 1);
        assertFalse(stats.valid());

        stats.merge(BucketSpaceStats.invalid(), -1);
        assertFalse(stats.valid());
        stats.merge(BucketSpaceStats.invalid(), -1);
        assertFalse(stats.valid());
        stats.merge(BucketSpaceStats.invalid(), -1);
        assertTrue(stats.valid());
        assertEquals(BucketSpaceStats.of(5, 1), stats);
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
