// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author olaa
 */
public class NodeMetricGathererTest {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    @Test
    public void testJSONObjectIsCorrectlyConvertedToMetricsPacket() {
        List<MetricsPacket.Builder> builders = new ArrayList<>();
        JsonNode hostLifePacket = generateHostLifePacket();
        NodeMetricGatherer.addObjectToBuilders(builders, hostLifePacket);
        MetricsPacket packet = builders.remove(0).build();

        assertEquals("host_life", packet.service.id);
        assertEquals(0, packet.statusCode);
        assertEquals("OK", packet.statusMessage);
        assertEquals(123, packet.timestamp);
        assertEquals(12l, packet.metrics().get(MetricId.toMetricId("uptime")));
        assertEquals(1l, packet.metrics().get(MetricId.toMetricId("alive")));
        assertEquals(Set.of(ConsumerId.toConsumerId("Vespa")), packet.consumers());
    }

    private JsonNode generateHostLifePacket() {

        ObjectNode jsonObject = jsonMapper.createObjectNode();
        jsonObject.put("status_code", 0);
        jsonObject.put("status_msg", "OK");
        jsonObject.put("timestamp", 123);
        jsonObject.put("application", "host_life");
        ObjectNode metrics = jsonMapper.createObjectNode();
        metrics.put("uptime", 12);
        metrics.put("alive", 1);
        jsonObject.set("metrics", metrics);
        return jsonObject;
    }
}
