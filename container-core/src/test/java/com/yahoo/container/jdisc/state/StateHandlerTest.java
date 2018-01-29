// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.yahoo.component.Vtag;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.jdisc.handler.BufferedContentChannel;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.metrics.MetricsPresentationConfig;
import com.yahoo.vespa.defaults.Defaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author Simon Thoresen Hult
 */
public class StateHandlerTest {

    private final static long SNAPSHOT_INTERVAL = TimeUnit.SECONDS.toMillis(300);
    private final static long META_GENERATION = 69;
    private TestDriver driver;
    private StateMonitor monitor;
    private Metric metric;
    private final AtomicLong currentTimeMillis = new AtomicLong(0);

    @Before
    public void startTestDriver() {
        Timer timer = this.currentTimeMillis::get;
        this.driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Timer.class).toInstance(timer);
            }
        });
        ContainerBuilder builder = driver.newContainerBuilder();
        HealthMonitorConfig healthMonitorConfig =
                new HealthMonitorConfig(
                        new HealthMonitorConfig.Builder()
                                .snapshot_interval(TimeUnit.MILLISECONDS.toSeconds(SNAPSHOT_INTERVAL)));
        ThreadFactory threadFactory = ignored -> mock(Thread.class);
        this.monitor = new StateMonitor(healthMonitorConfig, timer, threadFactory);
        builder.guiceModules().install(new AbstractModule() {

            @Override
            protected void configure() {
                bind(StateMonitor.class).toInstance(monitor);
                bind(MetricConsumer.class).toProvider(MetricConsumerProviders.wrap(monitor));
                bind(ApplicationMetadataConfig.class).toInstance(new ApplicationMetadataConfig(
                        new ApplicationMetadataConfig.Builder().generation(META_GENERATION)));
                bind(MetricsPresentationConfig.class)
                        .toInstance(new MetricsPresentationConfig(new MetricsPresentationConfig.Builder()));
            }
        });
        builder.serverBindings().bind("http://*/*", builder.getInstance(StateHandler.class));
        driver.activateContainer(builder);
        metric = builder.getInstance(Metric.class);
    }

    @After
    public void stopTestDriver() {
        assertTrue(driver.close());
    }

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
        incrementCurrentTime(SNAPSHOT_INTERVAL);
        JsonNode json1 = requestAsJson("http://localhost/state/v1/metrics");
        assertEquals(json1.toString(), "up", json1.get("status").get("code").asText());
        assertEquals(json1.toString(), 2, json1.get("metrics").get("values").size());

        metric.add("fuz", 1, metric.createContext(new HashMap<>(0)));
        incrementCurrentTime(SNAPSHOT_INTERVAL);
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
            incrementCurrentTime(SNAPSHOT_INTERVAL);
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
            incrementCurrentTime(SNAPSHOT_INTERVAL);
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
        incrementCurrentTime(SNAPSHOT_INTERVAL);
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
        incrementCurrentTime(SNAPSHOT_INTERVAL);
        JsonNode json = requestAsJson("http://localhost/state/v1/all");
        assertEquals(json.toString(), "up", json.get("status").get("code").asText());
        assertEquals(json.toString(), 1, json.get("metrics").get("values").size());
        assertEquals(json.toString(), 5,
                     json.get("metrics").get("values").get(0).get("values").get("count").asDouble(), 0.001);
    }

    @Test
    public void testReadabilityOfJsonReport() throws Exception {
        metric.add("foo", 1, null);
        incrementCurrentTime(SNAPSHOT_INTERVAL);
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
        incrementCurrentTime(SNAPSHOT_INTERVAL);
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
                     "                    \"rate\": 0.013333333333333334\n" +
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
        incrementCurrentTime(SNAPSHOT_INTERVAL);
        metric.add("foo", 2, null);
        metric.add("foo", 1, null);
        incrementCurrentTime(SNAPSHOT_INTERVAL);
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
        incrementCurrentTime(SNAPSHOT_INTERVAL - 1);
        {
            JsonNode json = requestAsJson("http://localhost/state/v1/all");
            assertFalse(json.toString(), json.get("metrics").has("snapshot"));
        }
        // At this time first snapshot should have been generated
        incrementCurrentTime(1);
        {
            JsonNode json = requestAsJson("http://localhost/state/v1/all");
            assertTrue(json.toString(), json.get("metrics").has("snapshot"));
            assertEquals(0.0, json.get("metrics").get("snapshot").get("from").asDouble(), 0.00001);
            assertEquals(300.0, json.get("metrics").get("snapshot").get("to").asDouble(), 0.00001);
        }
        // No new snapshot at this time
        incrementCurrentTime(SNAPSHOT_INTERVAL - 1);
        {
            JsonNode json = requestAsJson("http://localhost/state/v1/all");
            assertTrue(json.toString(), json.get("metrics").has("snapshot"));
            assertEquals(0.0, json.get("metrics").get("snapshot").get("from").asDouble(), 0.00001);
            assertEquals(300.0, json.get("metrics").get("snapshot").get("to").asDouble(), 0.00001);
        }
        // A new snapshot
        incrementCurrentTime(1);
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
        incrementCurrentTime(SNAPSHOT_INTERVAL);
        metric.set("bar", 4, null);
        metric.set("bar", 2, null);
        incrementCurrentTime(SNAPSHOT_INTERVAL);
        JsonNode json = requestAsJson("http://localhost/state/v1/all");
        assertEquals(json.toString(), "up", json.get("status").get("code").asText());
        assertEquals(json.toString(), 1, json.get("metrics").get("values").size());
        assertEquals(json.toString(), 3,
                     json.get("metrics").get("values").get(0).get("values").get("average").asDouble(), 0.001);
    }

    @Test
    public void snapshotsPreserveLastGaugeValue() throws Exception {
        metric.set("bar", 4, null);
        incrementCurrentTime(SNAPSHOT_INTERVAL);
        incrementCurrentTime(SNAPSHOT_INTERVAL);
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
        incrementCurrentTime(SNAPSHOT_INTERVAL);
        metric.set("bar", 20, null);
        metric.set("bar", 40, null);
        incrementCurrentTime(SNAPSHOT_INTERVAL);
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
        incrementCurrentTime(SNAPSHOT_INTERVAL);
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

    private void incrementCurrentTime(long val) {
        currentTimeMillis.addAndGet(val);
        monitor.checkTime();
    }

    private String requestAsString(String requestUri) throws Exception {
        final BufferedContentChannel content = new BufferedContentChannel();
        Response response = driver.dispatchRequest(requestUri, new ResponseHandler() {

            @Override
            public ContentChannel handleResponse(Response response) {
                return content;
            }
        }).get(60, TimeUnit.SECONDS);
        assertNotNull(response);
        assertEquals(Response.Status.OK, response.getStatus());
        StringBuilder str = new StringBuilder();
        Reader in = new InputStreamReader(content.toStream(), StandardCharsets.UTF_8);
        for (int c; (c = in.read()) != -1; ) {
            str.append((char)c);
        }
        return str.toString();
    }

    private JsonNode requestAsJson(String requestUri) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(mapper.getFactory().createParser(requestAsString(requestUri)));
    }
}
