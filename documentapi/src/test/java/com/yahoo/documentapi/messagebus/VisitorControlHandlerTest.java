// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus;

import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.vdslib.VisitorStatistics;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VisitorControlHandlerTest {

    @Test
    public void has_visited_any_buckets_is_false_if_no_bucket_stats_recorded() {
        VisitorControlHandler handler = new VisitorControlHandler();
        assertFalse(handler.hasVisitedAnyBuckets());
    }

    @Test
    public void has_visited_any_buckets_is_false_if_zero_buckets_visited() {
        VisitorControlHandler handler = new VisitorControlHandler();
        VisitorStatistics stats = new VisitorStatistics();
        stats.setBucketsVisited(0);
        handler.onVisitorStatistics(stats);

        assertFalse(handler.hasVisitedAnyBuckets());
    }

    @Test
    public void has_visited_any_buckets_is_true_if_more_than_zero_buckets_visited() {
        VisitorControlHandler handler = new VisitorControlHandler();
        VisitorStatistics stats = new VisitorStatistics();
        stats.setBucketsVisited(1);
        handler.onVisitorStatistics(stats);

        assertTrue(handler.hasVisitedAnyBuckets());
    }

}
