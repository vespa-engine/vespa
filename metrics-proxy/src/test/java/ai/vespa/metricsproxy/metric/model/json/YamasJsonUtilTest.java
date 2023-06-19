// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static ai.vespa.metricsproxy.core.VespaMetrics.vespaMetricsConsumerId;
import static ai.vespa.metricsproxy.http.ValuesFetcher.defaultMetricsConsumerId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static ai.vespa.metricsproxy.metric.model.json.YamasJsonUtil.YAMAS_ROUTING;
import static ai.vespa.metricsproxy.metric.model.json.YamasJsonUtil.toMetricsPackets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class YamasJsonUtilTest {
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static JsonNode metrics(MetricsPacket packet, boolean addStatus) throws IOException {
        return metrics(List.of(packet), addStatus).get(0);
    }
    private static ArrayNode metrics(List<MetricsPacket> packets, boolean addStatus) throws IOException {
        return (ArrayNode) jsonMapper.readTree(YamasJsonUtil.toJson(packets, addStatus)).get("metrics");
    }

    @Test
    public void status_is_included_in_json_model_when_explicitly_asked_for() throws IOException {
        ArrayNode json = metrics(List.of(new MetricsPacket.Builder(toServiceId("foo")).build(),
                new MetricsPacket.Builder(toServiceId("bar")).build()), true);
        assertTrue(json.get(0).has("status_code"));
        assertTrue(json.get(0).has("status_msg"));
        assertTrue(json.get(1).has("status_code"));
        assertTrue(json.get(1).has("status_msg"));
    }

    @Test
    public void timestamp_0_in_packet_is_translated_to_null_in_json_model() throws IOException {
        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo")).timestamp(0L).build();
        JsonNode json = metrics(packet, true);
        assertFalse(json.has("timestamp"));
    }

    @Test
    public void empty_consumers_is_translated_to_null_routing_in_json_model() throws IOException {
        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo")).build();
        JsonNode json = metrics(packet, true);
        assertFalse(json.has("routing"));
    }

    @Test
    public void default_public_consumer_is_filtered_from_yamas_routing() throws IOException {
        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo"))
                .addConsumers(Set.of(vespaMetricsConsumerId, defaultMetricsConsumerId))
                .build();
        JsonNode json = metrics(packet, false);
        JsonNode routing = json.get("routing");
        JsonNode yamas = routing.get(YAMAS_ROUTING);
        ArrayNode namespaces = (ArrayNode) yamas.get("namespaces");
        assertEquals(1, namespaces.size());
        assertEquals(vespaMetricsConsumerId.id, namespaces.get(0).asText());
    }

    @Test
    public void only_default_public_consumer_yields_null_routing_in_json_model() throws IOException {
        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo"))
                .addConsumers(Set.of(defaultMetricsConsumerId))
                .build();
        JsonNode json = metrics(packet, false);
        assertFalse(json.has(YAMAS_ROUTING));
    }

    @Test
    public void empty_json_string_yields_empty_packet_list() {
        List<MetricsPacket.Builder> builders = toMetricsPackets("");
        assertTrue(builders.isEmpty());
    }
}
