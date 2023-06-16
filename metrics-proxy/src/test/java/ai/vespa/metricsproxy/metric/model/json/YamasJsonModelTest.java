// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

import ai.vespa.metricsproxy.http.yamas.YamasResponse;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for YamasJsonModel and YamasArrayJsonModel
 *
 * @author smorgrav
 * @author gjoranv
 */
public class YamasJsonModelTest {

    private static final String EXPECTED_JSON = "{\"metrics\":[{\"timestamp\":1400047900,\"application\":\"vespa.searchnode\",\"metrics\":{\"cpu\":55.5555555555555,\"memory_virt\":22222222222,\"memory_rss\":5555555555},\"dimensions\":{\"applicationName\":\"app\",\"tenantName\":\"tenant\",\"metrictype\":\"system\",\"instance\":\"searchnode\",\"applicationInstance\":\"default\",\"clustername\":\"cluster\"},\"routing\":{\"yamas\":{\"namespaces\":[\"Vespa\"]}}}]}";

    @Test
    public void array_definition_creates_correct_json() throws IOException {
        YamasJsonModel jsonModel = getYamasJsonModel("yamas-array.json");

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            YamasResponse response = new YamasResponse(200, List.of(YamasJsonUtil.toMetricsPacketBuilder(jsonModel).build()));
            response.render(outputStream);
            assertEquals(EXPECTED_JSON, outputStream.toString());
        }
    }

    @Test
    public void deserialize_serialize_roundtrip() throws IOException {
        YamasJsonModel jsonModel = getYamasJsonModel("yamas-array.json");

        // Do some sanity checking
        assertEquals("vespa.searchnode", jsonModel.application);
        assertTrue(jsonModel.routing.get("yamas").namespaces.contains("Vespa"));
        assertEquals(5.555555555E9, jsonModel.metrics.get("memory_rss"), 0.1d); //Not using custom double renderer

        // Serialize and verify
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            YamasResponse response = new YamasResponse(200, List.of(YamasJsonUtil.toMetricsPacketBuilder(jsonModel).build()));
            response.render(outputStream);
            assertEquals(EXPECTED_JSON, outputStream.toString());
        }
    }

    @Test
    public void deserialize_serialize_roundtrip_with_metrics_packet() throws IOException {
        YamasJsonModel jsonModel = getYamasJsonModel("yamas-array.json");
        MetricsPacket metricsPacket = YamasJsonUtil.toMetricsPacketBuilder(jsonModel).build();

        // Do some sanity checking
        assertEquals(toServiceId("vespa.searchnode"), metricsPacket.service);
        assertTrue(metricsPacket.consumers().contains(toConsumerId("Vespa")));
        assertEquals(5.555555555E9, metricsPacket.metrics().get(toMetricId("memory_rss")).doubleValue(), 0.1d); //Not using custom double rendrer

        // Serialize and verify
        String string = YamasJsonUtil.toJson(List.of(metricsPacket), false);
        assertEquals(EXPECTED_JSON, string);
    }

    @Test
    public void missing_routing_object_makes_it_null() throws IOException {
        // Read file that was taken from production (real -life example that is)
        String filename = getClass().getClassLoader().getResource("yamas-array-no-routing.json").getFile();
        BufferedReader reader = Files.newBufferedReader(Paths.get(filename));
        ObjectMapper mapper = new ObjectMapper();
        YamasJsonModel jsonModel = mapper.readValue(reader, YamasJsonModel.class);

        // Do some sanity checking
        assertNull(jsonModel.routing);
    }

    private YamasJsonModel getYamasJsonModel(String testFile) throws IOException {
        String filename = getClass().getClassLoader().getResource(testFile).getFile();
        BufferedReader reader = Files.newBufferedReader(Paths.get(filename));
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(reader, YamasJsonModel.class);
    }

}
