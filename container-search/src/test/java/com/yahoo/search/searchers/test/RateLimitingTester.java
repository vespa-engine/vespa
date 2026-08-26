// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers.test;

import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.RateLimitingConfig;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchers.RateLimitingSearcher;
import com.yahoo.test.ManualClock;

/**
 * @author bratseth
 */
public class RateLimitingTester {

    public final int requestsToTry = 50;

    private final ManualClock clock;
    private final MetricReceiver.MockReceiver metrics;
    private final Chain<Searcher> chain;

    public RateLimitingTester(boolean localRate) {
        this.clock = new ManualClock();
        this.metrics = new MetricReceiver.MockReceiver();
        this.chain = createChain(localRate, clock, metrics);
    }

    public ManualClock clock() { return clock; }
    public MetricReceiver.MockReceiver metrics() { return metrics; }

    /**
     * Try many requests and return how many was allowed.
     * This is to avoid testing the exact pattern of request/deny which does not matter
     * and is determined by floating point arithmetic details when capacity is close to zero.
     */
    public int tryRequests(String id) {
        int allowedCount = 0;
        for (int i = 0; i < requestsToTry; i++) {
            if (executeWasAllowed(id))
                allowedCount++;
        }
        return allowedCount;
    }

    public boolean executeWasAllowed(String id) {
        return executeWasAllowed(id, 8);  // allowed 8 requests per second over 4 nodes -> 2 per node
    }

    public boolean executeWasAllowed(String id, boolean dryRun) {
        return executeWasAllowed(id, 8, 1, dryRun);
    }

    public boolean executeWasAllowed(String id, int quota) {
        return executeWasAllowed(id, quota, 1, false);
    }

    public boolean executeWasAllowed(String id, double quota, double cost, boolean dryRun) {
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

    private static Chain<Searcher> createChain(boolean localRate, ManualClock clock, MetricReceiver.MockReceiver metrics) {
        RateLimitingConfig.Builder rateLimitingConfig = new RateLimitingConfig.Builder();
        rateLimitingConfig.maxAvailableCapacity(4);
        rateLimitingConfig.capacityIncrement(2);
        rateLimitingConfig.recheckForCapacityProbability(1.0);
        rateLimitingConfig.localRate(localRate);

        ClusterInfoConfig.Builder clusterInfoConfig = new ClusterInfoConfig.Builder();
        clusterInfoConfig.clusterId("testCluster");
        clusterInfoConfig.nodeCount(4);

        return new Chain<>("test", new RateLimitingSearcher(new RateLimitingConfig(rateLimitingConfig),
                                                                new ClusterInfoConfig(clusterInfoConfig),
                                                                metrics,
                                                                clock),
                           new CostSettingSearcher());
    }

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
