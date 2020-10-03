// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Stores the latency of e.g. acquiring the lock.
 *
 * @author hakon
 */
public class LatencyStore {

    private final AtomicDurationSum latencySum = new AtomicDurationSum();
    private final Clock clock;
    private volatile Instant startOfPeriod;

    LatencyStore() { this(Clock.systemUTC()); }

    LatencyStore(Clock clock) {
        this.clock = clock;
        startOfPeriod = clock.instant();
    }

    void reportLatency(Duration latency) {
        latencySum.add(latency);
    }

    public LatencyMetrics getLatencyMetrics() {
        return makeMetricsForPeriod(latencySum.get(), startOfPeriod, clock.instant());
    }

    public LatencyMetrics getAndResetLatencyMetrics() {
        Instant newStartOfPeriod = clock.instant();
        DurationSum latencySumOfPeriod = latencySum.getAndReset();
        LatencyMetrics latencyMetrics = makeMetricsForPeriod(latencySumOfPeriod, startOfPeriod, newStartOfPeriod);
        startOfPeriod = newStartOfPeriod;
        return latencyMetrics;
    }

    private static LatencyMetrics makeMetricsForPeriod(DurationSum latencySum, Instant start, Instant end) {
        long millisPeriod = Duration.between(start, end).toMillis();
        long normalizedMillisPeriod = Math.max(1L, millisPeriod);
        double load = Math.round(latencySum.duration().toMillis() * 1000.0 / normalizedMillisPeriod) / 1000.0;
        return new LatencyMetrics(latencySum, (float) load);
    }

    @Override
    public String toString() {
        return "LatencyStore{" +
                "latencySum=" + latencySum +
                ", startOfPeriod=" + startOfPeriod +
                '}';
    }
}
