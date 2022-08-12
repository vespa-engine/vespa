// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.time.Duration;
import java.util.List;

import static ai.vespa.metricsproxy.TestUtil.getFileContents;
import static ai.vespa.metricsproxy.rpc.IntegrationTester.SERVICE_1_CONFIG_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
    private static final Duration RPC_INVOKE_TIMEOUT = Duration.ofSeconds(60);


    @Rule
    public Timeout globalTimeout = Timeout.seconds(300);

    @Test
    public void expected_response_is_returned() {
        try (IntegrationTester tester = new IntegrationTester()) {
            MockHttpServer mockHttpServer = tester.httpServer();
            mockHttpServer.setResponse(HEALTH_OK_RESPONSE);
            List<VespaService> services = tester.vespaServices().getInstancesById(SERVICE_1_CONFIG_ID);

            assertEquals(1, services.size());
            VespaService container = services.get(0);
            HealthMetric h = container.getHealth();
            assertNotNull("Health metric should never be null", h);
            assertTrue("Status failed, reason = " + h.getMessage(), h.isOk());
            assertEquals("WORKING", h.getMessage());

            mockHttpServer.setResponse(HEALTH_FAILED_RESPONSE);
            h = container.getHealth();
            assertNotNull("Health metric should never be null", h);
            assertFalse("Status should be failed" + h.getMessage(), h.isOk());
            assertEquals("SOMETHING FAILED", h.getMessage());

            String jsonRPCMessage = getHealthMetrics(tester, container.getMonitoringName().id);
            assertEquals(WANTED_RPC_RESPONSE, jsonRPCMessage);
        }
    }

    @Test
    public void non_existent_service_name_returns_an_error_message() {
        try (IntegrationTester tester = new IntegrationTester()) {
            String jsonRPCMessage = getHealthMetrics(tester, "non-existing service");
            assertEquals("105: No service with name 'non-existing service'", jsonRPCMessage);
        }
    }

    private String getHealthMetrics(IntegrationTester tester, String service) {
        Supervisor supervisor = new Supervisor(new Transport());
        Target target = supervisor.connect(new Spec("localhost", tester.rpcPort()));
        Request req = new Request("getHealthMetricsForYamas");
        req.parameters().add(new StringValue(service));
        String returnValue;

        target.invokeSync(req, RPC_INVOKE_TIMEOUT);
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
