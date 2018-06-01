package com.yahoo.container.jdisc.metric;

import com.yahoo.jdisc.Metric;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.util.LinkedList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author ollivir
 */
public class GarbageCollectionMetricsTest {
    @Test
    public void gc_metrics_reported_at_intervals() {
        Clock clock = mock(Clock.class);
        Metric metric = mock(Metric.class);
        int base = ManagementFactory.getGarbageCollectorMXBeans().size();

        long iv = GarbageCollectionMetrics.REPORTING_INTERVAL;
        when(clock.millis()).thenReturn(10L);
        GarbageCollectionMetrics garbageCollectionMetrics = new GarbageCollectionMetrics(clock);
        assertThat(garbageCollectionMetrics.getGcStatistics().keySet().size(), is(base));

        when(clock.millis()).thenReturn(iv);
        garbageCollectionMetrics.emitMetrics(metric);
        assertWindowLengths(garbageCollectionMetrics, 2);

        when(clock.millis()).thenReturn(10L + iv);
        garbageCollectionMetrics.emitMetrics(metric);
        assertWindowLengths(garbageCollectionMetrics, 3);

        when(clock.millis()).thenReturn(20L + iv);
        garbageCollectionMetrics.emitMetrics(metric);
        assertWindowLengths(garbageCollectionMetrics, 3);

        when(clock.millis()).thenReturn(20L + iv + iv);
        garbageCollectionMetrics.emitMetrics(metric);
        assertWindowLengths(garbageCollectionMetrics, 2);

        verify(metric, times(base * 4 * 2)).set(anyString(), any(), any());
    }

    private static void assertWindowLengths(GarbageCollectionMetrics gcm, int count) {
        for(LinkedList<GarbageCollectionMetrics.GcStats> window: gcm.getGcStatistics().values()) {
            assertThat(window.size(), is(count));
        }
    }
}
