// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AggregatedStatsMergePendingCheckerTest {

    private static class Fixture {

        private final AggregatedStatsMergePendingChecker checker;

        public Fixture(ContentClusterStatsBuilder builder) {
            this.checker = new AggregatedStatsMergePendingChecker(builder.build());
        }

        public static Fixture fromBucketStats(long bucketsPending) {
            return new Fixture(new ContentClusterStatsBuilder()
                    .add(1, "default", 5, bucketsPending));
        }

        public static Fixture fromInvalidBucketStats() {
            return new Fixture(new ContentClusterStatsBuilder()
                    .add(1, "default"));
        }

        public boolean hasMergesPending(String bucketSpace, int contentNodeIndex) {
            return checker.hasMergesPending(bucketSpace, contentNodeIndex);
        }

    }

    @Test
    public void unknown_content_node_has_no_merges_pending() {
        Fixture f = Fixture.fromBucketStats(1);
        assertFalse(f.hasMergesPending("default", 2));
    }

    @Test
    public void unknown_bucket_space_has_no_merges_pending() {
        Fixture f = Fixture.fromBucketStats(1);
        assertFalse(f.hasMergesPending("global", 1));
    }

    @Test
    public void valid_bucket_space_stats_can_have_no_merges_pending() {
        Fixture f = Fixture.fromBucketStats(0);
        assertFalse(f.hasMergesPending("default", 1));
    }

    @Test
    public void valid_bucket_space_stats_can_have_merges_pending() {
        Fixture f = Fixture.fromBucketStats(1);
        assertTrue(f.hasMergesPending("default", 1));
    }

    @Test
    public void invalid_bucket_space_stats_has_merges_pending() {
        Fixture f = Fixture.fromInvalidBucketStats();
        assertTrue(f.hasMergesPending("default", 1));
    }

}
