// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers.test;

import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.component.chain.Chain;
import com.yahoo.metrics.simple.Bucket;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.metrics.simple.Point;
import com.yahoo.metrics.simple.UntypedMetric;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.RateLimitingConfig;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchers.RateLimitingSearcher;
import com.yahoo.yolean.chain.After;
import org.junit.jupiter.api.Test;
import com.yahoo.test.ManualClock;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimitingSearcher
 *
 * @author bratseth
 */
public class RateLimitingSearcherTestCase {

    @Test
    void testRateLimiting() {
        RateLimitingConfig.Builder rateLimitingConfig = new RateLimitingConfig.Builder();
        rateLimitingConfig.maxAvailableCapacity(4);
        rateLimitingConfig.capacityIncrement(2);
        rateLimitingConfig.recheckForCapacityProbability(1.0);

        ClusterInfoConfig.Builder clusterInfoConfig = new ClusterInfoConfig.Builder();
        clusterInfoConfig.clusterId("testCluster");
        clusterInfoConfig.nodeCount(4);

        ManualClock clock = new ManualClock();
        MetricReceiver.MockReceiver metric = new MetricReceiver.MockReceiver();

        Chain<Searcher> chain = new Chain<>("test", new RateLimitingSearcher(new RateLimitingConfig(rateLimitingConfig),
                        new ClusterInfoConfig(clusterInfoConfig),
                        metric, clock),
                new CostSettingSearcher());
        assertEquals(2, tryRequests(chain, "id1"), "'rate' request are available initially");
        assertTrue(executeWasAllowed(chain, "id1", true), "However, don't reject if we dryRun");
        clock.advance(Duration.ofMillis(1500)); // causes 2 new requests to become available
        assertEquals(2, tryRequests(chain, "id1"), "'rate' new requests became available");

        assertEquals(2, tryRequests(chain, "id2"), "Another id");

        clock.advance(Duration.ofMillis(1000000));
        assertEquals(4, tryRequests(chain, "id2"), "'maxAvailableCapacity' request became available");

        assertFalse(executeWasAllowed(chain, "id3", 0), "If quota is set to 0, all requests are rejected, even initially");

        clock.advance(Duration.ofMillis(1000000));
        assertTrue(executeWasAllowed(chain, "id1", 8, 8, false),
                "A single query which costs more than capacity is allowed as cost is calculated after allowing it");
        assertFalse(executeWasAllowed(chain, "id1"), "capacity is -4: disallowing");
        clock.advance(Duration.ofMillis(1000));
        assertFalse(executeWasAllowed(chain, "id1"), "capacity is -2: disallowing");
        clock.advance(Duration.ofMillis(1000));
        assertFalse(executeWasAllowed(chain, "id1"), "capacity is 0: disallowing");
        clock.advance(Duration.ofMillis(1000));
        assertTrue(executeWasAllowed(chain, "id1"));

        // check metrics
        Map<Point, UntypedMetric> map = metric.getSnapshot().getMapForMetric("requestsOverQuota");
        assertEquals(requestsToTry - 2 + 1 + requestsToTry - 2 + 3, map.get(metric.point("id", "id1")).getCount());
        assertEquals(requestsToTry - 2 + requestsToTry - 4,         map.get(metric.point("id", "id2")).getCount());
    }

    private int requestsToTry = 50;

    /**
     * Try many requests and return how many was allowed.
     * This is to avoid testing the exact pattern of request/deny which does not matter
     * and is determined by floating point arithmetic details when capacity is close to zero.
     */
    private int tryRequests(Chain<Searcher> chain, String id) {
        int allowedCount = 0;
        for (int i = 0; i < requestsToTry; i++) {
            if (executeWasAllowed(chain, id))
                allowedCount++;
        }
        return allowedCount;
    }

    private boolean executeWasAllowed(Chain<Searcher> chain, String id) {
        return executeWasAllowed(chain, id, 8);  // allowed 8 requests per second over 4 nodes -> 2 per node
    }

    private boolean executeWasAllowed(Chain<Searcher> chain, String id, boolean dryRun) {
        return executeWasAllowed(chain, id, 8, 1, dryRun);
    }

    private boolean executeWasAllowed(Chain<Searcher> chain, String id, int quota) {
        return executeWasAllowed(chain, id, quota, 1, false);
    }

    private boolean executeWasAllowed(Chain<Searcher> chain, String id, double quota, double cost, boolean dryRun) {
        Query query = new Query();
        query.properties().set("rate.id", id);
        query.properties().set("cost", cost); // converted to rate.cost by a searcher executing after rate limiting
        query.properties().set("rate.quota", quota);
        query.properties().set("rate.idDimension", "id");
        query.properties().set("rate.dryRun", dryRun);
        Result result = new Execution(chain, Execution.Context.createContextStub()).search(query);
        if (result.hits().getError() != null && result.hits().getError().getCode() == 429)
            return false;
        else
            return true;
    }

    /** The purpose of this test is simply to verify that cost is picked up after executing the query */
    @After(RateLimitingSearcher.RATE_LIMITING)
    private static class CostSettingSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Result result = execution.search(query);
            query.properties().set("rate.cost", query.properties().get("cost"));
            return result;
        }

    }

}
