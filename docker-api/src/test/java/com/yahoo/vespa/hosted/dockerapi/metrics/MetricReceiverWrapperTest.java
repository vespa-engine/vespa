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
    @Test
    public void testDefaultValue() {
        MetricReceiverWrapper metricReceiver = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        metricReceiver.declareCounter("some.name");

        assertEquals(metricReceiver.getLatestMetrics().get("some.name"), 0L);
    }

    @Test
    public void testSimpleIncrementMetric() {
        MetricReceiverWrapper metricReceiver = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        CounterWrapper counter = metricReceiver.declareCounter("a_counter.value");

        counter.add(5);
        counter.add(8);

        Map<String, Number> latestMetrics = metricReceiver.getLatestMetrics();
        assertTrue("Expected only 1 metric value to be set", latestMetrics.size() == 1);
        assertEquals(latestMetrics.get("a_counter.value"), 13L); // 5 + 8
    }

    @Test
    public void testSimpleGauge() {
        MetricReceiverWrapper metricReceiver = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        GaugeWrapper gauge = metricReceiver.declareGauge("test.gauge");

        gauge.sample(42);
        gauge.sample(-342.23);

        Map<String, Number> latestMetrics = metricReceiver.getLatestMetrics();
        assertTrue("Expected only 1 metric value to be set", latestMetrics.size() == 1);
        assertEquals(latestMetrics.get("test.gauge"), -342.23);
    }
}
