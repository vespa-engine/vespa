// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import static java.lang.Math.round;

/**
 * Metrics on the <em>time interval</em> associated with e.g. the acquiring of a lock.
 *
 * <p>In the language of {@link LatencyStats}, these metrics relate to time intervals associated
 * with e.g. the acquiring of a lock, collected over time period.</p>
 *
 * @see LatencyStats
 * @author hakon
 */
// @Immutable
public class LatencyMetrics {

    private final Duration latency;
    private final Duration maxLatency;
    private final Duration maxActiveLatency;
    private final double startHz;
    private final double endHz;
    private final Map<String, Double> loadByThread;
    private final double load;
    private final int maxLoad;
    private final int currentLoad;

    public LatencyMetrics(Duration latency, Duration maxLatency, Duration maxActiveLatency,
                          double startHz, double endHz, Map<String, Double> loadByThread,
                          double load, int maxLoad, int currentLoad) {
        this.latency = latency;
        this.maxLatency = maxLatency;
        this.maxActiveLatency = maxActiveLatency;
        this.startHz = startHz;
        this.endHz = endHz;
        this.loadByThread = new TreeMap<>(loadByThread);
        this.load = load;
        this.maxLoad = maxLoad;
        this.currentLoad = currentLoad;
    }

    /** Returns the average latency of all intervals that ended in the period. */
    public double latencySeconds() { return secondsWithMillis(latency); }

    /** Returns the maximum latency of any interval that ended in the period. */
    public double maxLatencySeconds() { return secondsWithMillis(maxLatency); }

    /** Return the maximum latency of any interval that ended in the period, or is still active. */
    public double maxActiveLatencySeconds() { return secondsWithMillis(maxActiveLatency); }

    /** Returns the average number of intervals that started in the period per second. */
    public double startHz() { return roundTo3DecimalPlaces(startHz); }

    /** Returns the average number of intervals that ended in the period per second. */
    public double endHz() { return roundTo3DecimalPlaces(endHz); }

    /** Returns the average load of the implied time periond, for each thread with non-zero load, with 3 decimal places precision. */
    public Map<String, Double> loadByThread() {
        Map<String, Double> result = new TreeMap<>();
        loadByThread.forEach((name, load) -> result.put(name, roundTo3DecimalPlaces(load)));
        return Collections.unmodifiableMap(result);
    }

    /** The average load of the implied time period, with 3 decimal places precision. */
    public double load() { return roundTo3DecimalPlaces(load); }

    /** Returns the maximum number of concurrently active intervals in the period. */
    public int maxLoad() { return maxLoad; }

    /** Returns the number of active intervals right now. */
    public int currentLoad() { return currentLoad; }

    @Override
    public String toString() {
        return "LatencyMetrics{" +
                "averageLatency=" + latency +
                ", maxLatency=" + maxLatency +
                ", maxActiveLatency=" + maxActiveLatency +
                ", numIntervalsStarted=" + startHz +
                ", numIntervalsEnded=" + endHz +
                ", load=" + load +
                ", maxLoad=" + maxLoad +
                ", currentLoad=" + currentLoad +
                '}';
    }

    private double secondsWithMillis(Duration duration) { return duration.toMillis() / 1000.0; }
    private double roundTo3DecimalPlaces(double value) { return round(value * 1000) / 1000.0; }
}
