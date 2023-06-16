// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.prometheus;

import ai.vespa.metricsproxy.http.HttpHandlerTestBase;
import ai.vespa.metricsproxy.service.DummyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.Executors;

import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.REASON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
@SuppressWarnings("UnstableApiUsage")
public class PrometheusHandlerTest extends HttpHandlerTestBase {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private static final String V1_URI = URI_BASE + PrometheusHandler.V1_PATH;
    private static final String VALUES_URI = URI_BASE + PrometheusHandler.VALUES_PATH;

    private static String valuesResponse;

    @BeforeClass
    public static void setup() {
        PrometheusHandler handler = new PrometheusHandler(Executors.newSingleThreadExecutor(),
                                                          getMetricsManager(),
                                                          vespaServices,
                                                          getMetricsConsumers());
        testDriver = new RequestHandlerTestDriver(handler);
        valuesResponse = testDriver.sendRequest(VALUES_URI).readAll();
    }

    @Test
    public void v1_response_contains_values_uri() throws Exception {
        String response = testDriver.sendRequest(V1_URI).readAll();
        JsonNode root = jsonMapper.readTree(response);
        assertTrue(root.has("resources"));

        ArrayNode resources = (ArrayNode) root.get("resources");
        assertEquals(1, resources.size());

        JsonNode valuesUrl = resources.get(0);
        assertEquals(VALUES_URI, valuesUrl.get("url").textValue());
    }

    @Ignore
    @Test
    public void visually_inspect_values_response() {
        System.out.println(valuesResponse);
    }

    @Test
    public void response_contains_node_metrics() {
        String cpu = getLine(valuesResponse, CPU_METRIC + "{");
        assertTrue(cpu.contains("} 12.345"));   // metric value
    }

    @Test
    public void response_contains_service_metrics() {
        String dummy0 = getLine(valuesResponse, DummyService.NAME + "0");
        assertTrue(dummy0.contains("c_test"));  // metric name
        assertTrue(dummy0.contains("} 1.0"));   // metric value
    }

    @Test
    public void service_metrics_have_configured_dimensions() {
        String dummy0 = getLine(valuesResponse, DummyService.NAME + "0");
        assertTrue(dummy0.contains(REASON + "=\"default-val\""));
    }

    @Test
    public void service_metrics_have_vespa_service_dimension() {
        String dummy0 = getLine(valuesResponse, DummyService.NAME + "0");
        assertTrue(dummy0.contains("vespa_service=\"vespa_dummy\""));
    }

    // Find the first line that contains the given string
    private String getLine(String raw, String searchString) {
        for (var s : raw.split("\\n")) {
            if (s.contains(searchString))
                return s;
        }
        throw new IllegalArgumentException("No line containing string: " + searchString);
    }
}
