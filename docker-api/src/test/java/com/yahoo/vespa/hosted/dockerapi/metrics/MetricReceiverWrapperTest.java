// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi.metrics;

import com.yahoo.metrics.simple.MetricReceiver;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author valerijf
 */
public class MetricReceiverWrapperTest {
    private static final Dimensions hostDimension = new Dimensions.Builder().add("host", "abc.yahoo.com").build();

    @Test
    public void testDefaultValue() {
        MetricReceiverWrapper metricReceiver = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        metricReceiver.declareCounter(hostDimension, "some.name");

        assertEquals(metricReceiver.getMetricsForDimension(hostDimension).get("some.name"), 0L);
    }

    @Test
    public void testSimpleIncrementMetric() {
        MetricReceiverWrapper metricReceiver = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        CounterWrapper counter = metricReceiver.declareCounter(hostDimension, "a_counter.value");

        counter.add(5);
        counter.add(8);

        Map<String, Number> latestMetrics = metricReceiver.getMetricsForDimension(hostDimension);
        assertTrue("Expected only 1 metric value to be set", latestMetrics.size() == 1);
        assertEquals(latestMetrics.get("a_counter.value"), 13L); // 5 + 8
    }

    @Test
    public void testSimpleGauge() {
        MetricReceiverWrapper metricReceiver = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        GaugeWrapper gauge = metricReceiver.declareGauge(hostDimension, "test.gauge");

        gauge.sample(42);
        gauge.sample(-342.23);

        Map<String, Number> latestMetrics = metricReceiver.getMetricsForDimension(hostDimension);
        assertTrue("Expected only 1 metric value to be set", latestMetrics.size() == 1);
        assertEquals(latestMetrics.get("test.gauge"), -342.23);
    }

    @Test
    public void testRedeclaringSameGauge() {
        MetricReceiverWrapper metricReceiver = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        GaugeWrapper gauge = metricReceiver.declareGauge(hostDimension, "test.gauge");
        gauge.sample(42);

        // Same as hostDimension, but new instance.
        Dimensions newDimension = new Dimensions.Builder().add("host", "abc.yahoo.com").build();
        GaugeWrapper newGauge = metricReceiver.declareGauge(newDimension, "test.gauge");
        newGauge.sample(56);

        assertEquals(metricReceiver.getMetricsForDimension(hostDimension).get("test.gauge"), 56.);
    }

    @Test
    public void testSameMetricNameButDifferentDimensions() {
        MetricReceiverWrapper metricReceiver = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        GaugeWrapper gauge = metricReceiver.declareGauge(hostDimension, "test.gauge");
        gauge.sample(42);

        // Not the same as hostDimension.
        Dimensions newDimension = new Dimensions.Builder().add("host", "abcd.yahoo.com").build();
        GaugeWrapper newGauge = metricReceiver.declareGauge(newDimension, "test.gauge");
        newGauge.sample(56);

        assertEquals(metricReceiver.getMetricsForDimension(hostDimension).get("test.gauge"), 42.);
        assertEquals(metricReceiver.getMetricsForDimension(newDimension).get("test.gauge"), 56.);
    }
}
