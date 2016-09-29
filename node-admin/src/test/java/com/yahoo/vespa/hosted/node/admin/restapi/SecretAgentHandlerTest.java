// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.restapi;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.metrics.CounterWrapper;
import com.yahoo.vespa.hosted.dockerapi.metrics.GaugeWrapper;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author valerijf
 */
public class SecretAgentHandlerTest {
    @Test
    public void testSecretAgentFormat() {
        MetricReceiverWrapper metricReceiver = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        CounterWrapper counter = metricReceiver.declareCounter("a_counter.value");
        GaugeWrapper someGauge = metricReceiver.declareGauge("some.other.gauge");
        metricReceiver.declareGauge("some.gauge-1");

        counter.add(5);
        counter.add(8);
        someGauge.sample(123);

        SecretAgentHandler secretAgentHandler = new SecretAgentHandler(metricReceiver);
        Map<String, Object> response = secretAgentHandler.getSecretAgentReport();

        // Test required fields
        assertTrue(response.containsKey("application"));
        assertTrue(response.containsKey("timestamp"));
        assertTrue(response.containsKey("dimensions"));
        assertFalse(((Map) response.get("dimensions")).isEmpty());

        assertTrue(response.containsKey("metrics"));

        // Test the default value
        assertEquals(0., ((Map) response.get("metrics")).get("some.gauge-1"));

        // Test the set value
        assertEquals(123., ((Map) response.get("metrics")).get("some.other.gauge"));

        // Test add function
        assertEquals(13L, ((Map) response.get("metrics")).get("a_counter.value"));
    }
}
