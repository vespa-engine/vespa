// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.yahoo.container.jdisc.state.MetricsPacketsHandler.APPLICATION_KEY;
import static com.yahoo.container.jdisc.state.MetricsPacketsHandler.DIMENSIONS_KEY;
import static com.yahoo.container.jdisc.state.MetricsPacketsHandler.METRICS_KEY;
import static com.yahoo.container.jdisc.state.MetricsPacketsHandler.PACKET_SEPARATOR;
import static com.yahoo.container.jdisc.state.MetricsPacketsHandler.STATUS_CODE_KEY;
import static com.yahoo.container.jdisc.state.MetricsPacketsHandler.STATUS_MSG_KEY;
import static com.yahoo.container.jdisc.state.MetricsPacketsHandler.TIMESTAMP_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class MetricsPacketsHandlerTest extends StateHandlerTestBase {

    private static final String APPLICATION_NAME = "state-handler-test-base";

    private static MetricsPacketsHandler metricsPacketsHandler;

    @Before
    public void setupHandler() {
        metricsPacketsHandlerConfig = new MetricsPacketsHandlerConfig(new MetricsPacketsHandlerConfig.Builder()
                                                                              .application(APPLICATION_NAME));
        metricsPacketsHandler = new MetricsPacketsHandler(monitor, timer, snapshotProviderRegistry, metricsPacketsHandlerConfig);
        testDriver = new RequestHandlerTestDriver(metricsPacketsHandler);
    }

    @Test
    public void status_packet_is_returned_prior_to_first_snapshot() throws Exception {
        String response = requestAsString("http://localhost/metrics-packets");

        List<JsonNode> packets = toJsonPackets(response);
        assertEquals(1, packets.size());

        JsonNode statusPacket = packets.get(0);
        assertEquals(statusPacket.toString(), APPLICATION_NAME, statusPacket.get(APPLICATION_KEY).asText());
        assertEquals(statusPacket.toString(), 0, statusPacket.get(STATUS_CODE_KEY).asInt());
        assertEquals(statusPacket.toString(), "up", statusPacket.get(STATUS_MSG_KEY).asText());
    }

    @Test
    public void metrics_are_included_after_snapshot() throws Exception {
        createSnapshotWithCountMetric("counter", 1, null);
        List<JsonNode> packets = incrementTimeAndGetJsonPackets();
        assertEquals(2, packets.size());

        JsonNode counterPacket = packets.get(1);
        assertCountMetric(counterPacket, "counter.count", 1);
    }

    @Test
    public void metadata_is_included_in_each_metrics_packet() throws Exception {
        createSnapshotWithCountMetric("counter", 1, null);
        List<JsonNode> packets = incrementTimeAndGetJsonPackets();
        JsonNode counterPacket = packets.get(1);

        assertTrue(counterPacket.has(TIMESTAMP_KEY));
        assertTrue(counterPacket.has(APPLICATION_KEY));
        assertEquals(APPLICATION_NAME, counterPacket.get(APPLICATION_KEY).asText());
    }

    @Test
    public void timestamp_resolution_is_in_seconds() throws Exception {
        createSnapshotWithCountMetric("counter", 1, null);
        List<JsonNode> packets = incrementTimeAndGetJsonPackets();
        JsonNode counterPacket = packets.get(1);

        assertEquals(SNAPSHOT_INTERVAL/1000L, counterPacket.get(TIMESTAMP_KEY).asLong());
    }

    @Test
    public void expected_aggregators_are_output_for_gauge_metrics() throws Exception{
        var context = StateMetricContext.newInstance(Map.of("dim1", "value1"));
        var snapshot = new MetricSnapshot();
        snapshot.set(context, "gauge", 0.2);
        snapshotProvider.setSnapshot(snapshot);

        List<JsonNode> packets = incrementTimeAndGetJsonPackets();
        JsonNode gaugeMetric = packets.get(1).get(METRICS_KEY);

        assertEquals(0.2, gaugeMetric.get("gauge.last").asDouble(), 0.1);
        assertEquals(0.2, gaugeMetric.get("gauge.average").asDouble(), 0.1);
        assertEquals(0.2, gaugeMetric.get("gauge.max").asDouble(), 0.1);
    }

    @Test
    public void dimensions_from_context_are_included() throws Exception {
        var context = StateMetricContext.newInstance(Map.of("dim1", "value1"));
        createSnapshotWithCountMetric("counter", 1, context);

        List<JsonNode> packets = incrementTimeAndGetJsonPackets();
        JsonNode counterPacket = packets.get(1);

        assertTrue(counterPacket.has(DIMENSIONS_KEY));
        JsonNode dimensions = counterPacket.get(DIMENSIONS_KEY);
        assertEquals("value1", dimensions.get("dim1").asText());
    }

    @Test
    public void metrics_with_identical_dimensions_are_contained_in_the_same_packet() throws Exception {
        var context = StateMetricContext.newInstance(Map.of("dim1", "value1"));
        var snapshot = new MetricSnapshot();
        snapshot.add(context, "counter1", 1);
        snapshot.add(context, "counter2", 2);
        snapshotProvider.setSnapshot(snapshot);

        List<JsonNode> packets = incrementTimeAndGetJsonPackets();
        assertEquals(2, packets.size());
        JsonNode countersPacket = packets.get(1);

        assertEquals("value1", countersPacket.get(DIMENSIONS_KEY).get("dim1").asText());
        assertCountMetric(countersPacket, "counter1.count", 1);
        assertCountMetric(countersPacket, "counter2.count", 2);
    }

    @Test
    public void metrics_with_different_dimensions_get_separate_packets() throws Exception {
        var context1 = StateMetricContext.newInstance(Map.of("dim1", "value1"));
        var context2 = StateMetricContext.newInstance(Map.of("dim2", "value2"));
        var snapshot = new MetricSnapshot();
        snapshot.add(context1, "counter1", 1);
        snapshot.add(context2, "counter2", 2);
        snapshotProvider.setSnapshot(snapshot);

        List<JsonNode> packets = incrementTimeAndGetJsonPackets();
        assertEquals(3, packets.size());
    }
    
    private List<JsonNode> incrementTimeAndGetJsonPackets() throws Exception {
        advanceToNextSnapshot();
        String response = requestAsString("http://localhost/metrics-packets");

        return toJsonPackets(response);
    }

    private List<JsonNode> toJsonPackets(String response) throws Exception {
        List<JsonNode> jsonPackets = new ArrayList<>();
        String[] packets = response.split(PACKET_SEPARATOR);
        ObjectMapper mapper = new ObjectMapper();
        for (String packet : packets) {
            jsonPackets.add(mapper.readTree(mapper.getFactory().createParser(packet)));
        }
        return jsonPackets;
    }

    private void assertCountMetric(JsonNode metricsPacket, String metricName, long expected) {
        assertTrue(metricsPacket.has(METRICS_KEY));
        JsonNode counterMetrics = metricsPacket.get(METRICS_KEY);
        assertTrue(counterMetrics.has(metricName));
        assertEquals(expected, counterMetrics.get(metricName).asLong());
    }

    private void createSnapshotWithCountMetric(String name, Number value, MetricDimensions context) {
        var snapshot = new MetricSnapshot();
        snapshot.add(context, name, value);
        snapshotProvider.setSnapshot(snapshot);
    }

}
