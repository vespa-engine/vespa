// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author olaa
 */
public class NodeMetricGathererTest {

    @Test
    public void testJSONObjectIsCorrectlyConvertedToMetricsPacket() throws JSONException {
        List<MetricsPacket.Builder> builders = new ArrayList<>();
        JSONObject hostLifePacket = generateHostLifePacket();
        NodeMetricGatherer.addObjectToBuilders(builders, hostLifePacket);
        MetricsPacket packet = builders.remove(0).build();

        assertEquals("host_life", packet.service.id);
        assertEquals(0, packet.statusCode);
        assertEquals("OK", packet.statusMessage);
        assertEquals(123, packet.timestamp);
        assertEquals(12l, packet.metrics().get(MetricId.toMetricId("uptime")));
        assertEquals(1l, packet.metrics().get(MetricId.toMetricId("alive")));
    }

    private JSONObject generateHostLifePacket() throws JSONException {

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("status_code", 0);
        jsonObject.put("status_msg", "OK");
        jsonObject.put("timestamp", 123);
        jsonObject.put("application", "host_life");
        JSONObject metrics = new JSONObject();
        metrics.put("uptime", 12);
        metrics.put("alive", 1);
        jsonObject.put("metrics", metrics);
        return jsonObject;
    }
}
