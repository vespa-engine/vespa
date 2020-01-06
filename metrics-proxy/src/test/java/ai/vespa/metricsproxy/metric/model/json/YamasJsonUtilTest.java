// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import org.junit.Test;

import java.util.List;

import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static ai.vespa.metricsproxy.metric.model.json.YamasJsonUtil.toMetricsPackets;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class YamasJsonUtilTest {
    @Test
    public void json_model_gets_null_status_by_default() {
        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo"))
                .build();
        YamasJsonModel jsonModel = YamasJsonUtil.toYamasArray(singleton(packet)).metrics.get(0);
        assertNull(jsonModel.status_code);
        assertNull(jsonModel.status_msg);
    }

    @Test
    public void status_is_included_in_json_model_when_explicitly_asked_for() {
        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo"))
                .build();
        YamasJsonModel jsonModel = YamasJsonUtil.toYamasArray(singleton(packet), true).metrics.get(0);
        assertNotNull(jsonModel.status_code);
        assertNotNull(jsonModel.status_msg);
    }

    @Test
    public void timestamp_0_in_packet_is_translated_to_null_in_json_model() {
        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo"))
                .timestamp(0L)
                .build();
        YamasJsonModel jsonModel = YamasJsonUtil.toYamasArray(singleton(packet)).metrics.get(0);
        assertNull(jsonModel.timestamp);
    }

    @Test
    public void empty_consumers_is_translated_to_null_routing_in_json_model() {
        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo"))
                .build();
        YamasJsonModel jsonModel = YamasJsonUtil.toYamasArray(singleton(packet)).metrics.get(0);
        assertNull(jsonModel.routing);
    }

    @Test
    public void empty_json_string_yields_empty_packet_list() {
        List<MetricsPacket.Builder> builders = toMetricsPackets("");
        assertTrue(builders.isEmpty());
    }
}
