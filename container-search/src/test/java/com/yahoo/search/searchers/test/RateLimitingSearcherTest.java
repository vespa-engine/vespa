// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers.test;

import com.yahoo.metrics.simple.Point;
import com.yahoo.metrics.simple.UntypedMetric;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for RateLimitingSearcher
 *
 * @author bratseth
 */
public class RateLimitingSearcherTest {

    @Test
    void testRateLimiting() {
        var tester = new RateLimitingTester(false);
        assertEquals(2, tester.tryRequests("id1"), "'rate/nodes' request are available initially");
        assertTrue(tester.executeWasAllowed("id1", true), "However, don't reject if we dryRun");
        tester.clock().advance(Duration.ofMillis(1000)); // causes 2 new requests to become available
        assertEquals(2, tester.tryRequests("id1"), "'rate' new requests became available");

        assertEquals(2, tester.tryRequests("id2"), "Another id");

        tester.clock().advance(Duration.ofMillis(1000000));
        assertEquals(4, tester.tryRequests("id2"), "'maxAvailableCapacity' request became available");

        assertFalse(tester.executeWasAllowed("id3", 0), "If quota is set to 0, all requests are rejected, even initially");

        tester.clock().advance(Duration.ofMillis(1000000));
        assertTrue(tester.executeWasAllowed("id1", 8, 8, false),
                "A single query which costs more than capacity is allowed as cost is calculated after allowing it");
        assertFalse(tester.executeWasAllowed("id1"), "capacity is -4: disallowing");
        tester.clock().advance(Duration.ofMillis(1000));
        assertFalse(tester.executeWasAllowed("id1"), "capacity is -2: disallowing");
        tester.clock().advance(Duration.ofMillis(1000));
        assertFalse(tester.executeWasAllowed("id1"), "capacity is 0: disallowing");
        tester.clock().advance(Duration.ofMillis(1000));
        assertTrue(tester.executeWasAllowed("id1"));

        // check metrics
        Map<Point, UntypedMetric> map = tester.metrics().getSnapshot().getMapForMetric("requestsOverQuota");
        assertEquals(tester.requestsToTry - 2 + 1 + tester.requestsToTry - 2 + 3,
                     map.get(tester.metrics().point("id", "id1")).getCount());
        assertEquals(tester.requestsToTry - 2 + tester.requestsToTry - 4,
                     map.get(tester.metrics().point("id", "id2")).getCount());
    }

    @Test
    void testRateLimitingDryRun() {
        var tester = new RateLimitingTester(false);
        for (int i = 0; i < 2; i++)
            assertTrue(tester.executeWasAllowed("id1", true));

        assertFalse(tester.executeWasAllowed("id1", false), "Out of capacity");

        // Should not push us further below quota even they are not rejected in dryRun mode
        for (int i = 0; i < 10; i++)
            assertTrue(tester.executeWasAllowed("id1", true));

        tester.clock().advance(Duration.ofMillis(1000));
        assertEquals(2, tester.tryRequests("id1"), "2 new requests should have become available");
    }

    /** The purpose of this test is simply to verify that cost is picked up after executing the query */
    @Test
    void testLocalRateLimiting() {
        var tester = new RateLimitingTester(true);
        assertEquals(9, tester.tryRequests("id1"), "'rate' request are available initially");
    }

    @Test
    void testRequestsMetric() {
        var tester = new RateLimitingTester(false);
        tester.executeWasAllowed("id1");
        tester.executeWasAllowed("id1");
        assertFalse(tester.executeWasAllowed("id2", 0), "This request is also counted, although rejected");

        Map<Point, UntypedMetric> map = tester.metrics().getSnapshot().getMapForMetric("requests");
        assertEquals(2, map.get(tester.metrics().point("id", "id1")).getCount());
        assertEquals(1, map.get(tester.metrics().point("id", "id2")).getCount());
    }

}
