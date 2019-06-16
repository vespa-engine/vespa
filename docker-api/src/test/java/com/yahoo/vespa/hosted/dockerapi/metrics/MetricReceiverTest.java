// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi.metrics;

import org.junit.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiver.APPLICATION_HOST;
import static com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiver.DimensionType.DEFAULT;
import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class MetricReceiverTest {
    private static final Dimensions hostDimension = new Dimensions.Builder().add("host", "abc.yahoo.com").build();
    private final MetricReceiver metricReceiver = new MetricReceiver();

    @Test
    public void testDefaultValue() {
        metricReceiver.declareCounter("some.name", hostDimension);

        assertEquals(getMetricsForDimension(hostDimension).get("some.name"), 0L);
    }

    @Test
    public void testSimpleIncrementMetric() {
        Counter counter = metricReceiver.declareCounter("a_counter.value", hostDimension);

        counter.add(5);
        counter.add(8);

        Map<String, Number> latestMetrics = getMetricsForDimension(hostDimension);
        assertEquals("Expected only 1 metric value to be set", 1, latestMetrics.size());
        assertEquals(latestMetrics.get("a_counter.value"), 13L); // 5 + 8
    }

    @Test
    public void testSimpleGauge() {
        Gauge gauge = metricReceiver.declareGauge("test.gauge", hostDimension);

        gauge.sample(42);
        gauge.sample(-342.23);

        Map<String, Number> latestMetrics = getMetricsForDimension(hostDimension);
        assertEquals("Expected only 1 metric value to be set", 1, latestMetrics.size());
        assertEquals(latestMetrics.get("test.gauge"), -342.23);
    }

    @Test
    public void testRedeclaringSameGauge() {
        Gauge gauge = metricReceiver.declareGauge("test.gauge", hostDimension);
        gauge.sample(42);

        // Same as hostDimension, but new instance.
        Dimensions newDimension = new Dimensions.Builder().add("host", "abc.yahoo.com").build();
        Gauge newGauge = metricReceiver.declareGauge("test.gauge", newDimension);
        newGauge.sample(56);

        assertEquals(getMetricsForDimension(hostDimension).get("test.gauge"), 56.);
    }

    @Test
    public void testSameMetricNameButDifferentDimensions() {
        Gauge gauge = metricReceiver.declareGauge("test.gauge", hostDimension);
        gauge.sample(42);

        // Not the same as hostDimension.
        Dimensions newDimension = new Dimensions.Builder().add("host", "abcd.yahoo.com").build();
        Gauge newGauge = metricReceiver.declareGauge("test.gauge", newDimension);
        newGauge.sample(56);

        assertEquals(getMetricsForDimension(hostDimension).get("test.gauge"), 42.);
        assertEquals(getMetricsForDimension(newDimension).get("test.gauge"), 56.);
    }

    @Test
    public void testDeletingMetric() {
        metricReceiver.declareGauge("test.gauge", hostDimension);

        Dimensions differentDimension = new Dimensions.Builder().add("host", "abcd.yahoo.com").build();
        metricReceiver.declareGauge("test.gauge", differentDimension);

        assertEquals(2, metricReceiver.getMetricsByType(DEFAULT).size());
        metricReceiver.deleteMetricByDimension(APPLICATION_HOST, differentDimension, DEFAULT);
        assertEquals(1, metricReceiver.getMetricsByType(DEFAULT).size());
        assertEquals(getMetricsForDimension(hostDimension).size(), 1);
        assertEquals(getMetricsForDimension(differentDimension).size(), 0);
    }

    private Map<String, Number> getMetricsForDimension(Dimensions dimensions) {
        return metricReceiver.getOrCreateApplicationMetrics(APPLICATION_HOST, DEFAULT)
                .getOrDefault(dimensions, Map.of())
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue()));
    }
}
