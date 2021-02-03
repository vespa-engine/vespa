// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static ai.vespa.metricsproxy.TestUtil.getFileContents;
import static ai.vespa.metricsproxy.core.VespaMetrics.vespaMetricsConsumerId;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.rpc.IntegrationTester.CUSTOM_CONSUMER_ID;
import static ai.vespa.metricsproxy.rpc.IntegrationTester.MONITORING_SYSTEM;
import static ai.vespa.metricsproxy.rpc.IntegrationTester.SERVICE_1_CONFIG_ID;
import static ai.vespa.metricsproxy.rpc.IntegrationTester.SERVICE_2_CONFIG_ID;
import static ai.vespa.metricsproxy.service.VespaServices.ALL_SERVICES;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author jobergum
 * @author gjoranv
 */
public class RpcMetricsTest {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private static final String METRICS_RESPONSE = getFileContents("metrics-storage-simple.json").trim();
    private static final String EXTRA_APP = "extra";

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

    @Test
    public void extra_metrics_are_added_to_output() throws Exception {
        try (IntegrationTester tester = new IntegrationTester()) {
            try (RpcClient rpcClient = new RpcClient(tester.rpcPort())) {
                setExtraMetrics(rpcClient);
                String allServicesResponse = getMetricsForYamas(ALL_SERVICES, rpcClient).trim();

                // Verify that application is used as serviceId, and that metric exists.
                JsonNode extraMetrics = findExtraMetricsObject(allServicesResponse);
                assertThat(extraMetrics.get("metrics").get("foo.count").intValue(), is(3));
                assertThat(extraMetrics.get("dimensions").get("role").textValue(), is("extra-role"));
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

            assertThat("#Services should be 1 for config id " + SERVICE_1_CONFIG_ID, services.size(), is(1));

            VespaService qrserver = services.get(0);
            assertThat(qrserver.getMonitoringName(), is(MONITORING_SYSTEM + VespaService.SEPARATOR + "qrserver"));

            Metrics metrics = qrserver.getMetrics();
            assertThat("Fetched number of metrics is not correct", metrics.size(), is(2));
            Metric m = metrics.getMetric("foo.count");
            assertNotNull("Did not find expected metric with name 'foo.count'", m);
            Metric m2 = metrics.getMetric("bar.count");
            assertNotNull("Did not find expected metric with name 'bar.count'", m2);

            try (RpcClient rpcClient = new RpcClient(tester.rpcPort())) {
                verifyMetricsFromRpcRequest(qrserver, rpcClient);

                services = tester.vespaServices().getInstancesById(SERVICE_2_CONFIG_ID);
                assertThat("#Services should be 1 for config id " + SERVICE_2_CONFIG_ID, services.size(), is(1));

                VespaService storageService = services.get(0);
                verfiyMetricsFromServiceObject(storageService);

                String metricsById = getMetricsById(storageService.getConfigId(), rpcClient);
                assertThat(metricsById, is("'storage.cluster.storage.storage.0'.foo_count=1 "));

                String jsonResponse = getMetricsForYamas("non-existing", rpcClient).trim();
                assertThat(jsonResponse, is("105: No service with name 'non-existing'"));

                verifyMetricsFromRpcRequestForAllServices(rpcClient);

            }
        }
    }

    private static void verifyMetricsFromRpcRequest(VespaService service, RpcClient client) throws IOException {
        String jsonResponse = getMetricsForYamas(service.getMonitoringName(), client).trim();
        ArrayNode metrics = (ArrayNode) jsonMapper.readTree(jsonResponse).get("metrics");
        assertThat("Expected 3 metric messages", metrics.size(), is(3));
        for (int i = 0; i < metrics.size() - 1; i++) { // The last "metric message" contains only status code/message
            JsonNode jsonObject = metrics.get(i);
            assertFalse(jsonObject.has("status_code"));
            assertFalse(jsonObject.has("status_msg"));
            assertThat(jsonObject.get("dimensions").get("foo").textValue(), is("bar"));
            assertThat(jsonObject.get("dimensions").get("bar").textValue(), is("foo"));
            assertThat(jsonObject.get("dimensions").get("serviceDim").textValue(), is("serviceDimValue"));
            assertThat(jsonObject.get("routing").get("yamas").get("namespaces").size(), is(1));
            if (jsonObject.get("metrics").has("foo_count")) {
                assertThat(jsonObject.get("metrics").get("foo_count").intValue(), is(1));
                assertThat(jsonObject.get("routing").get("yamas").get("namespaces").get(0).textValue(), is(vespaMetricsConsumerId.id));
            } else {
                assertThat(jsonObject.get("metrics").get("foo.count").intValue(), is(1));
                assertThat(jsonObject.get("routing").get("yamas").get("namespaces").get(0).textValue(), is(CUSTOM_CONSUMER_ID.id));
            }
        }

        verifyStatusMessage(metrics.get(metrics.size() - 1));
    }

    private void verfiyMetricsFromServiceObject(VespaService service) {
        Metrics storageMetrics = service.getMetrics();
        assertThat(storageMetrics.size(), is(2));
        Metric foo = storageMetrics.getMetric("foo.count");
        assertNotNull("Did not find expected metric with name 'foo.count'", foo);
        assertThat("Expected 2 dimensions for metric foo", foo.getDimensions().size(), is(2));
        assertThat("Metric foo did not contain correct dimension mapping for key = foo.count", foo.getDimensions().containsKey(toDimensionId("foo")), is(true));
        assertThat("Metric foo did not contain correct dimension", foo.getDimensions().get(toDimensionId("foo")), is("bar"));
        assertThat("Metric foo did not contain correct dimension", foo.getDimensions().containsKey(toDimensionId("bar")), is(true));
        assertThat("Metric foo did not contain correct dimension for key = bar", foo.getDimensions().get(toDimensionId("bar")), is("foo"));
    }

    private void verifyMetricsFromRpcRequestForAllServices(RpcClient client) throws IOException {
        // Verify that metrics for all services can be retrieved in one request.
        String allServicesResponse = getMetricsForYamas(ALL_SERVICES, client).trim();
        ArrayNode allServicesMetrics = (ArrayNode) jsonMapper.readTree(allServicesResponse).get("metrics");
        assertThat(allServicesMetrics.size(), is(5));
    }

    @Test
    public void testGetAllMetricNames() {
        try (IntegrationTester tester = new IntegrationTester()) {

            tester.httpServer().setResponse(METRICS_RESPONSE);
            List<VespaService> services = tester.vespaServices().getInstancesById(SERVICE_1_CONFIG_ID);

            assertThat(services.size(), is(1));
            Metrics metrics = services.get(0).getMetrics();
            assertThat("Fetched number of metrics is not correct", metrics.size(), is(2));
            Metric m = metrics.getMetric("foo.count");
            assertNotNull("Did not find expected metric with name 'foo.count'", m);

            Metric m2 = metrics.getMetric("bar.count");
            assertNotNull("Did not find expected metric with name 'bar'", m2);

            try (RpcClient rpcClient = new RpcClient(tester.rpcPort())) {
                String response = getAllMetricNamesForService(services.get(0).getMonitoringName(), vespaMetricsConsumerId, rpcClient);
                assertThat(response, is("foo.count=ON;output-name=foo_count,bar.count=OFF,"));
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
        client.target.invokeSync(req, 20.0);
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
        assertThat(jsonObject.get("status_code").intValue(), is(0));
        assertThat(jsonObject.get("status_msg").textValue(), notNullValue());
        assertThat(jsonObject.get("application").textValue(), notNullValue());
        assertThat(jsonObject.get("routing"), notNullValue());
        assertThat(jsonObject.size(), is(4));
    }

}
