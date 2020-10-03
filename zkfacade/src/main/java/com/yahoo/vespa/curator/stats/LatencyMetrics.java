// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

/**
 * Metrics on the latency of execution of some piece of code, e.g. the acquiring of a lock.
 *
 * @author hakon
 */
public class LatencyMetrics {
    private final DurationSum cumulativeLatency;
    private final float load;

    public LatencyMetrics(DurationSum cumulativeLatency, float load) {
        this.cumulativeLatency = cumulativeLatency;
        this.load = load;
    }

    /**
     * The total time spent by all threads accumulating latency in an implicit time period,
     * e.g. a metric snapshot window, divided by the duration of the time period.
     */
    public float load() { return load; }

    /** Returns the average latency in seconds with milliseconds resolution, or 0.0 by default. */
    public float averageInSeconds() {
        return cumulativeLatency.averageDuration().map(average -> average.toMillis() / 1000f).orElse(0f);
    }

    /** The number of latency-producing events. */
    public int count() { return cumulativeLatency.count(); }

    @Override
    public String toString() {
        return "LatencyMetrics{" +
                "cumulativeLatency=" + cumulativeLatency +
                ", load=" + load +
                '}';
    }
}
