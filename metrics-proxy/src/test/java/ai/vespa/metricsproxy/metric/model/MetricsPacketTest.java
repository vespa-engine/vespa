// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model;

import ai.vespa.metricsproxy.metric.Metric;
import org.junit.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
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
                    .timestamp(Instant.EPOCH)
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
                .addConsumers(Set.of(DUPLICATE_CONSUMER))
                .addConsumers(Set.of(DUPLICATE_CONSUMER))
                .build();
        assertEquals(1, packet.consumers().size());
    }

    @Test
    public void builder_allows_inspecting_consumers() {
        var consumer = toConsumerId("my-consumer");
        var builder = new MetricsPacket.Builder(toServiceId("foo"))
                .statusCode(0)
                .statusMessage("")
                .addConsumers(Set.of(consumer));
        assertTrue(builder.hasConsumer(consumer));
    }

    @Test
    public void builder_can_retain_subset_of_metrics() {
        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo"))
                .putMetrics(List.of(
                        new Metric(toMetricId("remove"), 1),
                        new Metric(toMetricId("keep"), 2)))
                .retainMetrics(Set.of(toMetricId("keep"), toMetricId("non-existent")))
                .build();

        assertFalse("should not contain 'remove'", packet.hasMetric(toMetricId("remove")));
        assertTrue("should contain 'keep'", packet.hasMetric(toMetricId("keep")));
        assertFalse("should not contain 'non-existent'", packet.hasMetric(toMetricId("non-existent")));
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

        Map<MetricId, List<MetricId>> outputNamesById = Map.of(
                ONE_ID,          List.of(ONE_ID),
                TWO_ID,          List.of(TWO_ID, toMetricId("dos")),
                THREE_ID,        List.of(toMetricId("3")),
                NON_EXISTENT_ID, List.of(NON_EXISTENT_ID));

        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo"))
                .putMetrics(List.of(
                        new Metric(ONE_ID, 1),
                        new Metric(TWO_ID, 2),
                        new Metric(THREE_ID, 3)))
                .applyOutputNames(outputNamesById)
                .build();

        // Only original name
        assertTrue(packet.hasMetric(ONE_ID));

        // Both names
        assertTrue(packet.hasMetric(TWO_ID));
        assertTrue(packet.hasMetric(toMetricId("dos")));

        // Only new name
        assertFalse(packet.hasMetric(THREE_ID));
        assertTrue(packet.hasMetric(toMetricId("3")));

        // Non-existent metric not added
        assertFalse(packet.hasMetric(NON_EXISTENT_ID));
    }

    @Test
    public void metrics_and_dimensions_keep_insertion_order_and_are_found_by_id() {
        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo"))
                .putMetric(toMetricId("b"), 2L)
                .putMetric(toMetricId("a"), 1.5)
                .putDimension(toDimensionId("y"), "yv")
                .putDimension(toDimensionId("x"), "xv")
                .build();

        assertEquals(2, packet.metricCount());
        assertEquals(toMetricId("b"), packet.metricId(0));
        assertEquals(2.0, packet.metricValue(0), 0);
        assertEquals(1, packet.metricIndex(toMetricId("a")));
        assertEquals(1.5, packet.metricValue(packet.metricIndex(toMetricId("a"))), 0);
        assertEquals(-1, packet.metricIndex(toMetricId("non-existent")));

        assertEquals(2, packet.dimensionCount());
        assertEquals(toDimensionId("y"), packet.dimensionId(0));
        assertEquals("xv", packet.dimension(toDimensionId("x")));
        assertNull(packet.dimension(toDimensionId("non-existent")));

        var metrics = new LinkedHashMap<MetricId, Double>();
        packet.forEachMetric(metrics::put);
        assertEquals(List.of(toMetricId("b"), toMetricId("a")), List.copyOf(metrics.keySet()));
        var dimensions = new LinkedHashMap<DimensionId, String>();
        packet.forEachDimension(dimensions::put);
        assertEquals(List.of(toDimensionId("y"), toDimensionId("x")), List.copyOf(dimensions.keySet()));

        assertEquals(List.of(2.0, 1.5), List.copyOf(metrics.values()));
        assertEquals(List.of("yv", "xv"), List.copyOf(dimensions.values()));
    }

    @Test
    public void packet_without_metrics_or_dimensions() {
        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo")).build();
        assertEquals(0, packet.metricCount());
        assertEquals(0, packet.dimensionCount());
        packet.forEachMetric((id, value) -> fail("no metrics expected"));
        packet.forEachDimension((id, value) -> fail("no dimensions expected"));
    }

    @Test
    public void packets_built_with_same_sharing_share_equal_values() {
        var sharing = new MetricsPacket.Sharing();
        MetricsPacket first = packetWithFreshValues().build(sharing);
        MetricsPacket second = packetWithFreshValues().build(sharing);
        assertSame(first.dimension(toDimensionId("host")), second.dimension(toDimensionId("host")));
        assertSame(first.service(), second.service());
        assertSame(first.timestamp(), second.timestamp());
        assertSame(first.statusMessage(), second.statusMessage());

        MetricsPacket unshared = packetWithFreshValues().build();
        assertNotSame(first.dimension(toDimensionId("host")), unshared.dimension(toDimensionId("host")));
        assertEquals(first.dimension(toDimensionId("host")), unshared.dimension(toDimensionId("host")));
    }

    private static MetricsPacket.Builder packetWithFreshValues() {
        return new MetricsPacket.Builder(toServiceId(new String("service")))
                .statusMessage(new String("up"))
                .timestamp(Instant.ofEpochSecond(1000))
                .putMetric(toMetricId("a"), 1)
                .putDimension(toDimensionId("host"), new String("host1"));
    }

    @Test
    public void metric_without_value_is_rejected() {
        var builder = new MetricsPacket.Builder(toServiceId("foo"));
        var e = assertThrows(NullPointerException.class, () -> builder.putMetric(toMetricId("a"), null));
        assertEquals("No value for metric a", e.getMessage());
    }

    @Test
    public void to_string_lists_metrics_and_dimensions() {
        MetricsPacket packet = new MetricsPacket.Builder(toServiceId("foo"))
                .putMetric(toMetricId("a"), 1)
                .putMetric(toMetricId("b"), 2.5)
                .putDimension(toDimensionId("x"), "xv")
                .build();
        assertEquals("MetricsPacket{statusCode=0, statusMessage='', timestamp=1970-01-01T00:00:00Z, service=foo, " +
                     "metrics={a=1.0,b=2.5}, dimensions={x=xv}, consumers=[]}",
                     packet.toString());
    }

}
