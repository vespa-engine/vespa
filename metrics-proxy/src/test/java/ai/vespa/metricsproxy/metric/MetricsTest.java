// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric;

import ai.vespa.metricsproxy.metric.model.StatusCode;
import ai.vespa.metricsproxy.service.DummyService;
import ai.vespa.metricsproxy.service.VespaService;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Unknowm
 */
public class MetricsTest {
    private static final double EPSILON = 0.00000000001;
    @Test
    public void testIterator() {
        Metrics m = new Metrics();
        long now = System.currentTimeMillis() / 1000;
        m.add(new Metric(toMetricId("a"), 1, now));
        m.add(new Metric(toMetricId("b"), 2.5, now));

        //should expire after 0 seconds
        m.add(new Metric(toMetricId("c"), 2, now));

        Map<String, Number> map = new HashMap<>();

        for (Metric metric: m.list()) {
            String k = metric.getName().id;

            assertFalse(map.containsKey(k));
            map.put(k, metric.getValue());
        }

        assertEquals(1, map.get("a").intValue());
        assertEquals(2.5, map.get("b").doubleValue(), EPSILON);
    }

    @Test
    public void testBasicMetric() {
        Metrics m = new Metrics();
        m.add(new Metric(toMetricId("count"), 1, System.currentTimeMillis() / 1000));
        assertEquals(1, m.list().size());
        assertEquals(toMetricId("count"), m.list().get(0).getName());
    }

    @Test
    public void testHealthMetric() {
        HealthMetric m = HealthMetric.get(null, null);
        assertFalse(m.isOk());
        m = HealthMetric.get("up", "test message");
        assertTrue(m.isOk());
        assertEquals("test message", m.getMessage());
        m = HealthMetric.get("ok", "test message");
        assertTrue(m.isOk());
        assertEquals("test message", m.getMessage());

        m = HealthMetric.get("bad", "test message");
        assertFalse(m.isOk());
        assertEquals(StatusCode.UNKNOWN, m.getStatus());
    }

    @Test
    public void testMetricFormatter() {
        MetricsFormatter formatter = new MetricsFormatter(false, false);
        VespaService service = new DummyService(0, "config.id");
        String data = formatter.format(service, "key", 1);
        assertEquals("'config.id'.key=1", data);

        formatter = new MetricsFormatter(true, false);
        data = formatter.format(service, "key", 1);
        assertEquals("dummy.'config.id'.key=1", data);


        formatter = new MetricsFormatter(true, true);
        data = formatter.format(service, "key", 1);
        assertEquals("dummy.config.'id'.key=1", data);

        formatter = new MetricsFormatter(false, true);
        data = formatter.format(service, "key", 1);
        assertEquals("config.'id'.key=1", data);
    }

    @Test
    public void testTimeAdjustment() {
        assertEquals(0L, Metric.adjustTime(0L, 0L));
        assertEquals(59L, Metric.adjustTime(59L, 59L));
        assertEquals(60L, Metric.adjustTime(60L, 60L));
        assertEquals(60L, Metric.adjustTime(59L, 60L));
        assertEquals(60L, Metric.adjustTime(60L, 59L));
        assertEquals(59L, Metric.adjustTime(59L, 61L));
    }

}
