// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric;

import ai.vespa.metricsproxy.metric.model.StatusCode;
import ai.vespa.metricsproxy.service.DummyService;
import ai.vespa.metricsproxy.service.VespaService;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Unknowm
 */
public class MetricsTest {

    @Test
    public void testIterator() {
        Metrics m = new Metrics();
        long now = System.currentTimeMillis() / 1000;
        m.add(new Metric("a", 1, now));
        m.add(new Metric("b", 2.5, now));

        //should expire after 0 seconds
        m.add(new Metric("c", 2, now));

        Map<String, Number> map = new HashMap<>();

        for (Metric metric: m.getMetrics()) {
            String k = metric.getName();

            assertThat(map.containsKey(k), is(false));
            map.put(k, metric.getValue());
        }

        assertThat(map.get("a").intValue(), is(1));
        assertThat(map.get("b").doubleValue(), is(2.5));
    }

    @Test
    public void testBasicMetric() {
        Metrics m = new Metrics();
        m.add(new Metric("count", 1, System.currentTimeMillis() / 1000));
        assertThat(m.getMetrics().size(), is(1));
        assertThat(m.getMetrics().get(0).getName(), is("count"));
    }

    @Test
    public void testHealthMetric() {
        HealthMetric m = HealthMetric.get(null, null);
        assertThat(m.isOk(), is(false));
        m = HealthMetric.get("up", "test message");
        assertThat(m.isOk(), is(true));
        assertThat(m.getMessage(), is("test message"));
        m = HealthMetric.get("ok", "test message");
        assertThat(m.isOk(), is(true));
        assertThat(m.getMessage(), is("test message"));

        m = HealthMetric.get("bad", "test message");
        assertThat(m.isOk(), is(false));
        assertThat(m.getStatus(), is(StatusCode.UNKNOWN));
    }

    @Test
    public void testMetricFormatter() {
        MetricsFormatter formatter = new MetricsFormatter(false, false);
        VespaService service = new DummyService(0, "config.id");
        String data = formatter.format(service, "key", 1);
        assertThat(data, is("'config.id'.key=1"));

        formatter = new MetricsFormatter(true, false);
        data = formatter.format(service, "key", 1);
        assertThat(data, is("dummy.'config.id'.key=1"));


        formatter = new MetricsFormatter(true, true);
        data = formatter.format(service, "key", 1);
        assertThat(data, is("dummy.config.'id'.key=1"));

        formatter = new MetricsFormatter(false, true);
        data = formatter.format(service, "key", 1);
        assertThat(data, is("config.'id'.key=1"));
    }

    @Test
    public void testTimeAdjustment() {
        assertThat(Metric.adjustTime(0L, 0L), is(0L));
        assertThat(Metric.adjustTime(59L, 59L), is(59L));
        assertThat(Metric.adjustTime(60L, 60L), is(60L));
        assertThat(Metric.adjustTime(59L, 60L), is(60L));
        assertThat(Metric.adjustTime(60L, 59L), is(60L));
        assertThat(Metric.adjustTime(59L, 61L), is(59L));
    }

}
