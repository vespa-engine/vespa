// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.yahoo.container.jdisc.state.MetricsPacketsHandler.APPLICATION_KEY;
import static com.yahoo.container.jdisc.state.MetricsPacketsHandler.DIMENSIONS_KEY;
import static com.yahoo.container.jdisc.state.MetricsPacketsHandler.METRICS_KEY;
import static com.yahoo.container.jdisc.state.MetricsPacketsHandler.PACKET_SEPARATOR;
import static com.yahoo.container.jdisc.state.MetricsPacketsHandler.TIMESTAMP_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
public class MetricsPacketsHandlerTest extends StateHandlerTestBase {

    private static final String APPLICATION_NAME = "state-handler-test-base";
    private static final String HOST_DIMENSION = "some-hostname";

    private static MetricsPacketsHandler metricsPacketsHandler;

    @BeforeEach
    public void setupHandler() {
        metricsPacketsHandlerConfig = new MetricsPacketsHandlerConfig(new MetricsPacketsHandlerConfig.Builder()
                                                                              .application(APPLICATION_NAME).hostname(HOST_DIMENSION));
        metricsPacketsHandler = new MetricsPacketsHandler(timer, snapshotProviderRegistry, metricsPacketsHandlerConfig);
        testDriver = new RequestHandlerTestDriver(metricsPacketsHandler);
    }

    @Test
    void metrics_are_included_after_snapshot() throws Exception {
        createSnapshotWithCountMetric("counter", 1, null);
        List<JsonNode> packets = incrementTimeAndGetJsonPackets();
        assertEquals(1, packets.size());

        JsonNode counterPacket = packets.get(0);
        assertCountMetric(counterPacket, "counter.count", 1);
    }

    @Test
    void metadata_is_included_in_each_metrics_packet() throws Exception {
        createSnapshotWithCountMetric("counter", 1, null);
        List<JsonNode> packets = incrementTimeAndGetJsonPackets();
        JsonNode counterPacket = packets.get(0);

        assertTrue(counterPacket.has(TIMESTAMP_KEY));
        assertTrue(counterPacket.has(APPLICATION_KEY));
        assertEquals(APPLICATION_NAME, counterPacket.get(APPLICATION_KEY).asText());
    }

    @Test
    void timestamp_resolution_is_in_seconds() throws Exception {
        createSnapshotWithCountMetric("counter", 1, null);
        List<JsonNode> packets = incrementTimeAndGetJsonPackets();
        JsonNode counterPacket = packets.get(0);

        assertEquals(SNAPSHOT_INTERVAL / 1000L, counterPacket.get(TIMESTAMP_KEY).asLong());
    }

    @Test
    void expected_aggregators_are_output_for_gauge_metrics() throws Exception {
        var context = StateMetricContext.newInstance(Map.of("dim1", "value1"));
        var snapshot = new MetricSnapshot();
        snapshot.set(context, "gauge", 0.2);
        snapshotProvider.setSnapshot(snapshot);

        List<JsonNode> packets = incrementTimeAndGetJsonPackets();
        JsonNode gaugeMetric = packets.get(0).get(METRICS_KEY);

        assertEquals(0.2, gaugeMetric.get("gauge.last").asDouble(), 0.1);
        assertEquals(0.2, gaugeMetric.get("gauge.average").asDouble(), 0.1);
        assertEquals(0.2, gaugeMetric.get("gauge.max").asDouble(), 0.1);
    }

    @Test
    void dimensions_from_context_are_included() throws Exception {
        var context = StateMetricContext.newInstance(Map.of("dim1", "value1"));
        createSnapshotWithCountMetric("counter", 1, context);

        List<JsonNode> packets = incrementTimeAndGetJsonPackets();
        JsonNode counterPacket = packets.get(0);

        assertTrue(counterPacket.has(DIMENSIONS_KEY));
        JsonNode dimensions = counterPacket.get(DIMENSIONS_KEY);
        assertEquals("value1", dimensions.get("dim1").asText());
    }

    @Test
    void metrics_with_identical_dimensions_are_contained_in_the_same_packet() throws Exception {
        var context = StateMetricContext.newInstance(Map.of("dim1", "value1"));
        var snapshot = new MetricSnapshot();
        snapshot.add(context, "counter1", 1);
        snapshot.add(context, "counter2", 2);
        snapshotProvider.setSnapshot(snapshot);

        List<JsonNode> packets = incrementTimeAndGetJsonPackets();
        assertEquals(1, packets.size());
        JsonNode countersPacket = packets.get(0);

        assertEquals("value1", countersPacket.get(DIMENSIONS_KEY).get("dim1").asText());
        assertCountMetric(countersPacket, "counter1.count", 1);
        assertCountMetric(countersPacket, "counter2.count", 2);
    }

    @Test
    void metrics_with_different_dimensions_get_separate_packets() throws Exception {
        var context1 = StateMetricContext.newInstance(Map.of("dim1", "value1"));
        var context2 = StateMetricContext.newInstance(Map.of("dim2", "value2"));
        var snapshot = new MetricSnapshot();
        snapshot.add(context1, "counter1", 1);
        snapshot.add(context2, "counter2", 2);
        snapshotProvider.setSnapshot(snapshot);

        List<JsonNode> packets = incrementTimeAndGetJsonPackets();
        assertEquals(2, packets.size());
    }

    @Test
    void host_dimension_only_created_if_absent() throws Exception {
        var context1 = StateMetricContext.newInstance(Map.of("dim1", "value1", "host", "foo.bar"));
        var context2 = StateMetricContext.newInstance(Map.of("dim2", "value2"));
        var snapshot = new MetricSnapshot();
        snapshot.add(context1, "counter1", 1);
        snapshot.add(context2, "counter2", 2);
        snapshotProvider.setSnapshot(snapshot);

        var packets = incrementTimeAndGetJsonPackets();
        assertEquals(2, packets.size());

        packets.forEach(packet -> {
            if (!packet.has(DIMENSIONS_KEY)) return;
            var dimensions = packet.get(DIMENSIONS_KEY);
            if (dimensions.has("dim1")) assertDimension(packet, "host", "foo.bar");
            if (dimensions.has("dim2")) assertDimension(packet, "host", HOST_DIMENSION);
        });
    }

    @Test
    public void prometheus_metrics() {
        var context = StateMetricContext.newInstance(Map.of("dim-1", "value1"));
        var snapshot = new MetricSnapshot();
        snapshot.set(context, "gauge.metric", 0.2);
        snapshot.add(context, "counter.metric", 5);
        snapshotProvider.setSnapshot(snapshot);
        var response = requestAsString("http://localhost/metrics-packets?format=prometheus");
        var expectedResponse = """
                # HELP gauge_metric_last\s
                # TYPE gauge_metric_last untyped
                gauge_metric_last{dim_1="value1",vespa_service="state-handler-test-base",} 0.2 0
                # HELP counter_metric_count\s
                # TYPE counter_metric_count untyped
                counter_metric_count{dim_1="value1",vespa_service="state-handler-test-base",} 5 0
                """;
        assertEquals(expectedResponse, response);
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

    private void assertDimension(JsonNode metricsPacket, String dimensionName, String expectedDimensionValue) {
        assertTrue(metricsPacket.has(DIMENSIONS_KEY));
        var dimensions = metricsPacket.get(DIMENSIONS_KEY);
        assertTrue(dimensions.has(dimensionName));
        assertEquals(expectedDimensionValue, dimensions.get(dimensionName).asText());
    }

    private void createSnapshotWithCountMetric(String name, Number value, MetricDimensions context) {
        var snapshot = new MetricSnapshot();
        snapshot.add(context, name, value);
        snapshotProvider.setSnapshot(snapshot);
    }

}
