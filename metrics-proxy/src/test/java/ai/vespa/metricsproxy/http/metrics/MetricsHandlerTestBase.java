// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.metrics;

import ai.vespa.metricsproxy.http.HttpHandlerTestBase;
import ai.vespa.metricsproxy.metric.model.json.GenericJsonModel;
import ai.vespa.metricsproxy.metric.model.json.GenericMetrics;
import ai.vespa.metricsproxy.metric.model.json.GenericService;
import ai.vespa.metricsproxy.service.DownService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.INTERNAL_SERVICE_ID;
import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.REASON;
import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.SERVICE_ID;
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
public abstract class MetricsHandlerTestBase<MODEL> extends HttpHandlerTestBase {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    static String rootUri;
    static String valuesUri;

    Class<MODEL> modelClass;

    abstract GenericJsonModel getGenericJsonModel(MODEL model);

    private MODEL getResponseAsJsonModel(String consumer) {
        String response = testDriver.sendRequest(valuesUri + "?consumer=" + consumer).readAll();
        try {
            return createObjectMapper().readValue(response, modelClass);
        } catch (IOException e) {
            fail("Failed to create json model: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private GenericJsonModel getResponseAsGenericJsonModel(String consumer) {
        return getGenericJsonModel(getResponseAsJsonModel(consumer));
    }

    @Test
    public void invalid_path_yields_error_response() throws Exception {
        String response = testDriver.sendRequest(rootUri + "/invalid").readAll();
        JsonNode root = jsonMapper.readTree(response);
        assertTrue(root.has("error"));
    }

    @Test
    public void root_response_contains_values_uri() throws Exception {
        String response = testDriver.sendRequest(rootUri).readAll();
        JsonNode root = jsonMapper.readTree(response);
        assertTrue(root.has("resources"));

        ArrayNode resources = (ArrayNode) root.get("resources");
        assertEquals(1, resources.size());

        JsonNode valuesUrl = resources.get(0);
        assertEquals(valuesUri, valuesUrl.get("url").textValue());
    }

    @Ignore
    @Test
    public void visually_inspect_values_response() throws Exception {
        String response = testDriver.sendRequest(valuesUri).readAll();
        ObjectMapper mapper = createObjectMapper();
        var jsonModel = mapper.readValue(response, modelClass);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonModel));
    }

    @Test
    public void no_explicit_consumer_gives_the_default_consumer() {
        String responseDefaultConsumer = testDriver.sendRequest(valuesUri + "?consumer=default").readAll();
        String responseNoConsumer = testDriver.sendRequest(valuesUri).readAll();
        assertEqualsExceptTimestamps(responseDefaultConsumer, responseNoConsumer);
    }

    @Test
    public void unknown_consumer_gives_the_default_consumer() {
        String response = testDriver.sendRequest(valuesUri).readAll();
        String responseUnknownConsumer = testDriver.sendRequest(valuesUri + "?consumer=not_defined").readAll();
        assertEqualsExceptTimestamps(response, responseUnknownConsumer);
    }

    private void assertEqualsExceptTimestamps(String s1, String s2) {
        assertEquals(replaceTimestamps(s1), replaceTimestamps(s2));
    }

    private String replaceTimestamps(String s) {
        return s.replaceAll("timestamp\":\\d+,", "timestamp\":1,");
    }

    @Test
    public void response_contains_node_metrics() {
        GenericJsonModel jsonModel = getResponseAsGenericJsonModel(DEFAULT_CONSUMER);

        assertNotNull(jsonModel.node);
        assertEquals(1, jsonModel.node.metrics.size());
        assertEquals(12.345, jsonModel.node.metrics.get(0).values.get(CPU_METRIC), 0.0001d);
    }

    @Test
    public void response_contains_service_metrics() {
        GenericJsonModel jsonModel = getResponseAsGenericJsonModel(DEFAULT_CONSUMER);

        assertEquals(2, jsonModel.services.size());
        GenericService dummyService = jsonModel.services.get(0);
        assertEquals(2, dummyService.metrics.size());

        GenericMetrics dummy0Metrics = getMetricsForService("dummy0", dummyService);
        assertEquals(1L, dummy0Metrics.values.get(METRIC_1).longValue());
        assertEquals("default-val", dummy0Metrics.dimensions.get(REASON));

        GenericMetrics dummy1Metrics = getMetricsForService("dummy1", dummyService);
        assertEquals(6L, dummy1Metrics.values.get(METRIC_1).longValue());
        assertEquals("default-val", dummy1Metrics.dimensions.get(REASON));
    }

    @Test
    public void custom_consumer_gets_only_its_whitelisted_metrics() {
        GenericJsonModel jsonModel = getResponseAsGenericJsonModel(CUSTOM_CONSUMER);

        assertNotNull(jsonModel.node);
        // TODO: see comment in ExternalMetrics.setExtraMetrics
        // assertEquals(0, jsonModel.node.metrics.size());

        assertEquals(2, jsonModel.services.size());
        GenericService dummyService = jsonModel.services.get(0);
        assertEquals(2, dummyService.metrics.size());

        GenericMetrics dummy0Metrics = getMetricsForService("dummy0", dummyService);
        assertEquals("custom-val", dummy0Metrics.dimensions.get(REASON));

        GenericMetrics dummy1Metrics = getMetricsForService("dummy1", dummyService);
        assertEquals("custom-val", dummy1Metrics.dimensions.get(REASON));
    }

    private static GenericMetrics getMetricsForService(String serviceInstance, GenericService service) {
        for (var metrics : service.metrics) {
            if (getServiceIdDimension(metrics).equals(serviceInstance))
                return metrics;
        }
        fail("Could not find metrics for service instance " + serviceInstance);
        throw new RuntimeException();
    }

    @Test
    public void all_timestamps_are_equal_and_non_zero() {
        GenericJsonModel jsonModel = getResponseAsGenericJsonModel(DEFAULT_CONSUMER);

        Long nodeTimestamp = jsonModel.node.timestamp;
        assertNotEquals(0L, (long) nodeTimestamp);
        for (var service : jsonModel.services)
            assertEquals(nodeTimestamp, service.timestamp);
    }

    @Test
    public void all_consumers_get_health_from_service_that_is_down() {
        assertDownServiceHealth(DEFAULT_CONSUMER);
        assertDownServiceHealth(CUSTOM_CONSUMER);
    }

    private void assertDownServiceHealth(String consumer) {
        GenericJsonModel jsonModel = getResponseAsGenericJsonModel(consumer);

        GenericService downService = jsonModel.services.get(1);
        assertEquals(DOWN.status, downService.status.code);
        assertEquals("No response", downService.status.description);

        // Service should output metric dimensions, even without metrics, because they contain important info about the service.
        assertEquals(1, downService.metrics.size());
        assertEquals(0, downService.metrics.get(0).values.size());
        assertFalse(downService.metrics.get(0).dimensions.isEmpty());
        assertEquals(DownService.NAME, getServiceIdDimension(downService.metrics.get(0)));
    }

    private static String getServiceIdDimension(GenericMetrics metrics) {
        var instanceDimension = metrics.dimensions.get(INTERNAL_SERVICE_ID);
        return instanceDimension != null ? instanceDimension : metrics.dimensions.get(SERVICE_ID);
    }

}
