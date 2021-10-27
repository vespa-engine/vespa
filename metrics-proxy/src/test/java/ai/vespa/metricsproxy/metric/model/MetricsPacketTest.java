// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model;

import ai.vespa.metricsproxy.metric.Metric;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author gjoranv
 */
public class MetricsPacketTest {

    @Test
    public void service_cannot_be_null() {
        try {
            MetricsPacket packet = new MetricsPacket.Builder(null)
                    .statusCode(0)
                    .statusMessage("")
                    .timestamp(0L)
                    .build();
            fail("Expected exception due to null service.");
        } catch (Exception e) {
            assertEquals("Service cannot be null.", e.getMessage());
        }
    }

    @Test
    public void consumers_are_always_distinct() {
        ConsumerId DUPLICATE_CONSUMER = toConsumerId("duplicateConsumer");

        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo"))
                .statusCode(0)
                .statusMessage("")
                .addConsumers(singleton(DUPLICATE_CONSUMER))
                .addConsumers(singleton(DUPLICATE_CONSUMER))
                .build();
        assertEquals(1, packet.consumers().size());
    }

    @Test
    public void builder_allows_inspecting_consumers() {
        var consumer = toConsumerId("my-consumer");
        var builder = new MetricsPacket.Builder(toServiceId("foo"))
                .statusCode(0)
                .statusMessage("")
                .addConsumers(singleton(consumer));
        assertTrue(builder.hasConsumer(consumer));
    }

    @Test
    public void builder_can_retain_subset_of_metrics() {
        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo"))
                .putMetrics(ImmutableList.of(
                        new Metric(toMetricId("remove"), 1),
                        new Metric(toMetricId("keep"), 2)))
                .retainMetrics(ImmutableSet.of(toMetricId("keep"), toMetricId("non-existent")))
                .build();

        assertFalse("should not contain 'remove'", packet.metrics().containsKey(toMetricId("remove")));
        assertTrue("should contain 'keep'", packet.metrics().containsKey(toMetricId("keep")));
        assertFalse("should not contain 'non-existent'", packet.metrics().containsKey(toMetricId("non-existent")));
    }

    @Test
    public void builder_applies_output_names() {
        String ONE          = "one";
        String TWO          = "two";
        String THREE        = "three";
        String NON_EXISTENT = "non-existent";
        MetricId ONE_ID          = toMetricId(ONE);
        MetricId TWO_ID          = toMetricId(TWO);
        MetricId THREE_ID        = toMetricId(THREE);
        MetricId NON_EXISTENT_ID = toMetricId(NON_EXISTENT);

        Map<MetricId, List<MetricId>> outputNamesById = ImmutableMap.of(
                ONE_ID,          ImmutableList.of(ONE_ID),
                TWO_ID,          ImmutableList.of(TWO_ID, toMetricId("dos")),
                THREE_ID,        ImmutableList.of(toMetricId("3")),
                NON_EXISTENT_ID, ImmutableList.of(NON_EXISTENT_ID));

        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo"))
                .putMetrics(ImmutableList.of(
                        new Metric(ONE_ID, 1),
                        new Metric(TWO_ID, 2),
                        new Metric(THREE_ID, 3)))
                .applyOutputNames(outputNamesById)
                .build();

        // Only original name
        assertTrue(packet.metrics().containsKey(ONE_ID));

        // Both names
        assertTrue(packet.metrics().containsKey(TWO_ID));
        assertTrue(packet.metrics().containsKey(toMetricId("dos")));

        // Only new name
        assertFalse(packet.metrics().containsKey(THREE_ID));
        assertTrue(packet.metrics().containsKey(toMetricId("3")));

        // Non-existent metric not added
        assertFalse(packet.metrics().containsKey(NON_EXISTENT_ID));
    }

}
