// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.rpc;

import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.service.VespaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static ai.vespa.metricsproxy.TestUtil.getFileContents;
import static ai.vespa.metricsproxy.core.VespaMetrics.vespaMetricsConsumerId;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static ai.vespa.metricsproxy.rpc.IntegrationTester.CUSTOM_CONSUMER_ID;
import static ai.vespa.metricsproxy.rpc.IntegrationTester.MONITORING_SYSTEM;
import static ai.vespa.metricsproxy.rpc.IntegrationTester.SERVICE_1_CONFIG_ID;
import static ai.vespa.metricsproxy.rpc.IntegrationTester.SERVICE_2_CONFIG_ID;
import static ai.vespa.metricsproxy.service.VespaServices.ALL_SERVICES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author jobergum
 * @author gjoranv
 */
public class RpcMetricsTest {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private static final String METRICS_RESPONSE = getFileContents("metrics-storage-simple.json").trim();
    private static final String EXTRA_APP = "extra";
    private static final Duration RPC_INVOKE_TIMEOUT = Duration.ofSeconds(60);

    private static class RpcClient implements AutoCloseable {
        private final Supervisor supervisor;
        private final Target target;

        RpcClient(int port) {
            supervisor = new Supervisor(new Transport());
            target = supervisor.connect(new Spec("localhost", port));
        }

        @Override
        public void close() {
            target.close();
            supervisor.transport().shutdown().join();
        }
    }

    @Rule
    public Timeout globalTimeout = Timeout.seconds(300);

    @Test
    public void extra_metrics_are_added_to_output() throws Exception {
        try (IntegrationTester tester = new IntegrationTester()) {
            try (RpcClient rpcClient = new RpcClient(tester.rpcPort())) {
                setExtraMetrics(rpcClient);
                String allServicesResponse = getMetricsForYamas(ALL_SERVICES, rpcClient).trim();

                // Verify that application is used as serviceId, and that metric exists.
                JsonNode extraMetrics = findExtraMetricsObject(allServicesResponse);
                assertEquals(3, extraMetrics.get("metrics").get("foo.count").intValue());
                assertEquals("extra-role", extraMetrics.get("dimensions").get("role").textValue());
            }
        }
    }

    @Test
    public void extra_metrics_are_purged() throws Exception {
        try (IntegrationTester tester = new IntegrationTester()) {
            try (RpcClient rpcClient = new RpcClient(tester.rpcPort())) {
                setExtraMetrics(rpcClient);

                Request req = new Request("purgeExtraMetrics");
                invoke(req, rpcClient, false);

                // Verify that no extra metrics exists
                String allServicesResponse = getMetricsForYamas(ALL_SERVICES, rpcClient).trim();
                JsonNode extraMetrics = findExtraMetricsObject(allServicesResponse);
                assertEquals(extraMetrics.toString(), "{}");
            }
        }
    }

    @Test
    public void testGetMetrics() throws Exception {
        try (IntegrationTester tester = new IntegrationTester()) {
            tester.httpServer().setResponse(METRICS_RESPONSE);
            List<VespaService> services = tester.vespaServices().getInstancesById(SERVICE_1_CONFIG_ID);

            assertEquals("#Services should be 1 for config id " + SERVICE_1_CONFIG_ID, 1, services.size());

            VespaService container = services.get(0);
            assertEquals(MONITORING_SYSTEM + VespaService.SEPARATOR + "container", container.getMonitoringName().id);

            Metrics metrics = container.getMetrics();
            assertEquals("Fetched number of metrics is not correct", 2, metrics.size());
            Metric m = getMetric("foo.count", metrics);
            assertNotNull("Did not find expected metric with name 'foo.count'", m);
            Metric m2 = getMetric("bar.count", metrics);
            assertNotNull("Did not find expected metric with name 'bar.count'", m2);

            try (RpcClient rpcClient = new RpcClient(tester.rpcPort())) {
                verifyMetricsFromRpcRequest(container, rpcClient);

                services = tester.vespaServices().getInstancesById(SERVICE_2_CONFIG_ID);
                assertEquals("#Services should be 1 for config id " + SERVICE_2_CONFIG_ID, 1, services.size());

                VespaService storageService = services.get(0);
                verfiyMetricsFromServiceObject(storageService);

                String metricsById = getMetricsById(storageService.getConfigId(), rpcClient);
                assertEquals("'storage.cluster.storage.storage.0'.foo_count=1 ", metricsById);

                String jsonResponse = getMetricsForYamas("non-existing", rpcClient).trim();
                assertEquals("105: No service with name 'non-existing'", jsonResponse);

                verifyMetricsFromRpcRequestForAllServices(rpcClient);

            }
        }
    }

    public Metric getMetric(String metric, Metrics metrics) {
        for (Metric m: metrics.list()) {
            if (m.getName().equals(toMetricId(metric)))
                return m;
        }
        return null;
    }

    private static void verifyMetricsFromRpcRequest(VespaService service, RpcClient client) throws IOException {
        String jsonResponse = getMetricsForYamas(service.getMonitoringName().id, client).trim();
        ArrayNode metrics = (ArrayNode) jsonMapper.readTree(jsonResponse).get("metrics");
        assertEquals("Expected 3 metric messages", 3, metrics.size());
        for (int i = 0; i < metrics.size() - 1; i++) { // The last "metric message" contains only status code/message
            JsonNode jsonObject = metrics.get(i);
            assertFalse(jsonObject.has("status_code"));
            assertFalse(jsonObject.has("status_msg"));
            assertEquals("bar", jsonObject.get("dimensions").get("foo").textValue());
            assertEquals("foo", jsonObject.get("dimensions").get("bar").textValue());
            assertEquals("serviceDimValue", jsonObject.get("dimensions").get("serviceDim").textValue());
            assertEquals(1, jsonObject.get("routing").get("yamas").get("namespaces").size());
            if (jsonObject.get("metrics").has("foo_count")) {
                assertEquals(1, jsonObject.get("metrics").get("foo_count").intValue());
                assertEquals(vespaMetricsConsumerId.id, jsonObject.get("routing").get("yamas").get("namespaces").get(0).textValue());
            } else {
                assertEquals(1, jsonObject.get("metrics").get("foo.count").intValue());
                assertEquals(CUSTOM_CONSUMER_ID.id, jsonObject.get("routing").get("yamas").get("namespaces").get(0).textValue());
            }
        }

        verifyStatusMessage(metrics.get(metrics.size() - 1));
    }

    private void verfiyMetricsFromServiceObject(VespaService service) {
        Metrics storageMetrics = service.getMetrics();
        assertEquals(2, storageMetrics.size());
        Metric foo = getMetric("foo.count", storageMetrics);
        assertNotNull("Did not find expected metric with name 'foo.count'", foo);
        assertEquals("Expected 2 dimensions for metric foo", 2, foo.getDimensions().size());
        assertTrue("Metric foo did not contain correct dimension mapping for key = foo.count", foo.getDimensions().containsKey(toDimensionId("foo")));
        assertEquals("Metric foo did not contain correct dimension", "bar", foo.getDimensions().get(toDimensionId("foo")));
        assertTrue("Metric foo did not contain correct dimension", foo.getDimensions().containsKey(toDimensionId("bar")));
        assertEquals("Metric foo did not contain correct dimension for key = bar", "foo", foo.getDimensions().get(toDimensionId("bar")));
    }

    private void verifyMetricsFromRpcRequestForAllServices(RpcClient client) throws IOException {
        // Verify that metrics for all services can be retrieved in one request.
        String allServicesResponse = getMetricsForYamas(ALL_SERVICES, client).trim();
        ArrayNode allServicesMetrics = (ArrayNode) jsonMapper.readTree(allServicesResponse).get("metrics");
        assertEquals(5, allServicesMetrics.size());
    }

    @Test
    public void testGetAllMetricNames() {
        try (IntegrationTester tester = new IntegrationTester()) {

            tester.httpServer().setResponse(METRICS_RESPONSE);
            List<VespaService> services = tester.vespaServices().getInstancesById(SERVICE_1_CONFIG_ID);

            assertEquals(1, services.size());
            Metrics metrics = services.get(0).getMetrics();
            assertEquals("Fetched number of metrics is not correct", 2, metrics.size());
            Metric m = getMetric("foo.count", metrics);
            assertNotNull("Did not find expected metric with name 'foo.count'", m);

            Metric m2 = getMetric("bar.count", metrics);
            assertNotNull("Did not find expected metric with name 'bar'", m2);

            try (RpcClient rpcClient = new RpcClient(tester.rpcPort())) {
                String response = getAllMetricNamesForService(services.get(0).getMonitoringName().id, vespaMetricsConsumerId, rpcClient);
                assertEquals("foo.count=ON;output-name=foo_count,bar.count=OFF,", response);
            }
        }
    }

    private void setExtraMetrics(RpcClient rpcClient) {
        String extraMetricsPayload = "{\"timestamp\":1557754772,\"application\":\"" + EXTRA_APP +
                "\",\"metrics\":{\"foo.count\":3},\"dimensions\":{\"role\":\"extra-role\"}}";

        Request req = new Request("setExtraMetrics");
        req.parameters().add(new StringValue(extraMetricsPayload));
        invoke(req, rpcClient, false);
    }

    private JsonNode findExtraMetricsObject(String jsonResponse) throws IOException {
        ArrayNode metrics = (ArrayNode) jsonMapper.readTree(jsonResponse).get("metrics");
        for (int i = 0; i <  metrics.size(); i++) {
            JsonNode jsonObject = metrics.get(i);
            assertTrue(jsonObject.has("application"));
            if (jsonObject.get("application").textValue().equals(EXTRA_APP)) return jsonObject;
        }
        return jsonMapper.createObjectNode();
    }

    private static String getMetricsForYamas(String service, RpcClient client) {
        Request req = new Request("getMetricsForYamas");
        req.parameters().add(new StringValue(service));
        return invoke(req, client, true);
    }

    private String getMetricsById(String service, RpcClient client) {
        Request req = new Request("getMetricsById");
        req.parameters().add(new StringValue(service));
        return invoke(req, client, true);
    }

    private String getAllMetricNamesForService(String service, ConsumerId consumer, RpcClient client) {
        Request req = new Request("getAllMetricNamesForService");
        req.parameters().add(new StringValue(service));
        req.parameters().add(new StringValue(consumer.id));
        return invoke(req, client, true);
    }

    private static String invoke(Request req, RpcClient client, boolean expectReturnValue) {
        String returnValue;
        client.target.invokeSync(req, RPC_INVOKE_TIMEOUT);
        if (req.checkReturnTypes("s")) {
            returnValue = req.returnValues().get(0).asString();
        } else if (expectReturnValue) {
            System.out.println(req.methodName() + " from rpcserver - Invocation failed "
                                       + req.errorCode() + ": " + req.errorMessage());
            returnValue = req.errorCode() + ": " + req.errorMessage();
        }
        else {
            return "";
        }
        return returnValue;
    }

    private static void verifyStatusMessage(JsonNode jsonObject) {
        assertEquals(0, jsonObject.get("status_code").intValue());
        assertNotNull(jsonObject.get("status_msg"));
        assertNotNull(jsonObject.get("application"));
        assertNotNull(jsonObject.get("routing"));
        assertEquals(4, jsonObject.size());
    }

}
