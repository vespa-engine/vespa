// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.StatusCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static ai.vespa.metricsproxy.TestUtil.getFileContents;
import static ai.vespa.metricsproxy.metric.ExternalMetrics.VESPA_NODE_SERVICE_ID;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static ai.vespa.metricsproxy.metric.model.json.JacksonUtil.createObjectMapper;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author gjoranv
 */
public class GenericJsonModelTest {
    private static final String TEST_FILE = "generic-sample.json";
    private static final String TEST_FILE_WITHOUT_NODE = "generic-without-node.json";

    @Test
    public void deserialize_serialize_roundtrip() throws IOException {
        GenericJsonModel jsonModel = genericJsonModelFromTestFile(TEST_FILE);

        // Do some sanity checking
        assertEquals(2, jsonModel.services.size());
        assertEquals(2, jsonModel.node.metrics.size());
        assertEquals(16.222, jsonModel.node.metrics.get(0).values.get("cpu.util"), 0.01d);

        assertThatSerializedModelEqualsTestFile(jsonModel, TEST_FILE);
    }

    @Test
    public void deserialize_serialize_roundtrip_without_node_json() throws IOException {
        GenericJsonModel jsonModel = genericJsonModelFromTestFile(TEST_FILE_WITHOUT_NODE);
        assertEquals(2, jsonModel.services.size());

        assertThatSerializedModelEqualsTestFile(jsonModel, TEST_FILE_WITHOUT_NODE);
    }

    @Test
    public void deserialize_serialize_roundtrip_with_metrics_packets() throws IOException {
        GenericJsonModel modelFromFile = genericJsonModelFromTestFile(TEST_FILE);
        List<MetricsPacket> metricsPackets = GenericJsonUtil.toMetricsPackets(modelFromFile).stream()
                .map(MetricsPacket.Builder::build)
                .collect(toList());

        assertEquals(4, metricsPackets.size());

        GenericJsonModel modelFromPackets = GenericJsonUtil.toGenericJsonModel(metricsPackets);

        // Do some sanity checking
        assertEquals(2, modelFromFile.services.size());
        assertEquals(2, modelFromFile.node.metrics.size());
        assertEquals(16.222, modelFromFile.node.metrics.get(0).values.get("cpu.util"), 0.01d);

        assertThatSerializedModelEqualsTestFile(modelFromPackets, TEST_FILE);
    }

    @Test
    public void deserialize_serialize_roundtrip_without_node_json_with_metrics_packets() throws IOException {
        GenericJsonModel modelFromFile = genericJsonModelFromTestFile(TEST_FILE_WITHOUT_NODE);
        List<MetricsPacket> metricsPackets = GenericJsonUtil.toMetricsPackets(modelFromFile).stream()
                .map(MetricsPacket.Builder::build)
                .collect(toList());

        assertEquals(2, metricsPackets.size());

        GenericJsonModel modelFromPackets = GenericJsonUtil.toGenericJsonModel(metricsPackets);
        assertThatSerializedModelEqualsTestFile(modelFromPackets, TEST_FILE_WITHOUT_NODE);
    }

    @Test
    public void metrics_packets_can_be_converted_to_generic_json_model() throws Exception {
        var nodePacket = new MetricsPacket.Builder(VESPA_NODE_SERVICE_ID)
                .timestamp(123456L)
                .putMetric(toMetricId("node-metric"), 1.234)
                .putDimension(toDimensionId("node-dim"), "node-dim-value")
                .build();

        var servicePacket = new MetricsPacket.Builder(toServiceId("my-service"))
                .timestamp(123456L)
                .statusCode(0)
                .putMetric(toMetricId("service-metric"), 1234)
                .putDimension(toDimensionId("service-dim"), "service-dim-value")
                .build();

        var metricsPackets = List.of(servicePacket, nodePacket);

        GenericJsonModel jsonModel = GenericJsonUtil.toGenericJsonModel(metricsPackets);

        assertNotNull(jsonModel.node);
        assertEquals(1, jsonModel.node.metrics.size());
        GenericMetrics nodeMetrics = jsonModel.node.metrics.get(0);
        assertEquals(1.234, nodeMetrics.values.get("node-metric"), 0.001d);
        assertEquals("node-dim-value", nodeMetrics.dimensions.get("node-dim"));

        assertEquals(1, jsonModel.services.size());
        GenericService service = jsonModel.services.get(0);
        assertEquals(StatusCode.UP.status, service.status.code);
        assertEquals("", service.status.description);

        assertEquals(1, service.metrics.size());
        GenericMetrics serviceMetrics = service.metrics.get(0);
        assertEquals(1234L, serviceMetrics.values.get("service-metric").longValue());
        assertEquals("service-dim-value", serviceMetrics.dimensions.get("service-dim"));

        // Visual inspection
        System.out.println(createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jsonModel));
    }

    @Test
    public void generic_json_string_can_be_converted_to_metrics_packets() {
        String genericJson = getFileContents(TEST_FILE);
        List<MetricsPacket> metricsPackets = GenericJsonUtil.toMetricsPackets(genericJson).stream()
                .map(MetricsPacket.Builder::build)
                .collect(toList());

        assertEquals(4, metricsPackets.size());
        GenericJsonModel modelFromPackets = GenericJsonUtil.toGenericJsonModel(metricsPackets);

        assertThatSerializedModelEqualsTestFile(modelFromPackets, TEST_FILE);
    }

    private void assertThatSerializedModelEqualsTestFile(GenericJsonModel modelFromPackets, String testFile) {
        String serialized = modelFromPackets.serialize();
        String trimmed = serialized.trim().replaceAll("\\s+", "");

        String expected = getFileContents(testFile).trim().replaceAll("\\s+", "");
        assertEquals(expected, trimmed);
    }

    private GenericJsonModel genericJsonModelFromTestFile(String filename) throws IOException {
        ObjectMapper mapper = createObjectMapper();
        return mapper.readValue(getFileContents(filename), GenericJsonModel.class);
    }

    @Test
    public void within_long_range_as_long_if_possible() {
        assertEquals("7", JacksonUtil.format(7D));
        assertEquals("7.1", JacksonUtil.format(7.1));
        assertEquals("-7", JacksonUtil.format(-7D));
        assertEquals("-7.1", JacksonUtil.format(-7.1));
    }

    @Test
    public void outside_long_range_as_decimal_if_possible() {
        double within = Long.MAX_VALUE;
        double outside = 3 * within;
        assertEquals("9223372036854776000", JacksonUtil.format(within));
        assertEquals("-9223372036854776000", JacksonUtil.format(-within));
        assertEquals("27670116110564327000.0", JacksonUtil.format(outside));
        assertEquals("-27670116110564327000.0", JacksonUtil.format(-outside));
    }
}
