// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.benchmarks;

import java.util.Map;

/**
 * Various metrics stored during query execution
 *
 * @author bjorncs
 */
public class ResultMetrics {

    private static final int MAX_LATENCY = 100; // ms
    private static final int RESOLUTION = 25; // sample points per ms
    private static final int SLOTS = MAX_LATENCY * RESOLUTION;

    private long totalQueries = 0;
    private long totalHits = 0;
    private double maxLatency = Double.MIN_VALUE;
    private double minLatency = Double.MAX_VALUE;
    private final long[] latencyHistogram = new long[SLOTS];

    public void registerResult(long hits, double latencyMilliseconds) {
        if (latencyMilliseconds > maxLatency) {
            maxLatency = latencyMilliseconds;
        }
        if (latencyMilliseconds < minLatency) {
            minLatency = latencyMilliseconds;
        }
        totalHits += hits;
        ++totalQueries;
        int latencySlot = (int) Math.round(latencyMilliseconds * RESOLUTION);
        // Note: extreme latency values are ignored in the histogram for simplicity
        if (latencySlot < SLOTS) {
            ++latencyHistogram[latencySlot];
        }
    }

    public void combine(ResultMetrics other) {
        totalQueries += other.totalQueries;
        minLatency = Math.min(minLatency, other.minLatency);
        maxLatency = Math.max(maxLatency, other.maxLatency);
        totalHits += other.totalHits;
        for (int i = 0; i < SLOTS; i++) {
            latencyHistogram[i] += other.latencyHistogram[i];
        }
    }

    public void writeMetrics(Map<String, Object> metricMap, long timeSearch) {
        double qps = timeSearch == 0 ? 0 : (1000d * totalQueries / timeSearch);
        metricMap.put("QPS", qps);
        metricMap.put("Time search", timeSearch);
        metricMap.put("Total hits", totalHits);
        metricMap.put("Total queries", totalQueries);
        metricMap.put("Max latency", latencyToString(maxLatency));
        metricMap.put("Min latency", latencyToString(minLatency));
        metricMap.put("99.9 percentile", latencyToString(percentile(0.999)));
        metricMap.put("99 percentile", latencyToString(percentile(0.99)));
        metricMap.put("90 percentile", latencyToString(percentile(0.90)));
        metricMap.put("75 percentile", latencyToString(percentile(0.75)));
        metricMap.put("50 percentile", latencyToString(percentile(0.50)));
    }

    private double percentile(double percentile) {
        long targetCount = Math.round(totalQueries * percentile);
        long currentCount = 0;
        int index = 0;
        while (currentCount < targetCount && index < SLOTS) {
            currentCount += latencyHistogram[index];
            ++index;
        }
        if (index == SLOTS) {
            return maxLatency;
        }
        return toLatency(currentCount == targetCount ? index + 1 : index);
    }

    private static String latencyToString(double averageLatency) {
        return String.format("%.2fms", averageLatency);
    }

    private static double toLatency(int index) {
        return (index + 0.5) / (double) RESOLUTION;
    }
}
