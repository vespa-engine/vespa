// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

import ai.vespa.metricsproxy.http.application.Node;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.StatusCode;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ai.vespa.metricsproxy.TestUtil.getFileContents;
import static ai.vespa.metricsproxy.metric.ExternalMetrics.VESPA_NODE_SERVICE_ID;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static ai.vespa.metricsproxy.metric.model.json.JacksonUtil.objectMapper;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author gjoranv
 */
public class GenericApplicationModelTest {
    private static final String TEST_FILE = "generic-application.json";

    @Test
    public void deserialize_serialize_roundtrip() throws IOException {
        GenericApplicationModel model = genericJsonModelFromTestFile();

        // Do some sanity checking
        assertEquals(2, model.nodes.size());
        GenericJsonModel node0Model = model.nodes.get(0);
        assertEquals("node0", node0Model.hostname);
        assertEquals("role0", node0Model.role);
        assertEquals(1, node0Model.services.size());
        GenericService service = node0Model.services.get(0);
        assertEquals(1, service.metrics.size());
        assertEquals(4, service.metrics.get(0).values.get("queries.count"), 0.001d);

        GenericJsonModel node1Model = model.nodes.get(1);
        GenericNode node1 = node1Model.node;
        assertEquals("node1", node1Model.hostname);
        assertEquals("role1", node1Model.role);
        assertEquals(32.444, node1.metrics.get(0).values.get("cpu.util"), 0.001d);

        assertThatSerializedModelEqualsTestFile(model);
    }

    @Test
    public void metrics_packets_can_be_converted_to_generic_json_model() throws Exception {
        var nodePacket = new MetricsPacket.Builder(VESPA_NODE_SERVICE_ID)
                .timestamp(Instant.ofEpochSecond(123456L))
                .putMetric(toMetricId("node-metric"), 1.234)
                .putDimension(toDimensionId("node-dim"), "node-dim-value")
                .build();

        var servicePacket = new MetricsPacket.Builder(toServiceId("my-service"))
                .timestamp(Instant.ofEpochSecond(123456L))
                .statusCode(0)
                .putMetric(toMetricId("service-metric"), 1234)
                .putDimension(toDimensionId("service-dim"), "service-dim-value")
                .build();


        var metricsByNode = Map.of(new Node("my-role", "hostname", 0, "path"),
                                   List.of(nodePacket, servicePacket));

        GenericApplicationModel model = GenericJsonUtil.toGenericApplicationModel(metricsByNode);

        GenericJsonModel nodeModel = model.nodes.get(0);
        assertNotNull(nodeModel.node);
        assertEquals("hostname", nodeModel.hostname);
        assertEquals("my-role", nodeModel.role);
        assertEquals(1, nodeModel.node.metrics.size());
        GenericMetrics nodeMetrics = nodeModel.node.metrics.get(0);
        assertEquals(1.234, nodeMetrics.values.get("node-metric"), 0.001d);
        assertEquals("node-dim-value", nodeMetrics.dimensions.get("node-dim"));

        assertEquals(1, nodeModel.services.size());
        GenericService service = nodeModel.services.get(0);
        assertEquals(StatusCode.UP.status, service.status.code);
        assertEquals("", service.status.description);

        assertEquals(1, service.metrics.size());
        GenericMetrics serviceMetrics = service.metrics.get(0);
        assertEquals(1234L, serviceMetrics.values.get("service-metric").longValue());
        assertEquals("service-dim-value", serviceMetrics.dimensions.get("service-dim"));

        // Visual inspection
        System.out.println(objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(nodeModel));
    }

    @Test
    public void application_model_is_serialized_node_by_node_to_the_same_json() {
        var metricsByNode = new LinkedHashMap<Node, List<MetricsPacket>>();
        metricsByNode.put(new Node("role0", "host0", 0, "path"),
                          List.of(createMetricsPacket("node", 1.5, true),
                                  createMetricsPacket("searchnode", 4, false),
                                  createMetricsPacket("searchnode", 12345678901234.5, false),
                                  createMetricsPacket("container", 0.000123, false)));
        metricsByNode.put(new Node("role1", "host1", 0, "path"),
                          List.of(new MetricsPacket.Builder(toServiceId("distributor"))
                                          .timestamp(Instant.ofEpochSecond(1234L))
                                          .statusCode(StatusCode.DOWN.ordinal())
                                          .statusMessage("down for a reason")
                                          .build()));
        metricsByNode.put(new Node("role2", "host2", 0, "path"), List.of());

        assertEquals(GenericJsonUtil.toGenericApplicationModel(metricsByNode).serialize(), serializeNodeByNode(metricsByNode));
        assertEquals(GenericJsonUtil.toGenericApplicationModel(Map.of()).serialize(), serializeNodeByNode(Map.of()));
    }

    private static String serializeNodeByNode(Map<Node, List<MetricsPacket>> metricsByNode) {
        var out = new ByteArrayOutputStream();
        GenericJsonUtil.serializeApplicationModel(metricsByNode, out);
        return out.toString(UTF_8);
    }

    private MetricsPacket createMetricsPacket(String service, Number metricsValue, boolean isNode) {
        return new MetricsPacket.Builder(isNode ? VESPA_NODE_SERVICE_ID : toServiceId(service))
                .timestamp(Instant.ofEpochSecond(1234L))
                .statusCode(0)
                .putMetric(toMetricId(service + "-metric"), metricsValue)
                .putDimension(toDimensionId(service + "-dim"), isNode ? "node-dim-value" : "service-dim-value")
                .build();

    }

    private static void assertThatSerializedModelEqualsTestFile(GenericApplicationModel model) {
        String serialized = model.serialize();
        String trimmed = serialized.trim().replaceAll("\\s+", "");

        String expected = getFileContents(TEST_FILE).trim().replaceAll("\\s+", "");
        assertEquals(expected, trimmed);
    }

    private static GenericApplicationModel genericJsonModelFromTestFile() throws IOException {
        return objectMapper().readValue(getFileContents(TEST_FILE), GenericApplicationModel.class);
    }

}
