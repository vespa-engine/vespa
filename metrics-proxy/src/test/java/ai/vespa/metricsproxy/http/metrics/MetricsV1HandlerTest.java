// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.metrics;

import ai.vespa.metricsproxy.http.HttpHandlerTestBase;
import ai.vespa.metricsproxy.metric.model.json.GenericJsonModel;
import ai.vespa.metricsproxy.metric.model.json.GenericMetrics;
import ai.vespa.metricsproxy.metric.model.json.GenericService;
import ai.vespa.metricsproxy.service.DownService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.Executors;

import static ai.vespa.metricsproxy.core.VespaMetrics.INSTANCE_DIMENSION_ID;
import static ai.vespa.metricsproxy.http.metrics.MetricsV1Handler.V1_PATH;
import static ai.vespa.metricsproxy.http.metrics.MetricsV1Handler.VALUES_PATH;
import static ai.vespa.metricsproxy.metric.model.StatusCode.DOWN;
import static ai.vespa.metricsproxy.metric.model.json.JacksonUtil.createObjectMapper;
import static ai.vespa.metricsproxy.service.DummyService.METRIC_1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author gjoranv
 */
@SuppressWarnings("UnstableApiUsage")
public class MetricsV1HandlerTest extends HttpHandlerTestBase {

    private static final String V1_URI = URI_BASE + V1_PATH;
    private static final String VALUES_URI = URI_BASE + VALUES_PATH;

    @BeforeClass
    public static void setup() {
        MetricsV1Handler handler = new MetricsV1Handler(Executors.newSingleThreadExecutor(),
                                                        getMetricsManager(),
                                                        vespaServices,
                                                        getMetricsConsumers());
        testDriver = new RequestHandlerTestDriver(handler);
    }

    private GenericJsonModel getResponseAsJsonModel(String consumer) {
        String response = testDriver.sendRequest(VALUES_URI + "?consumer=" + consumer).readAll();
        try {
            return createObjectMapper().readValue(response, GenericJsonModel.class);
        } catch (IOException e) {
            fail("Failed to create json model: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Test
    public void v1_response_contains_values_uri() throws Exception {
        String response = testDriver.sendRequest(V1_URI).readAll();
        JSONObject root = new JSONObject(response);
        assertTrue(root.has("resources"));

        JSONArray resources = root.getJSONArray("resources");
        assertEquals(1, resources.length());

        JSONObject valuesUrl = resources.getJSONObject(0);
        assertEquals(VALUES_URI, valuesUrl.getString("url"));
    }

    @Ignore
    @Test
    public void visually_inspect_values_response() throws Exception {
        String response = testDriver.sendRequest(VALUES_URI).readAll();
        ObjectMapper mapper = createObjectMapper();
        var jsonModel = mapper.readValue(response, GenericJsonModel.class);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonModel));
    }

    @Test
    public void no_explicit_consumer_gives_the_default_consumer() {
        String responseDefaultConsumer = testDriver.sendRequest(VALUES_URI + "?consumer=default").readAll();
        String responseNoConsumer = testDriver.sendRequest(VALUES_URI).readAll();
        assertEqualsExceptTimestamps(responseDefaultConsumer, responseNoConsumer);
    }

    @Test
    public void unknown_consumer_gives_the_default_consumer() {
        String response = testDriver.sendRequest(VALUES_URI).readAll();
        String responseUnknownConsumer = testDriver.sendRequest(VALUES_URI + "?consumer=not_defined").readAll();
        assertEqualsExceptTimestamps(response, responseUnknownConsumer);
    }

    private void assertEqualsExceptTimestamps(String s1, String s2) {
        assertEquals(replaceTimestamps(s1), replaceTimestamps(s2));
    }

    @Test
    public void response_contains_node_metrics() {
        GenericJsonModel jsonModel = getResponseAsJsonModel(DEFAULT_CONSUMER);

        assertNotNull(jsonModel.node);
        assertEquals(1, jsonModel.node.metrics.size());
        assertEquals(12.345, jsonModel.node.metrics.get(0).values.get(CPU_METRIC), 0.0001d);
    }

    @Test
    public void response_contains_service_metrics() {
        GenericJsonModel jsonModel = getResponseAsJsonModel(DEFAULT_CONSUMER);

        assertEquals(2, jsonModel.services.size());
        GenericService dummyService = jsonModel.services.get(0);
        assertEquals(2, dummyService.metrics.size());

        GenericMetrics dummy0Metrics = getMetricsForInstance("dummy0", dummyService);
        assertEquals(1L, dummy0Metrics.values.get(METRIC_1).longValue());
        assertEquals("default-val", dummy0Metrics.dimensions.get("consumer-dim"));

        GenericMetrics dummy1Metrics = getMetricsForInstance("dummy1", dummyService);
        assertEquals(6L, dummy1Metrics.values.get(METRIC_1).longValue());
        assertEquals("default-val", dummy1Metrics.dimensions.get("consumer-dim"));
    }

    @Test
    public void all_consumers_get_health_from_service_that_is_down() {
        assertDownServiceHealth(DEFAULT_CONSUMER);
        assertDownServiceHealth(CUSTOM_CONSUMER);
    }

    @Test
    public void all_timestamps_are_equal_and_non_zero() {
        GenericJsonModel jsonModel = getResponseAsJsonModel(DEFAULT_CONSUMER);

        Long nodeTimestamp = jsonModel.node.timestamp;
        assertNotEquals(0L, (long) nodeTimestamp);
        for (var service : jsonModel.services)
            assertEquals(nodeTimestamp, service.timestamp);
    }

    @Test
    public void custom_consumer_gets_only_its_whitelisted_metrics() {
        GenericJsonModel jsonModel = getResponseAsJsonModel(CUSTOM_CONSUMER);

        assertNotNull(jsonModel.node);
        // TODO: see comment in ExternalMetrics.setExtraMetrics
        // assertEquals(0, jsonModel.node.metrics.size());

        assertEquals(2, jsonModel.services.size());
        GenericService dummyService = jsonModel.services.get(0);
        assertEquals(2, dummyService.metrics.size());

        GenericMetrics dummy0Metrics = getMetricsForInstance("dummy0", dummyService);
        assertEquals("custom-val", dummy0Metrics.dimensions.get("consumer-dim"));

        GenericMetrics dummy1Metrics = getMetricsForInstance("dummy1", dummyService);
        assertEquals("custom-val", dummy1Metrics.dimensions.get("consumer-dim"));
    }

    @Test
    public void invalid_path_yields_error_response() throws Exception {
        String response = testDriver.sendRequest(V1_URI + "/invalid").readAll();
        JSONObject root = new JSONObject(response);
        assertTrue(root.has("error"));
    }

    private void assertDownServiceHealth(String consumer) {
        GenericJsonModel jsonModel = getResponseAsJsonModel(consumer);

        GenericService downService = jsonModel.services.get(1);
        assertEquals(DOWN.status, downService.status.code);
        assertEquals("No response", downService.status.description);

        // Service should output metric dimensions, even without metrics, because they contain important info about the service.
        assertEquals(1, downService.metrics.size());
        assertEquals(0, downService.metrics.get(0).values.size());
        assertFalse(downService.metrics.get(0).dimensions.isEmpty());
        assertEquals(DownService.NAME, downService.metrics.get(0).dimensions.get(INSTANCE_DIMENSION_ID.id));
    }

    private String replaceTimestamps(String s) {
        return s.replaceAll("timestamp\":\\d+,", "timestamp\":1,");
    }

    private static GenericMetrics getMetricsForInstance(String instance, GenericService service) {
        for (var metrics : service.metrics) {
            if (metrics.dimensions.get(INSTANCE_DIMENSION_ID.id).equals(instance))
                return metrics;
        }
        fail("Could not find metrics for service instance " + instance);
        throw new RuntimeException();
    }

}
