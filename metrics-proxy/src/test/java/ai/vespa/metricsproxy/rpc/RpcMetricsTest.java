/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.rpc;

import ai.vespa.metricsproxy.TestUtil;
import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.service.VespaService;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static ai.vespa.metricsproxy.core.VespaMetrics.VESPA_CONSUMER_ID;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.rpc.IntegrationTester.CUSTOM_CONSUMER_ID;
import static ai.vespa.metricsproxy.rpc.IntegrationTester.MONITORING_SYSTEM;
import static ai.vespa.metricsproxy.rpc.IntegrationTester.root;
import static ai.vespa.metricsproxy.rpc.IntegrationTester.SERVICE_2_CONFIG_ID;
import static ai.vespa.metricsproxy.rpc.IntegrationTester.SERVICE_1_CONFIG_ID;
import static ai.vespa.metricsproxy.service.VespaServices.ALL_SERVICES;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author jobergum
 * @author gjoranv
 */
public class RpcMetricsTest {

    private static final String METRICS_RESPONSE_CCL =
            TestUtil.getContents(new File(root + "metrics-storage-simple.json")).trim();

    // see factory/doc/port-ranges.txt
    private static final int httpPort = 18633;
    private static final int rpcPort = 18634;

    @Test
    public void testGetMetrics() throws Exception {
        try (IntegrationTester tester = new IntegrationTester(httpPort, rpcPort)) {
            tester.httpServer().setResponse(METRICS_RESPONSE_CCL);
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

            // Setup RPC client
            Supervisor supervisor = new Supervisor(new Transport());
            Target target = supervisor.connect(new Spec("localhost", rpcPort));

            verifyMetricsFromRpcRequest(qrserver, target);

            services = tester.vespaServices().getInstancesById(SERVICE_2_CONFIG_ID);
            assertThat("#Services should be 1 for config id " + SERVICE_2_CONFIG_ID, services.size(), is(1));

            VespaService storageService = services.get(0);
            verfiyMetricsFromServiceObject(storageService);

            String metricsById = getMetricsById(storageService.getConfigId(), target);
            assertThat(metricsById, is("'storage.cluster.storage.storage.0'.foo_count=1 "));

            String jsonResponse = getMetricsForYamas("non-existing", target).trim();
            assertThat(jsonResponse, is("105: No service with name 'non-existing'"));

            verifyMetricsFromRpcRequestForAllServices(target);

            // Shutdown RPC
            target.close();
            supervisor.transport().shutdown().join();
        }
    }

    private static void verifyMetricsFromRpcRequest(VespaService service, Target target) throws JSONException {
        String jsonResponse = getMetricsForYamas(service.getMonitoringName(), target).trim();
        JSONArray metrics = new JSONObject(jsonResponse).getJSONArray("metrics");
        assertThat("Expected 3 metric messages", metrics.length(), is(3));
        for (int i = 0; i < metrics.length() - 1; i++) { // The last "metric message" contains only status code/message
            JSONObject jsonObject = metrics.getJSONObject(i);
            assertFalse(jsonObject.has("status_code"));
            assertFalse(jsonObject.has("status_msg"));
            assertThat(jsonObject.getJSONObject("dimensions").getString("foo"), is("bar"));
            assertThat(jsonObject.getJSONObject("dimensions").getString("bar"), is("foo"));
            assertThat(jsonObject.getJSONObject("dimensions").getString("serviceDim"), is("serviceDimValue"));
            assertThat(jsonObject.getJSONObject("routing").getJSONObject("yamas").getJSONArray("namespaces").length(), is(1));
            if (jsonObject.getJSONObject("metrics").has("foo_count")) {
                assertThat(jsonObject.getJSONObject("metrics").getInt("foo_count"), is(1));
                assertThat(jsonObject.getJSONObject("routing").getJSONObject("yamas").getJSONArray("namespaces").get(0), is(VESPA_CONSUMER_ID.id));
            } else {
                assertThat(jsonObject.getJSONObject("metrics").getInt("foo.count"), is(1));
                assertThat(jsonObject.getJSONObject("routing").getJSONObject("yamas").getJSONArray("namespaces").get(0), is(CUSTOM_CONSUMER_ID.id));
            }
        }

        verifyStatusMessage(metrics.getJSONObject(metrics.length() - 1));
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

    private void verifyMetricsFromRpcRequestForAllServices(Target target) throws JSONException {
        // Verify that metrics for all services can be retrieved in one request.
        String allServicesResponse = getMetricsForYamas(ALL_SERVICES, target).trim();
        JSONArray allServicesMetrics = new JSONObject(allServicesResponse).getJSONArray("metrics");
        assertThat(allServicesMetrics.length(), is(5));
    }

    @Test
    public void testGetAllMetricNames() {
        try (IntegrationTester tester = new IntegrationTester(httpPort, rpcPort)) {

            tester.httpServer().setResponse(METRICS_RESPONSE_CCL);
            List<VespaService> services = tester.vespaServices().getInstancesById(SERVICE_1_CONFIG_ID);

            assertThat(services.size(), is(1));
            Metrics metrics = services.get(0).getMetrics();
            assertThat("Fetched number of metrics is not correct", metrics.size(), is(2));
            Metric m = metrics.getMetric("foo.count");
            assertNotNull("Did not find expected metric with name 'foo.count'", m);


            Metric m2 = metrics.getMetric("bar.count");
            assertNotNull("Did not find expected metric with name 'bar'", m2);

            // Setup RPC
            Supervisor supervisor = new Supervisor(new Transport());
            Target target = supervisor.connect(new Spec("localhost", rpcPort));

            String response = getAllMetricNamesForService(services.get(0).getMonitoringName(), VESPA_CONSUMER_ID, target);
            assertThat(response, is("foo.count=ON;output-name=foo_count,bar.count=OFF,"));

            // Shutdown RPC
            target.close();
            supervisor.transport().shutdown().join();
        }
    }

    private static String getMetricsForYamas(String service, Target target) {
        Request req = new Request("getMetricsForYamas");
        req.parameters().add(new StringValue(service));
        return invoke(req, target);
    }

    private String getMetricsById(String service, Target target) {
        Request req = new Request("getMetricsById");
        req.parameters().add(new StringValue(service));
        return invoke(req, target);
    }

    private String getAllMetricNamesForService(String service, ConsumerId consumer, Target target) {
        Request req = new Request("getAllMetricNamesForService");
        req.parameters().add(new StringValue(service));
        req.parameters().add(new StringValue(consumer.id));
        return invoke(req, target);
    }

    private static String invoke(Request req, Target target) {
        String returnValue;
        target.invokeSync(req, 20.0);
        if (req.checkReturnTypes("s")) {
            returnValue = req.returnValues().get(0).asString();
        } else {
            System.out.println(req.methodName() + " from rpcserver - Invocation failed "
                                       + req.errorCode() + ": " + req.errorMessage());
            returnValue = req.errorCode() + ": " + req.errorMessage();
        }
        return returnValue;
    }

    private static void verifyStatusMessage(JSONObject jsonObject) throws JSONException {
        assertThat(jsonObject.getInt("status_code"), is(0));
        assertThat(jsonObject.getString("status_msg"), notNullValue());
        assertThat(jsonObject.getString("application"), notNullValue());
        assertThat(jsonObject.getString("routing"), notNullValue());
        assertThat(jsonObject.length(), is(4));
    }

}
