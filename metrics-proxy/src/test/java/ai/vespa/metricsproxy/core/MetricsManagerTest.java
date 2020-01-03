// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import ai.vespa.metricsproxy.service.DownService;
import ai.vespa.metricsproxy.service.DummyService;
import ai.vespa.metricsproxy.service.VespaService;
import ai.vespa.metricsproxy.service.VespaServices;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static ai.vespa.metricsproxy.core.MetricsManager.VESPA_VERSION;
import static ai.vespa.metricsproxy.core.VespaMetrics.METRIC_TYPE_DIMENSION_ID;
import static ai.vespa.metricsproxy.core.VespaMetrics.VESPA_CONSUMER_ID;
import static ai.vespa.metricsproxy.metric.ExternalMetrics.ROLE_DIMENSION;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class MetricsManagerTest {

    private MetricsManager metricsManager;

    private static final String SERVICE_0_ID = "dummy/id/0";
    private static final String SERVICE_1_ID = "dummy/id/1";

    private static final List<VespaService> testServices = ImmutableList.of(
            new DummyService(0, SERVICE_0_ID),
            new DummyService(1, SERVICE_1_ID));

    private static final String WHITELISTED_METRIC_ID = "whitelisted";

    @Before
    public void setupMetricsManager() {
        metricsManager = TestUtil.createMetricsManager(new VespaServices(testServices), getMetricsConsumers(),
                                                       getApplicationDimensions(), getNodeDimensions());
    }

    @Test
    public void service_that_is_down_has_a_separate_metrics_packet() {
        // Reset to use only the service that is down
        var downService = new DownService(HealthMetric.getDown("No response"));
        List<VespaService> testServices = Collections.singletonList(downService);
        MetricsManager metricsManager = TestUtil.createMetricsManager(new VespaServices(testServices),
                                                                      getMetricsConsumers(),getApplicationDimensions(), getNodeDimensions());

        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        assertThat(packets.size(), is(1));
        assertTrue(packets.get(0).metrics().isEmpty());
        assertThat(packets.get(0).dimensions().get(toDimensionId("instance")), is(DownService.NAME));
        assertThat(packets.get(0).dimensions().get(toDimensionId("global")), is("value"));
    }

    @Test
    public void each_service_gets_separate_metrics_packets() {
        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        assertThat(packets.size(), is(2));

        assertThat(packets.get(0).dimensions().get(toDimensionId("instance")), is("dummy0"));
        assertThat(packets.get(0).metrics().get(toMetricId("c.test")), is(1.0));
        assertThat(packets.get(0).metrics().get(toMetricId("val")), is(1.05));

        assertThat(packets.get(1).dimensions().get(toDimensionId("instance")), is("dummy1"));
        assertThat(packets.get(1).metrics().get(toMetricId("c.test")), is(6.0));
        assertThat(packets.get(1).metrics().get(toMetricId("val")), is(2.35));
    }

    @Test
    public void verify_expected_output_from_getMetricsById() {
        String dummy0Metrics = metricsManager.getMetricsByConfigId(SERVICE_0_ID);
        assertThat(dummy0Metrics, containsString("'dummy.id.0'.val=1.050"));
        assertThat(dummy0Metrics, containsString("'dummy.id.0'.c_test=1"));

        String dummy1Metrics = metricsManager.getMetricsByConfigId(SERVICE_1_ID);
        assertThat(dummy1Metrics, containsString("'dummy.id.1'.val=2.350"));
        assertThat(dummy1Metrics, containsString("'dummy.id.1'.c_test=6"));
    }

    @Test
    public void getServices_returns_service_types() {
        assertThat(metricsManager.getAllVespaServices(), is("dummy"));
    }

    @Test
    public void global_dimensions_are_added_but_do_not_override_metric_dimensions() {
        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        assertEquals(2, packets.size());
        assertGlobalDimensions(packets.get(0).dimensions());
        assertGlobalDimensions(packets.get(1).dimensions());
    }

    private void assertGlobalDimensions(Map<DimensionId, String> dimensions) {
        assertTrue(dimensions.containsKey(VESPA_VERSION));
        assertEquals("value", dimensions.get(toDimensionId("global")));
        assertEquals("metric-dim", dimensions.get(toDimensionId("dim0")));
    }


    @Test
    public void system_metrics_are_added() {
        VespaService service0 = testServices.get(0);
        Metrics oldSystemMetrics = service0.getSystemMetrics();

        service0.getSystemMetrics().add(new Metric("cpu", 1));

        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        assertEquals(3, packets.size());

        MetricsPacket systemPacket = packets.get(0); // system metrics are added before other metrics
        assertThat(systemPacket.metrics().get(toMetricId("cpu")), is(1.0));
        assertThat(systemPacket.dimensions().get(toDimensionId("metrictype")), is("system"));

        service0.setSystemMetrics(oldSystemMetrics);
    }

    // TODO: test that non-whitelisted metrics are filtered out, but this is currently not the case, see ExternalMetrics.setExtraMetrics
    @Test
    public void extra_metrics_packets_containing_whitelisted_metrics_are_added() {
        metricsManager.setExtraMetrics(ImmutableList.of(
                new MetricsPacket.Builder(toServiceId("foo"))
                        .putMetrics(ImmutableList.of(new Metric(WHITELISTED_METRIC_ID, 0)))));

        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        assertThat(packets.size(), is(3));
    }

    @Test
    public void extra_metrics_packets_without_whitelisted_metrics_are_not_added() {
        metricsManager.setExtraMetrics(ImmutableList.of(
                new MetricsPacket.Builder(toServiceId("foo"))
                        .putMetrics(ImmutableList.of(new Metric("not-whitelisted", 0)))));

        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        assertThat(packets.size(), is(2));
    }

    @Test
    public void application_from_extra_metrics_packets_is_used_as_service_in_result_packets() {
        final ServiceId serviceId = toServiceId("custom-service");
        metricsManager.setExtraMetrics(ImmutableList.of(
                new MetricsPacket.Builder(serviceId)
                        .putMetrics(ImmutableList.of(new Metric(WHITELISTED_METRIC_ID, 0)))));

        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        MetricsPacket extraPacket = null;
        for (MetricsPacket packet : packets) {
            if (packet.service.equals(serviceId)) extraPacket = packet;
        }
        assertNotNull(extraPacket);
    }

    @Test
    public void extra_dimensions_are_added_to_metrics_packets_that_do_not_have_those_dimensions() {
        metricsManager.setExtraMetrics(ImmutableList.of(
                new MetricsPacket.Builder(toServiceId("foo"))
                        .putMetrics(ImmutableList.of(new Metric(WHITELISTED_METRIC_ID, 0)))
                        .putDimension(ROLE_DIMENSION, "role from extraMetrics")));

        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        for (MetricsPacket packet : packets) {
            assertThat(packet.dimensions().get(ROLE_DIMENSION), is("role from extraMetrics"));
        }
    }

    @Test
    public void extra_dimensions_do_not_overwrite_existing_dimension_values() {
        metricsManager.setExtraMetrics(ImmutableList.of(
                new MetricsPacket.Builder(toServiceId("foo"))
                        .putMetrics(ImmutableList.of(new Metric(WHITELISTED_METRIC_ID, 0)))
                        .putDimension(METRIC_TYPE_DIMENSION_ID, "from extraMetrics")));

        List<MetricsPacket> packets = metricsManager.getMetrics(testServices, Instant.EPOCH);
        assertThat(packets.get(0).dimensions().get(METRIC_TYPE_DIMENSION_ID), is("standard"));
        assertThat(packets.get(1).dimensions().get(METRIC_TYPE_DIMENSION_ID), is("standard"));
        assertThat(packets.get(2).dimensions().get(METRIC_TYPE_DIMENSION_ID), is("from extraMetrics"));
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
                                                              .name(VESPA_CONSUMER_ID.id)
                                                              .metric(new Consumer.Metric.Builder()
                                                                              .name(WHITELISTED_METRIC_ID)
                                                                              .outputname(WHITELISTED_METRIC_ID))
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
