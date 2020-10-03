package com.yahoo.vespa.curator.stats;// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;

/**
 * @author hakon
 */
public class LatencyStoreTest {
    private final ManualClock clock = new ManualClock();
    private final LatencyStore store = new LatencyStore(clock);

    @Test
    public void verifyDefaultAndEmpty() {
        assertGetLatencyMetrics(0, 0.000f, 0f);
        assertGetAndResetLatencyMetrics(0, 0.000f, 0f);
        assertGetLatencyMetrics(0, 0.000f, 0f);
    }

    @Test
    public void commonCase() {
        store.reportLatency(Duration.ofMillis(2));
        store.reportLatency(Duration.ofMillis(6));
        clock.advance(Duration.ofMillis(2));
        assertGetLatencyMetrics(2, 0.004f, 4f);
        clock.advance(Duration.ofMillis(14));
        assertGetAndResetLatencyMetrics(2, 0.004f, 0.5f);
        assertGetLatencyMetrics(0, 0.000f, 0f);
    }

    private void assertGetLatencyMetrics(int count, float average, float load) {
        LatencyMetrics latencyMetrics = store.getLatencyMetrics();
        assertEquals(count, latencyMetrics.count());
        assertDoubleEquals(average, latencyMetrics.averageInSeconds());
        assertDoubleEquals(load, latencyMetrics.load());
    }

    private void assertGetAndResetLatencyMetrics(int count, float average, float load) {
        LatencyMetrics latencyMetrics = store.getAndResetLatencyMetrics();
        assertEquals(count, latencyMetrics.count());
        assertDoubleEquals(average, latencyMetrics.averageInSeconds());
        assertDoubleEquals(load, latencyMetrics.load());
    }

    private static void assertDoubleEquals(float expected, float actual) {
        assertEquals(expected, actual, 1e-6);
    }
}