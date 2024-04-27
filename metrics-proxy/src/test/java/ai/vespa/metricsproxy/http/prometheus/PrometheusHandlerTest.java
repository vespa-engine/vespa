// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.prometheus;

import ai.vespa.metricsproxy.http.HttpHandlerTestBase;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.prometheus.PrometheusUtil;
import ai.vespa.metricsproxy.service.DummyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.REASON;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static ai.vespa.metricsproxy.metric.model.json.JacksonUtil.objectMapper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class PrometheusHandlerTest extends HttpHandlerTestBase {

    private static final String V1_URI = URI_BASE + PrometheusHandler.V1_PATH;
    private static final String VALUES_URI = URI_BASE + PrometheusHandler.VALUES_PATH;

    private static String valuesResponse;

    @BeforeClass
    public static void setup() {
        PrometheusHandler handler = new PrometheusHandler(Executors.newSingleThreadExecutor(),
                                                          getMetricsManager(),
                                                          vespaServices,
                                                          getMetricsConsumers(),
                                                          getApplicationDimensions(),
                                                          getNodeDimensions());
        testDriver = new RequestHandlerTestDriver(handler);
        valuesResponse = testDriver.sendRequest(VALUES_URI).readAll();
    }

    @Test
    public void v1_response_contains_values_uri() throws Exception {
        String response = testDriver.sendRequest(V1_URI).readAll();
        JsonNode root = objectMapper().readTree(response);
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

    @Test
    public void response_contains_service_status() {
        assertTrue(valuesResponse.contains("vespa_dummy_status 1.0"));
        assertTrue(valuesResponse.contains("vespa_down_service_status 0.0"));
    }

    // Find the first line that contains the given string
    private String getLine(String raw, String searchString) {
        for (var s : raw.split("\\n")) {
            if (s.contains(searchString))
                return s;
        }
        throw new IllegalArgumentException("No line containing string: " + searchString);
    }

    @Test
    public void timestamp_is_in_milliseconds() {
        Instant packetTimestamp = Instant.ofEpochMilli(123456789L);
        var servicePacket = new MetricsPacket.Builder(toServiceId("my-service"))
                .timestamp(packetTimestamp)
                .statusCode(0)
                .putMetric(toMetricId("service-metric"), 1234)
                .putDimension(toDimensionId("service-dim"), "service-dim-value")
                .build();
        var model = PrometheusUtil.toPrometheusModel(List.of(servicePacket));
        assertTrue(model.hasMoreElements());
        var metricsFamily = model.nextElement();
        assertEquals(1, metricsFamily.samples.size());
        var sample = metricsFamily.samples.get(0);
        assertEquals(Instant.ofEpochMilli(sample.timestampMs), packetTimestamp);
    }
}
