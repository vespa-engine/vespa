// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.yahoo.jdisc.Metric;
import com.yahoo.test.ManualClock;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author ollivir
 */
public class GarbageCollectionMetricsTest {
    @Test
    void gc_metrics_are_collected_in_a_sliding_window() {
        ManualClock clock = new ManualClock();
        Metric metric = mock(Metric.class);
        int garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans().size();

        Duration interval = GarbageCollectionMetrics.REPORTING_INTERVAL;
        GarbageCollectionMetrics garbageCollectionMetrics = new GarbageCollectionMetrics(clock);
        assertEquals(garbageCollectors, garbageCollectionMetrics.getGcStatistics().keySet().size());

        clock.advance(interval.minus(Duration.ofMillis(10)));
        garbageCollectionMetrics.emitMetrics(metric);
        assertWindowLengths(garbageCollectionMetrics, 2);

        clock.advance(Duration.ofMillis(10));
        garbageCollectionMetrics.emitMetrics(metric);
        assertWindowLengths(garbageCollectionMetrics, 3);

        clock.advance(Duration.ofMillis(10));
        garbageCollectionMetrics.emitMetrics(metric);
        assertWindowLengths(garbageCollectionMetrics, 3);

        clock.advance(interval);
        garbageCollectionMetrics.emitMetrics(metric);
        assertWindowLengths(garbageCollectionMetrics, 2);

        verify(metric, times(garbageCollectors * 4 * 2)).set(anyString(), any(), any());
    }

    private static void assertWindowLengths(GarbageCollectionMetrics gcm, int count) {
        for(LinkedList<GarbageCollectionMetrics.GcStats> window: gcm.getGcStatistics().values()) {
            assertEquals(count, window.size());
        }
    }
}
