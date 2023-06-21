// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import ai.vespa.metrics.ContainerMetrics;
import com.yahoo.component.annotation.Inject;
import com.yahoo.cloud.config.ClusterInfoConfig;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.metrics.simple.Counter;
import com.yahoo.metrics.simple.Point;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.RateLimitingConfig;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.yolean.chain.Provides;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A simple rate limiter.
 * <p>
 * This takes these query parameter arguments:
 * <ul>
 *     <li>rate.id - (String) the id of the client from rate limiting perspective
 *     <li>rate.cost - (Double) the cost Double of this query. This is read after executing the query and hence can be set
 *     by downstream searchers inspecting the result to allow differencing the cost of various queries. Default is 1.
 *     <li>rate.quota - (Double) the cost per second a particular id is allowed to consume in this system.
 *     <li>rate.idDimension - (String) the name of the rate-id dimension used when logging metrics.
 *                                 If this is not specified, the metric will be logged without dimensions.
 *     <li>rate.dryRun - (Boolean) emit metrics on rejected requests but don't actually reject them
 * </ul>
 * <p>
 * Whenever quota is exceeded for an id this searcher will reject queries from that id by
 * returning a result containing a status 429 error.
 * <p>
 * If rate.id or rate.quota is not set in Query.properties this searcher will do nothing.
 * <p>
 * Metrics: This will emit the count metric requestsOverQuota with the dimension [rate.idDimension=rate.id]
 * counting rejected requests.
 * <p>
 * Ordering: This searcher Provides rateLimiting
 *
 * @author bratseth
 */
@Provides(RateLimitingSearcher.RATE_LIMITING)
public class RateLimitingSearcher extends Searcher {

    /** Constant containing the name this Provides - "rateLimiting", for ordering constraints */
    public static final String RATE_LIMITING = "rateLimiting";

    public static final CompoundName idKey = CompoundName.from("rate.id");
    public static final CompoundName costKey = CompoundName.from("rate.cost");
    public static final CompoundName quotaKey = CompoundName.from("rate.quota");
    public static final CompoundName idDimensionKey = CompoundName.from("rate.idDimension");
    public static final CompoundName dryRunKey = CompoundName.from("rate.dryRun");

    private static final String requestsOverQuotaMetricName = ContainerMetrics.REQUESTS_OVER_QUOTA.baseName();

    /** Used to divide quota by nodes. Assumption: All nodes get the same share of traffic. */
    private final int nodeCount;

    /** Shared capacity across all threads. Each thread will ask for more capacity from here when they run out. */
    private final AvailableCapacity availableCapacity;

    /** Capacity already allocated to this thread */
    private final ThreadLocal<Map<String, Double>> allocatedCapacity = new ThreadLocal<>();

    /** For emitting metrics */
    private final Counter overQuotaCounter;

    /**
     * How much capacity to allocate to a thread each time it runs out.
     * A higher value means less contention and less accuracy.
     */
    private final double capacityIncrement;

    /** How often to check for new capacity if we have run out */
    private final double recheckForCapacityProbability;

    @Inject
    public RateLimitingSearcher(RateLimitingConfig rateLimitingConfig, ClusterInfoConfig clusterInfoConfig, MetricReceiver metric) {
        this(rateLimitingConfig, clusterInfoConfig, metric, Clock.systemUTC());
    }

    /** For testing - allows injection of a timer to avoid depending on the system clock */
    public RateLimitingSearcher(RateLimitingConfig rateLimitingConfig, ClusterInfoConfig clusterInfoConfig, MetricReceiver metric, Clock clock) {
        this.capacityIncrement = rateLimitingConfig.capacityIncrement();
        this.recheckForCapacityProbability = rateLimitingConfig.recheckForCapacityProbability();
        this.availableCapacity = new AvailableCapacity(rateLimitingConfig.maxAvailableCapacity(), clock);

        this.nodeCount = clusterInfoConfig.nodeCount();

        this.overQuotaCounter = metric.declareCounter(requestsOverQuotaMetricName);
    }

    @Override
    public Result search(Query query, Execution execution) {
        String id = query.properties().getString(idKey);
        Double rate = query.properties().getDouble(quotaKey);
        if (id == null || rate == null) {
            query.trace(false, 6, "Skipping rate limiting check. Need both " + idKey + " and " + quotaKey + " set");
            return execution.search(query);
        }

        rate = rate / nodeCount;

        if (allocatedCapacity.get() == null) // new thread
            allocatedCapacity.set(new HashMap<>());
        if (allocatedCapacity.get().get(id) == null) // new id in this thread
            requestCapacity(id, rate);

        // Check if there is capacity available. Cannot check for exact cost as it may be computed after execution
        // no capacity means we're over rate. Only recheck occasionally to limit synchronization.
        if (getAllocatedCapacity(id) <= 0 && ThreadLocalRandom.current().nextDouble() < recheckForCapacityProbability) {
            requestCapacity(id, rate);
        }

        if (rate==0 || getAllocatedCapacity(id) <= 0) { // we are still over rate: reject
            String idDim = query.properties().getString(idDimensionKey, null);
            if (idDim == null) {
                overQuotaCounter.add(1);
            } else {
                overQuotaCounter.add(1, createContext(idDim, id));
            }
            if ( ! query.properties().getBoolean(dryRunKey, false))
                return new Result(query, new ErrorMessage(429, "Too many requests", "Allowed rate: " + rate + "/s"));
        }

        Result result = execution.search(query);
        addAllocatedCapacity(id, - query.properties().getDouble(costKey, 1.0));

        if (getAllocatedCapacity(id) <= 0) // make sure we ask for more with 100% probability when first running out
            requestCapacity(id, rate);

        return result;
    }

    private Point createContext(String dimensionName, String dimensionValue) {
        return overQuotaCounter.builder().set(dimensionName, dimensionValue).build();
    }

    private double getAllocatedCapacity(String id) {
        Double value = allocatedCapacity.get().get(id);
        if (value == null) return 0;
        return value;
    }

    private void addAllocatedCapacity(String id, double newCapacity) {
        Double capacity = allocatedCapacity.get().get(id);
        if (capacity != null)
            newCapacity += capacity;
        allocatedCapacity.get().put(id, newCapacity);
    }

    private void requestCapacity(String id, double rate) {
        double minimumRequested = Math.max(0, -getAllocatedCapacity(id)); // If we are below, make sure we reach 0
        double preferredRequested = Math.max(capacityIncrement, -getAllocatedCapacity(id));
        addAllocatedCapacity(id, availableCapacity.request(id, minimumRequested, preferredRequested, rate));
    }

    /**
     * This keeps track of the current "capacity" (total cost) available to each client (rate id)
     * across all threads. Capacity is supplied at the rate per second given by the clients quota.
     * When all the capacity is spent, no further capacity will be handed out, leading to request rejection.
     * Capacity has a max value it will never exceed to avoid clients saving capacity for future overspending.
     */
    private static class AvailableCapacity {

        private final double maxAvailableCapacity;
        private final Clock clock;

        private final Map<String, CapacityAllocation> available = new HashMap<>();

        public AvailableCapacity(double maxAvailableCapacity, Clock clock) {
            this.maxAvailableCapacity = maxAvailableCapacity;
            this.clock = clock;
        }

        /** Returns an amount of capacity between 0 and the requested amount based on availability for this id */
        public synchronized double request(String id, double minimumRequested, double preferredRequested, double rate) {
            CapacityAllocation allocation = available.get(id);
            if (allocation == null) {
                allocation = new CapacityAllocation(rate, clock);
                available.put(id, allocation);
            }
            return allocation.request(minimumRequested, preferredRequested, rate, maxAvailableCapacity);
        }

    }

    private static class CapacityAllocation {

        private double capacity;
        private final Clock clock;
        private long lastAllocatedTime;

        public CapacityAllocation(double initialCapacity, Clock clock) {
            this.capacity = initialCapacity;
            this.clock = clock;
            lastAllocatedTime = clock.millis();
        }

        public double request(double minimumRequested, double preferredRequested, double rate, double maxAvailableCapacity) {
            if ( preferredRequested > capacity) { // attempt to allocate more
                // rate is per second so we get rate/1000 per millisecond
                long currentTime = clock.millis();
                capacity += Math.min(maxAvailableCapacity, rate/1000d * (Math.max(0, currentTime - lastAllocatedTime)));
                lastAllocatedTime = currentTime;
            }
            double grantedCapacity = Math.min(capacity/10, preferredRequested); // /10 to avoid stealing all capacity when low
            if (grantedCapacity < minimumRequested)
                grantedCapacity = Math.min(minimumRequested, capacity);
            capacity = capacity - grantedCapacity;
            return grantedCapacity;
        }

    }

}
