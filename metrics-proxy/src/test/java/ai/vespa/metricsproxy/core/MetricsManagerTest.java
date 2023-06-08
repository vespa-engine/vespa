// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.core;

import ai.vespa.metricsproxy.TestUtil;
import ai.vespa.metricsproxy.core.ConsumersConfig.Consumer;
import ai.vespa.metricsproxy.metric.HealthMetric;
import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensionsConfig;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensionsConfig;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import ai.vespa.metricsproxy.service.DownService;
import ai.vespa.metricsproxy.service.DummyService;
import ai.vespa.metricsproxy.service.VespaService;
import ai.vespa.metricsproxy.service.VespaServices;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static ai.vespa.metricsproxy.core.MetricsManager.VESPA_VERSION;
import static ai.vespa.metricsproxy.core.VespaMetrics.METRIC_TYPE_DIMENSION_ID;
import static ai.vespa.metricsproxy.core.VespaMetrics.vespaMetricsConsumerId;
import static ai.vespa.metricsproxy.metric.ExternalMetrics.ROLE_DIMENSION;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class MetricsManagerTest {

    private MetricsManager metricsManager;

    private static final String SERVICE_0_ID = "dummy/id/0";
    private static final String SERVICE_1_ID = "dummy/id/1";

    private static final List<VespaService> testServices = List.of(
            new DummyService(0, SERVICE_0_ID),
            new DummyService(1, SERVICE_1_ID));

    private static final MetricId WHITELISTED_METRIC_ID = toMetricId("whitelisted");

    @Before
    public void setupMetricsManager() {
        metricsManager = TestUtil.createMetricsManager(new VespaServices(testServices), getMetricsConsumers(),
                                                       getApplicationDimensions(), getNodeDimensions());
    }

    @Test
    public void service_that_is_down_has_a_separate_metrics_packet() {
        // Reset to use only the service that is down
        var downService = new DownService(HealthMetric.getDown("No response"));
        List<VespaService> testServices = List.of(downService);
        MetricsManager metricsManager = TestUtil.createMetricsManager(new VespaServices(testServices),
                                                                      getMetricsConsumers(),getApplicationDimensions(), getNodeDimensions());

        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        assertEquals(1, packets.size());
        assertTrue(packets.get(0).metrics().isEmpty());
        assertEquals(DownService.NAME, packets.get(0).dimensions().get(toDimensionId("instance")));
        assertEquals("value", packets.get(0).dimensions().get(toDimensionId("global")));
    }

    @Test
    public void each_service_gets_separate_metrics_packets() {
        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        assertEquals(2, packets.size());

        assertEquals("dummy0", packets.get(0).dimensions().get(toDimensionId("instance")));
        assertEquals(1, packets.get(0).metrics().get(toMetricId("c.test")));
        assertEquals(1.05, packets.get(0).metrics().get(toMetricId("val")));

        assertEquals("dummy1", packets.get(1).dimensions().get(toDimensionId("instance")));
        assertEquals(6, packets.get(1).metrics().get(toMetricId("c.test")));
        assertEquals(2.35, packets.get(1).metrics().get(toMetricId("val")));
    }

    @Test
    public void verify_expected_output_from_getMetricsById() {
        String dummy0Metrics = metricsManager.getMetricsByConfigId(SERVICE_0_ID);
        assertTrue(dummy0Metrics.contains("'dummy.id.0'.val=1.050"));
        assertTrue(dummy0Metrics.contains("'dummy.id.0'.c_test=1"));

        String dummy1Metrics = metricsManager.getMetricsByConfigId(SERVICE_1_ID);
        assertTrue(dummy1Metrics.contains("'dummy.id.1'.val=2.350"));
        assertTrue(dummy1Metrics.contains("'dummy.id.1'.c_test=6"));
    }

    @Test
    public void getServices_returns_service_types() {
        assertEquals("dummy", metricsManager.getAllVespaServices());
    }

    @Test
    public void global_dimensions_are_added_but_do_not_override_metric_dimensions() {
        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        assertEquals(2, packets.size());
        assertGlobalDimensions(packets.get(0).dimensions());
        assertGlobalDimensions(packets.get(1).dimensions());
    }

    private void assertGlobalDimensions(Map<DimensionId, String> dimensions) {
        assertEquals("value", dimensions.get(toDimensionId("global")));
        assertEquals("metric-dim", dimensions.get(toDimensionId("dim0")));
    }


    @Test
    public void system_metrics_are_added() {
        VespaService service0 = testServices.get(0);
        Metrics oldSystemMetrics = service0.getSystemMetrics();

        service0.getSystemMetrics().add(new Metric(toMetricId("cpu"), 1));

        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        assertEquals(3, packets.size());

        MetricsPacket systemPacket = packets.get(0); // system metrics are added before other metrics
        assertEquals(1, systemPacket.metrics().get(toMetricId("cpu")));
        assertEquals("system", systemPacket.dimensions().get(toDimensionId("metrictype")));

        service0.setSystemMetrics(oldSystemMetrics);
    }

    // TODO: test that non-whitelisted metrics are filtered out, but this is currently not the case, see ExternalMetrics.setExtraMetrics
    @Test
    public void extra_metrics_packets_containing_whitelisted_metrics_are_added() {
        metricsManager.setExtraMetrics(List.of(
                new MetricsPacket.Builder(toServiceId("foo"))
                        .putMetrics(List.of(new Metric(WHITELISTED_METRIC_ID, 0)))));

        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        assertEquals(3, packets.size());
    }

    @Test
    public void extra_metrics_packets_without_whitelisted_metrics_are_not_added() {
        metricsManager.setExtraMetrics(List.of(
                new MetricsPacket.Builder(toServiceId("foo"))
                        .putMetrics(List.of(new Metric(toMetricId("not-whitelisted"), 0)))));

        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        assertEquals(2, packets.size());
    }

    @Test
    public void application_from_extra_metrics_packets_is_used_as_service_in_result_packets() {
        final ServiceId serviceId = toServiceId("custom-service");
        metricsManager.setExtraMetrics(List.of(
                new MetricsPacket.Builder(serviceId)
                        .putMetrics(List.of(new Metric(WHITELISTED_METRIC_ID, 0)))));

        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        MetricsPacket extraPacket = null;
        for (MetricsPacket packet : packets) {
            if (packet.service.equals(serviceId)) extraPacket = packet;
        }
        assertNotNull(extraPacket);
    }

    @Test
    public void extra_dimensions_are_added_to_metrics_packets_that_do_not_have_those_dimensions() {
        metricsManager.setExtraMetrics(List.of(
                new MetricsPacket.Builder(toServiceId("foo"))
                        .putMetrics(List.of(new Metric(WHITELISTED_METRIC_ID, 0)))
                        .putDimension(ROLE_DIMENSION, "role from extraMetrics")));

        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        for (MetricsPacket packet : packets) {
            assertEquals("role from extraMetrics", packet.dimensions().get(ROLE_DIMENSION));
        }
    }

    @Test
    public void extra_dimensions_do_not_overwrite_existing_dimension_values() {
        metricsManager.setExtraMetrics(List.of(
                new MetricsPacket.Builder(toServiceId("foo"))
                        .putMetrics(List.of(new Metric(WHITELISTED_METRIC_ID, 0)))
                        .putDimension(METRIC_TYPE_DIMENSION_ID, "from extraMetrics")));

        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        assertEquals("standard", packets.get(0).dimensions().get(METRIC_TYPE_DIMENSION_ID));
        assertEquals("standard", packets.get(1).dimensions().get(METRIC_TYPE_DIMENSION_ID));
        assertEquals("from extraMetrics", packets.get(2).dimensions().get(METRIC_TYPE_DIMENSION_ID));
    }

    @Test
    public void timestamp_is_adjusted_when_metric_is_less_than_one_minute_younger_than_start_time() {
        Instant START_TIME = Instant.ofEpochSecond(0);
        Instant METRIC_TIME = Instant.ofEpochSecond(59);
        assertEquals(START_TIME, getAdjustedTimestamp(START_TIME, METRIC_TIME));
    }

    @Test
    public void timestamp_is_adjusted_when_metric_is_less_than_one_minute_older_than_start_time() {
        Instant START_TIME = Instant.ofEpochSecond(59);
        Instant METRIC_TIME = Instant.ofEpochSecond(0);
        assertEquals(START_TIME, getAdjustedTimestamp(START_TIME, METRIC_TIME));
    }

    @Test
    public void timestamp_is_not_adjusted_when_metric_is_at_least_one_minute_younger_than_start_time() {
        Instant START_TIME = Instant.ofEpochSecond(0);
        Instant METRIC_TIME = Instant.ofEpochSecond(60);
        assertEquals(METRIC_TIME, getAdjustedTimestamp(START_TIME, METRIC_TIME));
    }

    @Test
    public void timestamp_is_not_adjusted_when_metric_is_at_least_one_minute_older_than_start_time() {
        Instant START_TIME = Instant.ofEpochSecond(60);
        Instant METRIC_TIME = Instant.ofEpochSecond(0);
        assertEquals(METRIC_TIME, getAdjustedTimestamp(START_TIME, METRIC_TIME));
    }

    private Instant getAdjustedTimestamp(Instant startTime, Instant metricTime) {
        MetricsPacket.Builder builder = new MetricsPacket.Builder(toServiceId("foo"))
                .timestamp(metricTime.getEpochSecond());
        return MetricsManager.adjustTimestamp(builder, startTime).getTimestamp();
    }

    private static MetricsConsumers getMetricsConsumers() {
        Consumer.Metric.Dimension.Builder metricDimension = new Consumer.Metric.Dimension.Builder()
                .key("dim0").value("metric-dim");

        return new MetricsConsumers(new ConsumersConfig.Builder()
                                            .consumer(new Consumer.Builder()
                                                              .name(vespaMetricsConsumerId.id)
                                                              .metric(new Consumer.Metric.Builder()
                                                                              .name(WHITELISTED_METRIC_ID.id)
                                                                              .outputname(WHITELISTED_METRIC_ID.id))
                                                              .metric(new Consumer.Metric.Builder()
                                                                              .name(DummyService.METRIC_1)
                                                                              .outputname(DummyService.METRIC_1)
                                                                              .dimension(metricDimension))
                                                              .metric(new Consumer.Metric.Builder()
                                                                              .name(DummyService.METRIC_2)
                                                                              .outputname(DummyService.METRIC_2)
                                                                              .dimension(metricDimension)))
                                            .build());
    }

    private ApplicationDimensions getApplicationDimensions() {
        return new ApplicationDimensions(new ApplicationDimensionsConfig.Builder()
                                                 .dimensions("global", "value").build());
    }

    private NodeDimensions getNodeDimensions() {
        return new NodeDimensions(new NodeDimensionsConfig.Builder()
                                                 .dimensions("dim0", "should not override metric dim").build());
    }

}
