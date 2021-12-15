// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric;

import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class ExternalMetricsTest {
    private static final ConsumerId CUSTOM_CONSUMER_1 = toConsumerId("consumer-1");
    private static final ConsumerId CUSTOM_CONSUMER_2 = toConsumerId("consumer-2");

    @Test
    public void extra_metrics_are_added() {
        MetricsConsumers noConsumers = new MetricsConsumers(new ConsumersConfig.Builder().build());
        ExternalMetrics externalMetrics = new ExternalMetrics(noConsumers);

        externalMetrics.setExtraMetrics(ImmutableList.of(
                new MetricsPacket.Builder(toServiceId("foo"))));

        List<MetricsPacket.Builder> packets = externalMetrics.getMetrics();
        assertEquals(1, packets.size());
    }

    @Test
    public void service_id_from_extra_packets_is_not_replaced() {
        final ServiceId SERVICE_ID = toServiceId("do-not-replace");

        MetricsConsumers noConsumers = new MetricsConsumers(new ConsumersConfig.Builder().build());
        ExternalMetrics externalMetrics = new ExternalMetrics(noConsumers);
        externalMetrics.setExtraMetrics(ImmutableList.of(
                new MetricsPacket.Builder(SERVICE_ID)));

        List<MetricsPacket.Builder> packets = externalMetrics.getMetrics();
        assertEquals(1, packets.size());
        assertEquals(SERVICE_ID, packets.get(0).build().service);
    }

    @Test
    public void custom_consumers_are_added() {
        ConsumersConfig consumersConfig = new ConsumersConfig.Builder()
                .consumer(new ConsumersConfig.Consumer.Builder().name(CUSTOM_CONSUMER_1.id))
                .consumer(new ConsumersConfig.Consumer.Builder().name(CUSTOM_CONSUMER_2.id))
                .build();
        MetricsConsumers consumers = new MetricsConsumers(consumersConfig);
        ExternalMetrics externalMetrics = new ExternalMetrics(consumers);

        externalMetrics.setExtraMetrics(ImmutableList.of(
                new MetricsPacket.Builder(toServiceId("foo"))));

        List<MetricsPacket.Builder> packets = externalMetrics.getMetrics();
        assertEquals(1, packets.size());

        Set<ConsumerId> consumerIds = packets.get(0).build().consumers();
        assertEquals(2, consumerIds.size());
        assertTrue(consumerIds.contains(CUSTOM_CONSUMER_1));
        assertTrue(consumerIds.contains(CUSTOM_CONSUMER_2));
    }

}
