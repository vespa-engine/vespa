// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.component.Vtag;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.defaults.Defaults;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class StateHandlerTest extends StateHandlerTestBase {

    @Test
    public void testReportPriorToFirstSnapshot() throws Exception {
        metric.add("foo", 1, null);
        metric.set("bar", 4, null);
        JsonNode json = requestAsJson("http://localhost/state/v1/all");
        assertEquals(json.toString(), "up", json.get("status").get("code").asText());
        assertFalse(json.toString(), json.get("metrics").has("values"));
    }

    @Test
    public void testReportIncludesMetricsAfterSnapshot() throws Exception {
        metric.add("foo", 1, null);
        metric.set("bar", 4, null);
        incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
        JsonNode json1 = requestAsJson("http://localhost/state/v1/metrics");
        assertEquals(json1.toString(), "up", json1.get("status").get("code").asText());
        assertEquals(json1.toString(), 2, json1.get("metrics").get("values").size());

        metric.add("fuz", 1, metric.createContext(new HashMap<>(0)));
        incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
        JsonNode json2 = requestAsJson("http://localhost/state/v1/metrics");
        assertEquals(json2.toString(), "up", json2.get("status").get("code").asText());
        assertEquals(json2.toString(), 3, json2.get("metrics").get("values").size());
    }

    /**
     * Tests that we restart an metric when it changes type from gauge to counter or back.
     * This may happen in practice on config reloads.
     */
    @Test
    public void testMetricTypeChangeIsAllowed() {
        String metricName = "myMetric";
        Metric.Context metricContext = null;

        {
            // Add a count metric
            metric.add(metricName, 1, metricContext);
            metric.add(metricName, 2, metricContext);
            // Change it to a gauge metric
            metric.set(metricName, 9, metricContext);
            incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
            MetricValue resultingMetric = monitor.snapshot().iterator().next().getValue().get(metricName);
            assertEquals(GaugeMetric.class, resultingMetric.getClass());
            assertEquals("Value was reset and produces the last gauge value",
                         9.0, ((GaugeMetric) resultingMetric).getLast(), 0.000001);
        }

        {
            // Add a gauge metric
            metric.set(metricName, 9, metricContext);
            // Change it to a count metric
            metric.add(metricName, 1, metricContext);
            metric.add(metricName, 2, metricContext);
            incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
            MetricValue resultingMetric = monitor.snapshot().iterator().next().getValue().get(metricName);
            assertEquals(CountMetric.class, resultingMetric.getClass());
            assertEquals("Value was reset, and changed to add semantics giving 1+2",
                         3, ((CountMetric) resultingMetric).getCount());
        }
    }

    @Test
    public void testAverageAggregationOfValues() throws Exception {
        metric.set("bar", 4, null);
        metric.set("bar", 5, null);
        metric.set("bar", 7, null);
        metric.set("bar", 2, null);
        incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
        JsonNode json = requestAsJson("http://localhost/state/v1/all");
        assertEquals(json.toString(), "up", json.get("status").get("code").asText());
        assertEquals(json.toString(), 1, json.get("metrics").get("values").size());
        assertEquals(json.toString(), 4.5,
                     json.get("metrics").get("values").get(0).get("values").get("average").asDouble(), 0.001);
    }

    @Test
    public void testSumAggregationOfCounts() throws Exception {
        metric.add("foo", 1, null);
        metric.add("foo", 1, null);
        metric.add("foo", 2, null);
        metric.add("foo", 1, null);
        incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
        JsonNode json = requestAsJson("http://localhost/state/v1/all");
        assertEquals(json.toString(), "up", json.get("status").get("code").asText());
        assertEquals(json.toString(), 1, json.get("metrics").get("values").size());
        assertEquals(json.toString(), 5,
                     json.get("metrics").get("values").get(0).get("values").get("count").asDouble(), 0.001);
    }

    @Test
    public void testReadabilityOfJsonReport() throws Exception {
        metric.add("foo", 1, null);
        incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
        assertEquals("{\n" +
                     "    \"metrics\": {\n" +
                     "        \"snapshot\": {\n" +
                     "            \"from\": 0,\n" +
                     "            \"to\": 300\n" +
                     "        },\n" +
                     "        \"values\": [{\n" +
                     "            \"name\": \"foo\",\n" +
                     "            \"values\": {\n" +
                     "                \"count\": 1,\n" +
                     "                \"rate\": 0.0033333333333333335\n" +
                     "            }\n" +
                     "        }]\n" +
                     "    },\n" +
                     "    \"status\": {\"code\": \"up\"},\n" +
                     "    \"time\": 300000\n" +
                     "}",
                     requestAsString("http://localhost/state/v1/all"));

        Metric.Context ctx = metric.createContext(Collections.singletonMap("component", "test"));
        metric.set("bar", 2, ctx);
        metric.set("bar", 3, ctx);
        metric.set("bar", 4, ctx);
        metric.set("bar", 5, ctx);
        incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
        assertEquals("{\n" +
                     "    \"metrics\": {\n" +
                     "        \"snapshot\": {\n" +
                     "            \"from\": 300,\n" +
                     "            \"to\": 600\n" +
                     "        },\n" +
                     "        \"values\": [\n" +
                     "            {\n" +
                     "                \"name\": \"foo\",\n" +
                     "                \"values\": {\n" +
                     "                    \"count\": 0,\n" +
                     "                    \"rate\": 0\n" +
                     "                }\n" +
                     "            },\n" +
                     "            {\n" +
                     "                \"dimensions\": {\"component\": \"test\"},\n" +
                     "                \"name\": \"bar\",\n" +
                     "                \"values\": {\n" +
                     "                    \"average\": 3.5,\n" +
                     "                    \"count\": 4,\n" +
                     "                    \"last\": 5,\n" +
                     "                    \"max\": 5,\n" +
                     "                    \"min\": 2,\n" +
                     "                    \"rate\": 0.013333333333333334,\n" +
                     "                    \"sum\": 14\n" +
                     "                }\n" +
                     "            }\n" +
                     "        ]\n" +
                     "    },\n" +
                     "    \"status\": {\"code\": \"up\"},\n" +
                     "    \"time\": 600000\n" +
                     "}",
                     requestAsString("http://localhost/state/v1/all"));
    }

    @Test
    public void testNotAggregatingCountsBeyondSnapshots() throws Exception {
        metric.add("foo", 1, null);
        metric.add("foo", 1, null);
        incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
        metric.add("foo", 2, null);
        metric.add("foo", 1, null);
        incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
        JsonNode json = requestAsJson("http://localhost/state/v1/all");
        assertEquals(json.toString(), "up", json.get("status").get("code").asText());
        assertEquals(json.toString(), 1, json.get("metrics").get("values").size());
        assertEquals(json.toString(), 3,
                     json.get("metrics").get("values").get(0).get("values").get("count").asDouble(), 0.001);
    }

    @Test
    public void testSnapshottingTimes() throws Exception {
        metric.add("foo", 1, null);
        metric.set("bar", 3, null);
        // At this time we should not have done any snapshotting
        incrementCurrentTimeAndAssertNoSnapshot(SNAPSHOT_INTERVAL - 1);
        {
            JsonNode json = requestAsJson("http://localhost/state/v1/all");
            assertFalse(json.toString(), json.get("metrics").has("snapshot"));
        }
        // At this time first snapshot should have been generated
        incrementCurrentTimeAndAssertSnapshot(1);
        {
            JsonNode json = requestAsJson("http://localhost/state/v1/all");
            assertTrue(json.toString(), json.get("metrics").has("snapshot"));
            assertEquals(0.0, json.get("metrics").get("snapshot").get("from").asDouble(), 0.00001);
            assertEquals(300.0, json.get("metrics").get("snapshot").get("to").asDouble(), 0.00001);
        }
        // No new snapshot at this time
        incrementCurrentTimeAndAssertNoSnapshot(SNAPSHOT_INTERVAL - 1);
        {
            JsonNode json = requestAsJson("http://localhost/state/v1/all");
            assertTrue(json.toString(), json.get("metrics").has("snapshot"));
            assertEquals(0.0, json.get("metrics").get("snapshot").get("from").asDouble(), 0.00001);
            assertEquals(300.0, json.get("metrics").get("snapshot").get("to").asDouble(), 0.00001);
        }
        // A new snapshot
        incrementCurrentTimeAndAssertSnapshot(1);
        {
            JsonNode json = requestAsJson("http://localhost/state/v1/all");
            assertTrue(json.toString(), json.get("metrics").has("snapshot"));
            assertEquals(300.0, json.get("metrics").get("snapshot").get("from").asDouble(), 0.00001);
            assertEquals(600.0, json.get("metrics").get("snapshot").get("to").asDouble(), 0.00001);
        }
    }

    @Test
    public void testFreshStartOfValuesBeyondSnapshot() throws Exception {
        metric.set("bar", 4, null);
        metric.set("bar", 5, null);
        incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
        metric.set("bar", 4, null);
        metric.set("bar", 2, null);
        incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
        JsonNode json = requestAsJson("http://localhost/state/v1/all");
        assertEquals(json.toString(), "up", json.get("status").get("code").asText());
        assertEquals(json.toString(), 1, json.get("metrics").get("values").size());
        assertEquals(json.toString(), 3,
                     json.get("metrics").get("values").get(0).get("values").get("average").asDouble(), 0.001);
    }

    @Test
    public void snapshotsPreserveLastGaugeValue() throws Exception {
        metric.set("bar", 4, null);
        incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
        incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
        JsonNode json = requestAsJson("http://localhost/state/v1/all");
        JsonNode metricValues = getFirstMetricValueNode(json);
        assertEquals(json.toString(), 4, metricValues.get("last").asDouble(), 0.001);
        // Use 'last' as avg/min/max when none has been set explicitly during snapshot period
        assertEquals(json.toString(), 4, metricValues.get("average").asDouble(), 0.001);
        assertEquals(json.toString(), 4, metricValues.get("min").asDouble(), 0.001);
        assertEquals(json.toString(), 4, metricValues.get("max").asDouble(), 0.001);
        // Count is tracked per period.
        assertEquals(json.toString(), 0, metricValues.get("count").asInt());
    }

    private JsonNode getFirstMetricValueNode(JsonNode root) {
        assertEquals(root.toString(), 1, root.get("metrics").get("values").size());
        JsonNode metricValues = root.get("metrics").get("values").get(0).get("values");
        assertTrue(root.toString(), metricValues.has("last"));
        return metricValues;
    }

    @Test
    public void gaugeSnapshotsTracksCountMinMaxAvgPerPeriod() throws Exception {
        metric.set("bar", 10000, null); // Ensure any cross-snapshot noise is visible
        incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
        metric.set("bar", 20, null);
        metric.set("bar", 40, null);
        incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
        JsonNode json = requestAsJson("http://localhost/state/v1/all");
        JsonNode metricValues = getFirstMetricValueNode(json);
        assertEquals(json.toString(), 40, metricValues.get("last").asDouble(), 0.001);
        // Last snapshot had explicit values set
        assertEquals(json.toString(), 30, metricValues.get("average").asDouble(), 0.001);
        assertEquals(json.toString(), 20, metricValues.get("min").asDouble(), 0.001);
        assertEquals(json.toString(), 40, metricValues.get("max").asDouble(), 0.001);
        assertEquals(json.toString(), 2, metricValues.get("count").asInt());
    }

    @Test
    public void testHealthAggregation() throws Exception {
        Map<String, String> dimensions1 = new TreeMap<>();
        dimensions1.put("port", String.valueOf(Defaults.getDefaults().vespaWebServicePort()));
        Metric.Context context1 = metric.createContext(dimensions1);
        Map<String, String> dimensions2 = new TreeMap<>();
        dimensions2.put("port", "80");
        Metric.Context context2 = metric.createContext(dimensions2);

        metric.add("serverNumSuccessfulResponses", 4, context1);
        metric.add("serverNumSuccessfulResponses", 2, context2);
        metric.set("serverTotalSuccessfulResponseLatency", 20, context1);
        metric.set("serverTotalSuccessfulResponseLatency", 40, context2);
        metric.add("random", 3, context1);
        incrementCurrentTimeAndAssertSnapshot(SNAPSHOT_INTERVAL);
        JsonNode json = requestAsJson("http://localhost/state/v1/health");
        assertEquals(json.toString(), "up", json.get("status").get("code").asText());
        assertEquals(json.toString(), 2, json.get("metrics").get("values").size());
        assertEquals(json.toString(), "requestsPerSecond",
                     json.get("metrics").get("values").get(0).get("name").asText());
        assertEquals(json.toString(), 6,
                     json.get("metrics").get("values").get(0).get("values").get("count").asDouble(), 0.001);
        assertEquals(json.toString(), "latencySeconds",
                     json.get("metrics").get("values").get(1).get("name").asText());
        assertEquals(json.toString(), 0.03,
                     json.get("metrics").get("values").get(1).get("values").get("average").asDouble(), 0.001);
    }

    @Test
    public void testStateConfig() throws Exception {
        JsonNode root = requestAsJson("http://localhost/state/v1/config");

        JsonNode config = root.get("config");
        JsonNode container = config.get("container");
        assertEquals(META_GENERATION, container.get("generation").asLong());
    }

    @Test
    public void testStateVersion() throws Exception {
        JsonNode root = requestAsJson("http://localhost/state/v1/version");

        JsonNode version = root.get("version");
        assertEquals(Vtag.currentVersion.toString(), version.asText());
    }

    private void incrementCurrentTimeAndAssertNoSnapshot(long val) {
        currentTimeMillis.addAndGet(val);
        assertFalse("Expected no snapshot", monitor.checkTime());;
    }
}
