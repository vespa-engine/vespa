// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric;

import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

        externalMetrics.setExtraMetrics(List.of(
                new MetricsPacket.Builder(toServiceId("foo"))));

        List<MetricsPacket.Builder> packets = externalMetrics.getMetrics();
        assertEquals(1, packets.size());
    }

    @Test
    public void service_id_from_extra_packets_is_not_replaced() {
        final ServiceId SERVICE_ID = toServiceId("do-not-replace");

        MetricsConsumers noConsumers = new MetricsConsumers(new ConsumersConfig.Builder().build());
        ExternalMetrics externalMetrics = new ExternalMetrics(noConsumers);
        externalMetrics.setExtraMetrics(List.of(
                new MetricsPacket.Builder(SERVICE_ID)));

        List<MetricsPacket.Builder> packets = externalMetrics.getMetrics();
        assertEquals(1, packets.size());
        assertEquals(SERVICE_ID, packets.get(0).build().service());
    }

    @Test
    public void custom_consumers_are_added() {
        ConsumersConfig consumersConfig = new ConsumersConfig.Builder()
                .consumer(new ConsumersConfig.Consumer.Builder().name(CUSTOM_CONSUMER_1.id))
                .consumer(new ConsumersConfig.Consumer.Builder().name(CUSTOM_CONSUMER_2.id))
                .build();
        MetricsConsumers consumers = new MetricsConsumers(consumersConfig);
        ExternalMetrics externalMetrics = new ExternalMetrics(consumers);

        externalMetrics.setExtraMetrics(List.of(
                new MetricsPacket.Builder(toServiceId("foo"))));

        List<MetricsPacket.Builder> packets = externalMetrics.getMetrics();
        assertEquals(1, packets.size());

        Set<ConsumerId> consumerIds = packets.get(0).build().consumers();
        assertEquals(2, consumerIds.size());
        assertTrue(consumerIds.contains(CUSTOM_CONSUMER_1));
        assertTrue(consumerIds.contains(CUSTOM_CONSUMER_2));
    }

    @Test
    public void host_dimensions_are_extracted_and_other_dimensions_dropped() {
        MetricsPacket.Builder packet = new MetricsPacket.Builder(toServiceId("vespa.node"))
                .putDimension(ExternalMetrics.HOST_DIMENSION, "host1")
                .putDimension(ExternalMetrics.PARENT_HOSTNAME_DIMENSION, "parent1")
                .putDimension(ExternalMetrics.OS_VERSION_DIMENSION, "8.4.0")
                .putDimension(toDimensionId("role"), "tenants")
                .putDimension(toDimensionId("state"), "active")
                .putDimension(toDimensionId("zone"), "prod.us-east-1");

        Map<DimensionId, String> hostDimensions = ExternalMetrics.extractHostDimensions(List.of(packet));

        assertEquals(3, hostDimensions.size());
        assertEquals("host1", hostDimensions.get(ExternalMetrics.HOST_DIMENSION));
        assertEquals("parent1", hostDimensions.get(ExternalMetrics.PARENT_HOSTNAME_DIMENSION));
        assertEquals("8.4.0", hostDimensions.get(ExternalMetrics.OS_VERSION_DIMENSION));
        assertFalse(hostDimensions.containsKey(toDimensionId("role")));
        assertFalse(hostDimensions.containsKey(toDimensionId("state")));
        assertFalse(hostDimensions.containsKey(toDimensionId("zone")));
    }

    @Test
    public void osVersion_is_stripped_from_carrier_packets_but_host_and_parentHostname_kept() {
        MetricsConsumers noConsumers = new MetricsConsumers(new ConsumersConfig.Builder().build());
        ExternalMetrics externalMetrics = new ExternalMetrics(noConsumers);
        externalMetrics.setExtraMetrics(List.of(
                new MetricsPacket.Builder(toServiceId("vespa.node"))
                        .putDimension(ExternalMetrics.HOST_DIMENSION, "host1")
                        .putDimension(ExternalMetrics.PARENT_HOSTNAME_DIMENSION, "parent1")
                        .putDimension(ExternalMetrics.OS_VERSION_DIMENSION, "8.4.0")
                        .putDimension(toDimensionId("role"), "tenants")));

        Map<DimensionId, String> dims = externalMetrics.getMetrics().get(0).build().dimensions();
        assertEquals("host1", dims.get(ExternalMetrics.HOST_DIMENSION));
        assertEquals("parent1", dims.get(ExternalMetrics.PARENT_HOSTNAME_DIMENSION));
        assertEquals("tenants", dims.get(toDimensionId("role")));
        assertFalse(dims.containsKey(ExternalMetrics.OS_VERSION_DIMENSION));
    }

    @Test
    public void osVersion_is_kept_on_host_life_packets() {
        MetricsConsumers noConsumers = new MetricsConsumers(new ConsumersConfig.Builder().build());
        ExternalMetrics externalMetrics = new ExternalMetrics(noConsumers);
        externalMetrics.setExtraMetrics(List.of(
                new MetricsPacket.Builder(toServiceId("host_life"))
                        .putDimension(ExternalMetrics.HOST_DIMENSION, "host1")
                        .putDimension(ExternalMetrics.PARENT_HOSTNAME_DIMENSION, "parent1")
                        .putDimension(ExternalMetrics.OS_VERSION_DIMENSION, "8.4.0")));

        Map<DimensionId, String> dims = externalMetrics.getMetrics().get(0).build().dimensions();
        assertEquals("8.4.0", dims.get(ExternalMetrics.OS_VERSION_DIMENSION));
        assertEquals("host1", dims.get(ExternalMetrics.HOST_DIMENSION));
        assertEquals("parent1", dims.get(ExternalMetrics.PARENT_HOSTNAME_DIMENSION));
    }

}
