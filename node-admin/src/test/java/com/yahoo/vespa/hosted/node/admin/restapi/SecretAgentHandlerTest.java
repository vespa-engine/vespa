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
        GaugeWrapper someGauge1 = metricReceiver.declareGauge("some.gauge-1");
        GaugeWrapper someGauge2 = metricReceiver.declareGauge("some.other.gauge");
        CounterWrapper counter = metricReceiver.declareCounter("a_counter.value");

        SecretAgentHandler secretAgentHandler = new SecretAgentHandler(metricReceiver);
        Map<String, Object> response = secretAgentHandler.getSecretAgentReport();

        // Test required fields
        assertTrue(response.containsKey("application"));
        assertTrue(response.containsKey("timestamp"));
        assertTrue(response.containsKey("dimensions"));
        assertFalse(((Map) response.get("dimensions")).isEmpty());

        assertTrue(response.containsKey("metrics"));

        // Test default value
        assertEquals(0L, ((Map) response.get("metrics")).get("a_counter.value"));

        // Set a sample value and check that the new reports contains it
        counter.add(123);
        response = secretAgentHandler.getSecretAgentReport();
        assertEquals(123L, ((Map) response.get("metrics")).get("a_counter.value"));

    }
}
