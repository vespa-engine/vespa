// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import ai.vespa.metricsproxy.TestUtil;
import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensionsConfig;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensionsConfig;
import ai.vespa.metricsproxy.metric.dimensions.PublicDimensions;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.service.VespaServices;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static ai.vespa.metricsproxy.metric.model.json.JacksonUtil.objectMapper;
import static org.junit.Assert.assertEquals;

/**
 * @author olaa
 */
public class NodeMetricGathererTest {

    @Test
    public void testJSONObjectIsCorrectlyConvertedToMetricsPacket() {
        List<MetricsPacket.Builder> builders = new ArrayList<>();
        JsonNode hostLifePacket = generateHostLifePacket();
        NodeMetricGatherer.addObjectToBuilders(builders, hostLifePacket);
        MetricsPacket packet = builders.remove(0).build();

        assertEquals("host_life", packet.service().id);
        assertEquals(123, packet.timestamp().getEpochSecond());
        assertEquals(12L, packet.metrics().get(MetricId.toMetricId("uptime")));
        assertEquals(1L, packet.metrics().get(MetricId.toMetricId("alive")));
        assertEquals(Set.of(ConsumerId.toConsumerId("Vespa")), packet.consumers());
    }

    @Test
    public void host_life_packet_gets_extra_host_dimensions() {
        ApplicationDimensions applicationDimensions =
                new ApplicationDimensions(new ApplicationDimensionsConfig.Builder().build());
        NodeDimensions nodeDimensions =
                new NodeDimensions(new NodeDimensionsConfig.Builder().build());
        MetricsConsumers consumers = new MetricsConsumers(new ConsumersConfig.Builder().build());
        MetricsManager metricsManager = TestUtil.createMetricsManager(
                new VespaServices(List.of()), consumers, applicationDimensions, nodeDimensions);

        // host-admin pushes host-level dimensions on its packets; these get harvested.
        metricsManager.setExtraMetrics(List.of(
                new MetricsPacket.Builder(toServiceId("vespa.node"))
                        .putDimension(toDimensionId(PublicDimensions.HOSTNAME), "host1")
                        .putDimension(toDimensionId(PublicDimensions.PARENT_HOSTNAME), "parent1")
                        .putDimension(toDimensionId(PublicDimensions.OS_VERSION), "8.4.0")));

        NodeMetricGatherer gatherer = new NodeMetricGatherer(metricsManager, applicationDimensions, nodeDimensions);

        MetricsPacket hostLife = gatherer.gatherMetrics().stream()
                .filter(p -> p.service().id.equals("host_life"))
                .findFirst().orElseThrow();

        assertEquals("host1", hostLife.dimensions().get(toDimensionId(PublicDimensions.HOSTNAME)));
        assertEquals("parent1", hostLife.dimensions().get(toDimensionId(PublicDimensions.PARENT_HOSTNAME)));
        assertEquals("8.4.0", hostLife.dimensions().get(toDimensionId(PublicDimensions.OS_VERSION)));
    }

    private JsonNode generateHostLifePacket() {

        ObjectNode jsonObject = objectMapper().createObjectNode();
        jsonObject.put("timestamp", 123);
        jsonObject.put("application", "host_life");
        ObjectNode metrics = objectMapper().createObjectNode();
        metrics.put("uptime", 12);
        metrics.put("alive", 1);
        jsonObject.set("metrics", metrics);
        return jsonObject;
    }
}
