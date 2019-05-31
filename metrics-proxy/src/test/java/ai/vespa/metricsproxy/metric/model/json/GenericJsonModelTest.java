/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.metric.model.json;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author gjoranv
 */
public class GenericJsonModelTest {
    private static final String TEST_FILE = "generic-sample.json";

    @Test
    public void deserialize_serialize_roundtrip() throws IOException {
        GenericJsonModel jsonModel = genericJsonModelFromTestFile();

        // Do some sanity checking
        assertEquals(2, jsonModel.services.size());
        assertEquals(2, jsonModel.node.metrics.size());
        assertEquals(16.222, jsonModel.node.metrics.get(0).values.get("cpu.util"), 0.01d);

        String expected = getFileContents(TEST_FILE).trim().replaceAll("\\s+", "");;

        String serialized = jsonModel.serialize();
        String trimmed = serialized.trim().replaceAll("\\s+", "");

        assertEquals(expected, trimmed);
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
        assertEquals(1, service.metrics.size());
        GenericMetrics serviceMetrics = service.metrics.get(0);
        assertEquals(1234L, serviceMetrics.values.get("service-metric").longValue());
        assertEquals("service-dim-value", serviceMetrics.dimensions.get("service-dim"));

        // Visual inspection
        System.out.println(createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jsonModel));
    }


    private GenericJsonModel genericJsonModelFromTestFile() throws IOException {
        ObjectMapper mapper = createObjectMapper();
        return mapper.readValue(getFileContents(TEST_FILE), GenericJsonModel.class);
    }

}
