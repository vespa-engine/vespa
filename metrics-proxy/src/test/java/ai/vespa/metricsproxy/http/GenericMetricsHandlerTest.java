/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.http;

import ai.vespa.metricsproxy.TestUtil;
import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.metric.HealthMetric;
import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensionsConfig;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensionsConfig;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.json.GenericJsonModel;
import ai.vespa.metricsproxy.metric.model.json.GenericMetrics;
import ai.vespa.metricsproxy.metric.model.json.GenericService;
import ai.vespa.metricsproxy.service.DownService;
import ai.vespa.metricsproxy.service.DummyService;
import ai.vespa.metricsproxy.service.VespaService;
import ai.vespa.metricsproxy.service.VespaServices;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

import static ai.vespa.metricsproxy.core.VespaMetrics.INSTANCE_DIMENSION_ID;
import static ai.vespa.metricsproxy.http.GenericMetricsHandler.DEFAULT_PUBLIC_CONSUMER_ID;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static ai.vespa.metricsproxy.metric.model.StatusCode.DOWN;
import static ai.vespa.metricsproxy.metric.model.json.JacksonUtil.createObjectMapper;
import static ai.vespa.metricsproxy.service.DummyService.METRIC_1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author gjoranv
 */
@SuppressWarnings("UnstableApiUsage")
public class GenericMetricsHandlerTest {

    private static final List<VespaService> testServices = ImmutableList.of(
            new DummyService(0, ""),
            new DummyService(1, ""),
            new DownService(HealthMetric.getDown("No response")));

    private static final String CPU_METRIC = "cpu";

    private static final String URI = "http://localhost/metrics/v1/values";

    private static final VespaServices vespaServices = new VespaServices(testServices);

    private static RequestHandlerTestDriver testDriver;

    @BeforeClass
    public static void setup() {
        MetricsManager metricsManager = TestUtil.createMetricsManager(vespaServices, getMetricsConsumers(), getApplicationDimensions(), getNodeDimensions());
        metricsManager.setExtraMetrics(ImmutableList.of(
                new MetricsPacket.Builder(toServiceId("foo"))
                        .timestamp(Instant.now().getEpochSecond())
                        .putMetrics(ImmutableList.of(new Metric(CPU_METRIC, 12.345)))));
        GenericMetricsHandler handler = new GenericMetricsHandler(Executors.newSingleThreadExecutor(), metricsManager, vespaServices, getMetricsConsumers());
        testDriver = new RequestHandlerTestDriver(handler);
    }

    @Ignore
    @Test
    public void visually_inspect_response() throws Exception{
        String response = testDriver.sendRequest(URI).readAll();
        ObjectMapper mapper = createObjectMapper();
        var jsonModel = mapper.readValue(response, GenericJsonModel.class);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonModel));
    }

    @Test
    public void response_contains_node_metrics() throws Exception {
        String response = testDriver.sendRequest(URI).readAll();
        var jsonModel = createObjectMapper().readValue(response, GenericJsonModel.class);

        assertNotNull(jsonModel.node);
        assertEquals(1, jsonModel.node.metrics.size());
        assertEquals(12.345, jsonModel.node.metrics.get(0).values.get(CPU_METRIC), 0.0001d);
    }

    @Test
    public void response_contains_service_metrics() throws Exception {
        String response = testDriver.sendRequest(URI).readAll();
        var jsonModel = createObjectMapper().readValue(response, GenericJsonModel.class);

        assertEquals(2, jsonModel.services.size());
        GenericService dummyService = jsonModel.services.get(0);
        assertEquals(2, dummyService.metrics.size());

        GenericMetrics dummy0Metrics = getMetricsForInstance("dummy0", dummyService);
        assertEquals(1L, dummy0Metrics.values.get(METRIC_1).longValue());
        assertEquals("metric-dim", dummy0Metrics.dimensions.get("dim0"));

        GenericMetrics dummy1Metrics = getMetricsForInstance("dummy1", dummyService);
        assertEquals(6L, dummy1Metrics.values.get(METRIC_1).longValue());
        assertEquals("metric-dim", dummy1Metrics.dimensions.get("dim0"));
    }

    @Test
    public void response_contains_health_from_service_that_is_down() throws Exception {
        String response = testDriver.sendRequest(URI).readAll();
        var jsonModel = createObjectMapper().readValue(response, GenericJsonModel.class);

        GenericService downService = jsonModel.services.get(1);
        assertEquals(DOWN.status, downService.status.code);
        assertEquals("No response", downService.status.description);

        // Service should output metric dimensions, even without metrics, because they contain important info about the service.
        assertEquals(1, downService.metrics.size());
        assertEquals(0, downService.metrics.get(0).values.size());
        assertFalse(downService.metrics.get(0).dimensions.isEmpty());
        assertEquals(DownService.NAME, downService.metrics.get(0).dimensions.get(INSTANCE_DIMENSION_ID.id));
    }

    @Test
    public void all_timestamps_are_equal_and_non_zero() throws Exception {
        String response = testDriver.sendRequest(URI).readAll();
        var jsonModel = createObjectMapper().readValue(response, GenericJsonModel.class);

        Long nodeTimestamp = jsonModel.node.timestamp;
        assertNotEquals(0L, (long) nodeTimestamp);
        for (var service : jsonModel.services)
            assertEquals(nodeTimestamp, service.timestamp);
    }

    private static GenericMetrics getMetricsForInstance(String instance, GenericService service) {
        for (var metrics : service.metrics) {
            if (metrics.dimensions.get(INSTANCE_DIMENSION_ID.id).equals(instance))
                return metrics;
        }
        throw new RuntimeException("Could not find metrics for service instance " + instance);
    }

    private static MetricsConsumers getMetricsConsumers() {
        ConsumersConfig.Consumer.Metric.Dimension.Builder metricDimension = new ConsumersConfig.Consumer.Metric.Dimension.Builder()
                .key("dim0").value("metric-dim");

        return new MetricsConsumers(new ConsumersConfig.Builder()
                                            .consumer(new ConsumersConfig.Consumer.Builder()
                                                              .name(DEFAULT_PUBLIC_CONSUMER_ID.id)
                                                              .metric(new ConsumersConfig.Consumer.Metric.Builder()
                                                                              .name(CPU_METRIC)
                                                                              .outputname(CPU_METRIC))
                                                              .metric(new ConsumersConfig.Consumer.Metric.Builder()
                                                                              .name(METRIC_1)
                                                                              .outputname(METRIC_1)
                                                                              .dimension(metricDimension)))
                                            .build());
    }

    private static ApplicationDimensions getApplicationDimensions() {
        return new ApplicationDimensions(new ApplicationDimensionsConfig.Builder().build());
    }

    private static NodeDimensions getNodeDimensions() {
        return new NodeDimensions(new NodeDimensionsConfig.Builder().build());
    }

}
