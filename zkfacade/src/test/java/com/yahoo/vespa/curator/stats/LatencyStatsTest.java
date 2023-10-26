// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import com.yahoo.vespa.curator.stats.LatencyStats.ActiveInterval;
import org.junit.Test;

import java.util.function.LongSupplier;

import static org.junit.Assert.assertEquals;

public class LatencyStatsTest {
    private final NanoTimeSupplier nanoTimeSupplier = new NanoTimeSupplier();
    private final LatencyStats stats = new LatencyStats(nanoTimeSupplier);

    @Test
    public void defaults() {
        assertNoActivity(stats.getLatencyMetrics());
        assertNoActivity(stats.getLatencyMetricsAndStartNewPeriod());
        assertNoActivity(stats.getLatencyMetricsAndStartNewPeriod());
    }

    @Test
    public void oneInterval() {
        ActiveInterval activeInterval = stats.startNewInterval();

        int micros = 1_234_567;
        nanoTimeSupplier.addMicros(micros);
        activeInterval.close();

        nanoTimeSupplier.addMicros(32_000_000 - micros);
        var latencyMetrics = stats.getLatencyMetricsAndStartNewPeriod();
        // 1.234567 gets truncated to 1.234 (rounding to 1.235 would also be fine)
        assertDoubleEquals(1.234f, latencyMetrics.latencySeconds());
        assertDoubleEquals(1.234f, latencyMetrics.maxLatencySeconds());
        assertDoubleEquals(1.234f, latencyMetrics.maxActiveLatencySeconds());
        // 1 / 32 = 0.03125
        assertDoubleEquals(0.031f, latencyMetrics.startHz());
        assertDoubleEquals(0.031f, latencyMetrics.endHz());
        // 1.234567 / 32 rounded to 0.039 (truncating to 0.038 would also be fine)
        assertDoubleEquals(0.039f, latencyMetrics.load());
        assertEquals(1, latencyMetrics.maxLoad());
        assertEquals(0, latencyMetrics.currentLoad());

        assertNoActivity();
    }

    @Test
    public void manyIntervals() {
        nanoTimeSupplier.addSeconds(1);
        ActiveInterval activeInterval1 = stats.startNewInterval();
        nanoTimeSupplier.addSeconds(1);
        ActiveInterval activeInterval2 = stats.startNewInterval();

        assertLatencyMetrics(
                0.0,  // latency: No intervals have ended
                0.0,  // maxLatency: No intervals have ended
                1.0,  // maxActiveLatency:  First interval has lasted 1s
                1.0,  // startHz:  There have been 2 starts in 2 seconds
                0.0,  // endHz:  There have been 0 endings of intervals
                0.5,  // load:  First second had 0 active intervals, second second had 1.
                2,  // maxLoad:  There are now 2 active intervals
                2);  // currentLoad:  There are now 2 active intervals

        nanoTimeSupplier.addSeconds(1);
        ActiveInterval activeInterval3 = stats.startNewInterval();

        nanoTimeSupplier.addSeconds(1);
        activeInterval1.close();
        activeInterval3.close();

        assertLatencyMetrics(
                2.0,  // latency: 2 intervals ended: 3s and 1s
                3.0,
                3.0,  // maxActiveLatency:  both interval 1 and 2 have 3s latency
                0.75,  // startHz: 3 started in 4s
                0.5,
                1.5,  // load: 1s of each of 0, 1, 2, and 3 active intervals.
                3,
                1);
    }

    @Test
    public void intervalsCrossingPeriods() {
        nanoTimeSupplier.addSeconds(1);
        ActiveInterval activeInterval1 = stats.startNewInterval();
        nanoTimeSupplier.addSeconds(1);

        stats.getLatencyMetricsAndStartNewPeriod();
        assertLatencyMetrics(
                0, 0, 1,  // maxActiveLatency:  One active interval has latency 1s
                0, 0,
                1, 1, 1);  // all loads are 1

        nanoTimeSupplier.addSeconds(1);
        activeInterval1.close();
        assertLatencyMetrics(
                2, 2, 2,
                0, 1,  // startHz, endHz
                1, 1, 0);  // currentLoad just dropped to 0
    }

    private void assertLatencyMetrics(double latencySeconds, double maxLatencySeconds, double maxActiveLatencySeconds,
                                      double startHz, double endHz,
                                      double load, int maxLoad, int currentLoad) {
        var latencyMetrics = stats.getLatencyMetrics();
        assertDoubleEquals(latencySeconds, latencyMetrics.latencySeconds());
        assertDoubleEquals(maxLatencySeconds, latencyMetrics.maxLatencySeconds());
        assertDoubleEquals(maxActiveLatencySeconds, latencyMetrics.maxActiveLatencySeconds());
        assertDoubleEquals(startHz, latencyMetrics.startHz());
        assertDoubleEquals(endHz, latencyMetrics.endHz());
        assertDoubleEquals(load, latencyMetrics.load());
        assertEquals(maxLoad, latencyMetrics.maxLoad());
        assertEquals(currentLoad, latencyMetrics.currentLoad());
    }

    private void assertNoActivity() { assertNoActivity(stats.getLatencyMetricsAndStartNewPeriod()); }

    private void assertNoActivity(LatencyMetrics latencyMetrics) {
        assertDoubleEquals(0.0, latencyMetrics.latencySeconds());
        assertDoubleEquals(0.0, latencyMetrics.maxLatencySeconds());
        assertDoubleEquals(0.0, latencyMetrics.maxActiveLatencySeconds());
        assertDoubleEquals(0.0, latencyMetrics.startHz());
        assertDoubleEquals(0.0, latencyMetrics.endHz());
        assertDoubleEquals(0.0, latencyMetrics.load());
        assertEquals(0, latencyMetrics.maxLoad());
        assertEquals(0, latencyMetrics.currentLoad());
    }

    private void assertDoubleEquals(double expected, double actual) {
        assertEquals(expected, actual, 1e-5);
    }

    private static class NanoTimeSupplier implements LongSupplier {
        // The initial nano time should not matter
        private long nanoTime = 0x678abf4967L;

        public void addSeconds(int seconds) { nanoTime += seconds * 1_000_000_000L; }
        public void addMillis(int millis) { nanoTime += millis * 1_000_000L; }
        public void addMicros(int micros) { nanoTime += micros * 1_000L; }
        public void addNanos(int nanos) { nanoTime += nanos; }

        @Override
        public long getAsLong() { return nanoTime; }
    }
}
