// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.rpc;

import ai.vespa.metricsproxy.metric.HealthMetric;
import ai.vespa.metricsproxy.service.MockHttpServer;
import ai.vespa.metricsproxy.service.VespaService;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import org.junit.Test;

import java.util.List;

import static ai.vespa.metricsproxy.TestUtil.getFileContents;
import static ai.vespa.metricsproxy.rpc.IntegrationTester.SERVICE_1_CONFIG_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author jobergum
 * @author gjoranv
 */
public class RpcHealthMetricsTest {

    private static final String HEALTH_OK_RESPONSE =
            getFileContents("health-check.response.json");
    private static final String HEALTH_FAILED_RESPONSE =
            getFileContents("health-check-failed.response.json");
    private static final String WANTED_RPC_RESPONSE =
            getFileContents("rpc-json-output-check.json").trim();

    @Test
    public void expected_response_is_returned() {
        try (IntegrationTester tester = new IntegrationTester()) {
            MockHttpServer mockHttpServer = tester.httpServer();
            mockHttpServer.setResponse(HEALTH_OK_RESPONSE);
            List<VespaService> services = tester.vespaServices().getInstancesById(SERVICE_1_CONFIG_ID);

            assertThat(services.size(), is(1));
            VespaService qrserver = services.get(0);
            HealthMetric h = qrserver.getHealth();
            assertNotNull("Health metric should never be null", h);
            assertThat("Status failed, reason = " + h.getMessage(), h.isOk(), is(true));
            assertThat(h.getMessage(), is("WORKING"));

            mockHttpServer.setResponse(HEALTH_FAILED_RESPONSE);
            h = qrserver.getHealth();
            assertNotNull("Health metric should never be null", h);
            assertThat("Status should be failed" + h.getMessage(), h.isOk(), is(false));
            assertThat(h.getMessage(), is("SOMETHING FAILED"));

            String jsonRPCMessage = getHealthMetrics(tester, qrserver.getMonitoringName());
            assertThat(jsonRPCMessage, is(WANTED_RPC_RESPONSE));
        }
    }

    @Test
    public void non_existent_service_name_returns_an_error_message() {
        try (IntegrationTester tester = new IntegrationTester()) {
            String jsonRPCMessage = getHealthMetrics(tester, "non-existing service");
            assertThat(jsonRPCMessage, is("105: No service with name 'non-existing service'"));
        }
    }

    private String getHealthMetrics(IntegrationTester tester, String service) {
        Supervisor supervisor = new Supervisor(new Transport());
        Target target = supervisor.connect(new Spec("localhost", tester.rpcPort()));
        Request req = new Request("getHealthMetricsForYamas");
        req.parameters().add(new StringValue(service));
        String returnValue;

        target.invokeSync(req, 20.0);
        if (req.checkReturnTypes("s")) {
            returnValue = req.returnValues().get(0).asString();
        } else {
            System.out.println("RpcServer invocation failed " + req.errorCode() + ": " + req.errorMessage());
            returnValue = req.errorCode() + ": " + req.errorMessage();
        }
        target.close();
        supervisor.transport().shutdown().join();

        return returnValue;
    }
}
