// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers.test;

import ai.vespa.metrics.ContainerMetrics;
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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A benchmark and multithread stress test of rate limiting.
 * The purpose of this is to simulate the environment the rate limiter will work under in production
 * and verify that it manages to keep rates more or less within set bounds and does not lead to excessive contention.
 *
 * @author bratseth
 */
public class RateLimitingBenchmark {

    private final int clientCount = 10;
    private final int threadCount = 250;
    private final int epochs = 100; // the number of times the sequence of load types are repeated
    private final int totalQueriesPerThread = 4 * 1000 * 10;

    // This number produces a theoretical max request rate of 1000/5*threadCount = 50 k rps
    // which in practice on my machine is about 40 k rps.
    // With the number set to 0 my machine does about 150 k rps.
    // This means that peaks (when it is zero) are roughly 3x base.
    private final int sleepMsBetweenRequests = 5;
    private final int peakDurationMs = 1000;
    private final int timeBetweenPeaksMs = 2000;

    private final Chain<Searcher> chain;
    private final MetricReceiver metric;
    private Bucket metricSnapshot;

    private final Map<String, RequestCounts> requestCounters = new HashMap<>();

    public RateLimitingBenchmark() {
        RateLimitingConfig.Builder rateLimitingConfig = new RateLimitingConfig.Builder();
        /* Defaults:
        rateLimitingConfig.maxAvailableCapacity(10000);
        rateLimitingConfig.capacityIncrement(1000);
        rateLimitingConfig.recheckForCapacityProbability(0.001);
        */

        rateLimitingConfig.maxAvailableCapacity(10000);
        rateLimitingConfig.capacityIncrement(1000);
        rateLimitingConfig.recheckForCapacityProbability(0.001);

        ClusterInfoConfig.Builder clusterInfoConfig = new ClusterInfoConfig.Builder();
        clusterInfoConfig.clusterId("testCluster");
        clusterInfoConfig.nodeCount(1);

        this.metric = new MetricReceiver.MockReceiver();

        chain = new Chain<>("test", new RateLimitingSearcher(new RateLimitingConfig(rateLimitingConfig),
                                    new ClusterInfoConfig(clusterInfoConfig), metric));

        for (int i = 0; i < clientCount ; i++)
            requestCounters.put(toClientId(i), new RequestCounts());
    }

    public void run() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        runWorkers();
        long totalTime = Math.max(1, System.currentTimeMillis() - startTime);

        metricSnapshot = metric.getSnapshot();
        double totalAttemptedRate = 0;
        for (int i=0; i < clientCount; i++) {
            double attemptedRate = requestCounters.get(toClientId(i)).attempted.get() * 1000d / totalTime;
            double allowedRate = requestCounters.get(toClientId(i)).allowed.get() * 1000d / totalTime;
            System.out.println(String.format(Locale.ENGLISH,
                                             "Client %1$2d:  Attempted rate: %2$10.2f.  Target allowed rate: %3$10.2f.  Allowed rate: %4$10.2f.  Rejected requests: %5$8d",
                                             i, attemptedRate, Math.pow(4, i), allowedRate, rejectedRequests(i)));
            totalAttemptedRate += attemptedRate;
        }
        System.out.println(String.format(Locale.ENGLISH, "\nTotal attempted rate: %1$10.2f seconds", totalAttemptedRate));
        System.out.println(String.format(Locale.ENGLISH, "\nTotal time: %1$8.2f seconds", totalTime/1000.0));
    }

    private void runWorkers() {
        try {
            long startTime = System.currentTimeMillis();

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++)
                threads[i] = new Thread(new Worker(startTime));

            for (int i = 0; i < threadCount; i++)
                threads[i].start();

            for (int i = 0; i < threadCount; i++)
                threads[i].join();
        }
        catch (Exception e) { // not production code
            throw new RuntimeException(e);
        }
    }

    private int rejectedRequests(int id) {
        Point context = metric.pointBuilder().set("id", toClientId(id)).build();
        UntypedMetric rejectedRequestsMetric = metricSnapshot.getMapForMetric(ContainerMetrics.REQUESTS_OVER_QUOTA.baseName()).get(context);
        if (rejectedRequestsMetric == null) return 0;
        return (int)rejectedRequestsMetric.getCount();
    }

    private class Worker implements Runnable {

        private final int sequences = 5;
        private final long startTime;

        public Worker(long startTime) {
            this.startTime = startTime;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < epochs; i++) {
                    issueRequests(this::pickClientFairly);
                    issueRequests(this::pickClientSkewedToLowerNumbers);
                    issueRequests(this::pickClientSkewedToHigherNumbers);
                    issueRequests(this::pickClientFairly);
                    issueRequests(this::pickClientSkewedToHigherNumbers);
                }
            }
            catch (InterruptedException e) {
                // just end
            }
        }

        private void issueRequests(Supplier<Integer> clientNumberSupplier) throws InterruptedException {
            for (int i = 0; i< totalQueriesPerThread/(epochs * sequences); i++) {
                int clientNumber = clientNumberSupplier.get();
                requestCounters.get(toClientId(clientNumber)).addRequest(executeWasAllowed(chain, clientNumber));
                if ( ! isInPeak())
                    Thread.sleep(sleepMsBetweenRequests);
            }
        }

        private boolean isInPeak() {
            long timeSinceStart = System.currentTimeMillis() - startTime;
            return timeSinceStart % timeBetweenPeaksMs < peakDurationMs; // a peak is at every start of every timeBetweenPeaks interval
        }

        protected int pickClientFairly() {
            return ThreadLocalRandom.current().nextInt(clientCount);
        }

        protected int pickClientSkewedToLowerNumbers() {
            int nr = (int)Math.floor((Math.pow(ThreadLocalRandom.current().nextDouble(), 3) * clientCount));
            if (nr > clientCount-1) return clientCount-1;
            return nr;
        }

        protected int pickClientSkewedToHigherNumbers() {
            int nr = (int)Math.floor( ( 1- Math.pow(ThreadLocalRandom.current().nextDouble(), 3)) * clientCount);
            if (nr > clientCount-1) return clientCount-1;
            return nr;
        }

    }

    private String toClientId(int n) {
        return "id" + n;
    }

    private boolean executeWasAllowed(Chain<Searcher> chain, int id) {
        Query query = new Query();
        query.properties().set("rate.id", toClientId(id));
        query.properties().set("rate.cost", 1);
        query.properties().set("rate.quota", Math.pow(4, id));
        query.properties().set("rate.idDimension", "id");
        Result result = new Execution(chain, Execution.Context.createContextStub()).search(query);
        if (result.hits().getError() != null && result.hits().getError().getCode() == 429)
            return false;
        else
            return true;
    }


    public static void main(String[] args) throws InterruptedException {
        new RateLimitingBenchmark().run();
    }

    private static class RequestCounts {

        private AtomicInteger attempted = new AtomicInteger(0);
        private AtomicInteger allowed = new AtomicInteger(0);

        public void addRequest(boolean wasAllowed) {
            attempted.incrementAndGet();
            if (wasAllowed) allowed.incrementAndGet();
        }

    }

}
