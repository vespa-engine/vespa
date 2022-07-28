// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.component.Vtag;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.vespa.defaults.Defaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class StateHandlerTest extends StateHandlerTestBase {

    private static final String V1_URI = URI_BASE + "/state/v1/";
    private static StateHandler stateHandler;

    @BeforeEach
    public void setupHandler() {
        stateHandler = new StateHandler(monitor, timer, applicationMetadataConfig, snapshotProviderRegistry);
        testDriver = new RequestHandlerTestDriver(stateHandler);
    }

    @Test
    void testStatusReportPriorToFirstSnapshot() throws Exception {
        JsonNode json = requestAsJson(V1_URI + "/all");
        assertEquals("up", json.get("status").get("code").asText(), json.toString());
        assertFalse(json.get("metrics").has("values"), json.toString());
    }

    @Test
    void testReportIncludesMetricsAfterSnapshot() throws Exception {
        var snapshot = new MetricSnapshot();
        snapshot.add(null, "foo", 1);
        snapshot.set(null, "bar", 4);
        snapshotProvider.setSnapshot(snapshot);

        JsonNode json1 = requestAsJson(V1_URI + "metrics");
        assertEquals("up", json1.get("status").get("code").asText(), json1.toString());
        assertEquals(2, json1.get("metrics").get("values").size(), json1.toString());
    }

    @Test
    void testCountMetricCount() throws Exception {
        var snapshot = new MetricSnapshot();
        snapshot.add(null, "foo", 4);
        snapshot.add(null, "foo", 2);
        snapshotProvider.setSnapshot(snapshot);

        JsonNode json = requestAsJson(V1_URI + "all");
        assertEquals("up", json.get("status").get("code").asText(), json.toString());
        assertEquals(1, json.get("metrics").get("values").size(), json.toString());
        assertEquals(6,
                json.get("metrics").get("values").get(0).get("values").get("count").asDouble(), 0.001, json.toString());
    }

    @Test
    void gaugeSnapshotsTracksCountMinMaxAvgPerPeriod() throws Exception {
        var snapshot = new MetricSnapshot();
        snapshot.set(null, "bar", 20);
        snapshot.set(null, "bar", 40);
        snapshotProvider.setSnapshot(snapshot);

        JsonNode json = requestAsJson(V1_URI + "all");
        JsonNode metricValues = getFirstMetricValueNode(json);
        assertEquals(40, metricValues.get("last").asDouble(), 0.001, json.toString());
        // Last snapshot had explicit values set
        assertEquals(30, metricValues.get("average").asDouble(), 0.001, json.toString());
        assertEquals(20, metricValues.get("min").asDouble(), 0.001, json.toString());
        assertEquals(40, metricValues.get("max").asDouble(), 0.001, json.toString());
        assertEquals(2, metricValues.get("count").asInt(), json.toString());
    }

    private JsonNode getFirstMetricValueNode(JsonNode root) {
        assertEquals(1, root.get("metrics").get("values").size(), root.toString());
        JsonNode metricValues = root.get("metrics").get("values").get(0).get("values");
        assertTrue(metricValues.has("last"), root.toString());
        return metricValues;
    }

    @Test
    void testReadabilityOfJsonReport() {
        var snapshot = new MetricSnapshot(0L, SNAPSHOT_INTERVAL, TimeUnit.MILLISECONDS);
        snapshot.add(null, "foo", 1);
        var ctx = StateMetricContext.newInstance(Map.of("component", "test"));
        snapshot.set(ctx, "bar", 2);
        snapshot.set(ctx, "bar", 3);
        snapshot.set(ctx, "bar", 4);
        snapshot.set(ctx, "bar", 5);
        snapshotProvider.setSnapshot(snapshot);
        advanceToNextSnapshot();
        assertEquals("{\n" +
                "  \"time\" : 300000,\n" +
                "  \"status\" : {\n" +
                "    \"code\" : \"up\"\n" +
                "  },\n" +
                "  \"metrics\" : {\n" +
                "    \"snapshot\" : {\n" +
                "      \"from\" : 0.0,\n" +
                "      \"to\" : 300.0\n" +
                "    },\n" +
                "    \"values\" : [ {\n" +
                "      \"name\" : \"foo\",\n" +
                "      \"values\" : {\n" +
                "        \"count\" : 1,\n" +
                "        \"rate\" : 0.0033333333333333335\n" +
                "      }\n" +
                "    }, {\n" +
                "      \"name\" : \"bar\",\n" +
                "      \"values\" : {\n" +
                "        \"average\" : 3.5,\n" +
                "        \"sum\" : 14.0,\n" +
                "        \"count\" : 4,\n" +
                "        \"last\" : 5.0,\n" +
                "        \"max\" : 5.0,\n" +
                "        \"min\" : 2.0,\n" +
                "        \"rate\" : 0.013333333333333334\n" +
                "      },\n" +
                "      \"dimensions\" : {\n" +
                "        \"component\" : \"test\"\n" +
                "      }\n" +
                "    } ]\n" +
                "  }\n" +
                "}",
                requestAsString(V1_URI + "all"));
    }

    @Test
    void testHealthAggregation() throws Exception {
        var context1 = StateMetricContext.newInstance(Map.of("port", Defaults.getDefaults().vespaWebServicePort()));
        var context2 = StateMetricContext.newInstance(Map.of("port", 80));
        var snapshot = new MetricSnapshot();
        snapshot.add(context1, "serverNumSuccessfulResponses", 4);
        snapshot.add(context2, "serverNumSuccessfulResponses", 2);
        snapshot.set(context1, "serverTotalSuccessfulResponseLatency", 20);
        snapshot.set(context2, "serverTotalSuccessfulResponseLatency", 40);
        snapshot.add(context1, "random", 3);
        snapshotProvider.setSnapshot(snapshot);

        JsonNode json = requestAsJson(V1_URI + "health");
        assertEquals("up", json.get("status").get("code").asText(), json.toString());
        assertEquals(2, json.get("metrics").get("values").size(), json.toString());
        assertEquals("requestsPerSecond",
                json.get("metrics").get("values").get(0).get("name").asText(),
                json.toString());
        assertEquals(6,
                json.get("metrics").get("values").get(0).get("values").get("count").asDouble(), 0.001, json.toString());
        assertEquals("latencySeconds",
                json.get("metrics").get("values").get(1).get("name").asText(),
                json.toString());
        assertEquals(0.03,
                json.get("metrics").get("values").get(1).get("values").get("average").asDouble(), 0.001, json.toString());
    }

    @Test
    void testStateConfig() throws Exception {
        JsonNode root = requestAsJson(V1_URI + "config");

        JsonNode config = root.get("config");
        JsonNode container = config.get("container");
        assertEquals(META_GENERATION, container.get("generation").asLong());
    }

    @Test
    void testStateVersion() throws Exception {
        JsonNode root = requestAsJson(V1_URI + "version");

        JsonNode version = root.get("version");
        assertEquals(Vtag.currentVersion.toString(), version.asText());
    }
}
