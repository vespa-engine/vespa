// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.yahoo.metrics.ManagerConfig;

/**
 * Functional tests for gauges.
 *
 * @author steinar
 */
public class GaugeTest {

    MetricReceiver receiver;

    @BeforeEach
    public void setUp() throws Exception {
        receiver = new MetricReceiver.MockReceiver();
    }

    @AfterEach
    public void tearDown() throws Exception {
        receiver = null;
    }

    @Test
    final void testSampleDouble() throws InterruptedException {
        final String metricName = "unitTestGauge";
        Gauge g = receiver.declareGauge(metricName);
        g.sample(1.0d);
        Bucket b = receiver.getSnapshot();
        final Map<String, List<Entry<Point, UntypedMetric>>> valuesByMetricName = b.getValuesByMetricName();
        assertEquals(1, valuesByMetricName.size());
        List<Entry<Point, UntypedMetric>> x = valuesByMetricName.get(metricName);
        assertEquals(1, x.size());
        assertEquals(Point.emptyPoint(), x.get(0).getKey());
        assertEquals(1L, x.get(0).getValue().getCount());
        assertEquals(1.0d, x.get(0).getValue().getLast(), 0.0d);
    }

    @Test
    final void testSampleDoublePoint() throws InterruptedException {
        final String metricName = "unitTestGauge";
        Point p = receiver.pointBuilder().set("x", 2L).set("y", 3.0d).set("z", "5").build();
        Gauge g = receiver.declareGauge(metricName, p);
        g.sample(Math.E, g.builder().set("x", 7).set("_y", 11.0d).set("Z", "13").build());
        Bucket b = receiver.getSnapshot();
        final Map<String, List<Entry<Point, UntypedMetric>>> valuesByMetricName = b.getValuesByMetricName();
        assertEquals(1, valuesByMetricName.size());
        List<Entry<Point, UntypedMetric>> x = valuesByMetricName.get(metricName);
        assertEquals(1, x.size());
        Point actual = x.get(0).getKey();
        assertEquals(5, actual.dimensionality());
        List<String> dimensions = actual.dimensions();
        List<Value> location = actual.location();
        assertEquals(dimensions.size(), location.size());
        Iterator<String> i0 = dimensions.iterator();
        Iterator<Value> i1 = location.iterator();
        Map<String, Value> asMap = new HashMap<>();
        while (i0.hasNext() && i1.hasNext()) {
            asMap.put(i0.next(), i1.next());
        }
        assertEquals(Value.of(7), asMap.get("x"));
        assertEquals(Value.of(3.0d), asMap.get("y"));
        assertEquals(Value.of("5"), asMap.get("z"));
        assertEquals(Value.of(11.0d), asMap.get("_y"));
        assertEquals(Value.of("13"), asMap.get("Z"));
        assertEquals(Math.E, x.get(0).getValue().getLast(), 1e-15);
    }

}
