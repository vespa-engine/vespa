// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model;

import ai.vespa.metricsproxy.metric.Metric;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
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
                .addConsumers(Collections.singleton(DUPLICATE_CONSUMER))
                .addConsumers(Collections.singleton(DUPLICATE_CONSUMER))
                .build();
        assertEquals(1, packet.consumers().size());
    }

    @Test
    public void builder_can_retain_subset_of_metrics() {
        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo"))
                .putMetrics(ImmutableList.of(
                        new Metric("remove", 1),
                        new Metric("keep", 2)))
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

        Map<MetricId, List<String>> outputNamesById = ImmutableMap.of(
                toMetricId(ONE),          ImmutableList.of(ONE),
                toMetricId(TWO),          ImmutableList.of(TWO, "dos"),
                toMetricId(THREE),        ImmutableList.of("3"),
                toMetricId(NON_EXISTENT), ImmutableList.of(NON_EXISTENT));

        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo"))
                .putMetrics(ImmutableList.of(
                        new Metric(ONE, 1),
                        new Metric(TWO, 2),
                        new Metric(THREE, 3)))
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
