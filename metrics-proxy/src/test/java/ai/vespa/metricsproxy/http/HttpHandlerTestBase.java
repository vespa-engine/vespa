// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.service.DownService;
import ai.vespa.metricsproxy.service.DummyService;
import ai.vespa.metricsproxy.service.VespaService;
import ai.vespa.metricsproxy.service.VespaServices;
import com.google.common.collect.ImmutableList;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;

import java.time.Instant;
import java.util.List;

import static ai.vespa.metricsproxy.http.ValuesFetcher.defaultMetricsConsumerId;
import static ai.vespa.metricsproxy.metric.ExternalMetrics.VESPA_NODE_SERVICE_ID;
import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.REASON;
import static ai.vespa.metricsproxy.service.DummyService.METRIC_1;

/**
 * Base class for http handler tests.
 *
 * @author gjoranv
 */
@SuppressWarnings("UnstableApiUsage")
public class HttpHandlerTestBase {

    protected static final List<VespaService> testServices = ImmutableList.of(
            new DummyService(0, ""),
            new DummyService(1, ""),
            new DownService(HealthMetric.getDown("No response")));

    protected static final VespaServices vespaServices = new VespaServices(testServices);

    protected static final String DEFAULT_CONSUMER = "default";
    protected static final String CUSTOM_CONSUMER = "custom-consumer";

    protected static final String CPU_METRIC = "cpu";

    protected static final String URI_BASE = "http://localhost";

    protected static RequestHandlerTestDriver testDriver;


    protected static MetricsManager getMetricsManager() {
        MetricsManager metricsManager = TestUtil.createMetricsManager(vespaServices, getMetricsConsumers(), getApplicationDimensions(), getNodeDimensions());
        metricsManager.setExtraMetrics(ImmutableList.of(
                new MetricsPacket.Builder(VESPA_NODE_SERVICE_ID)
                        .timestamp(Instant.now().getEpochSecond())
                        .putMetrics(ImmutableList.of(new Metric(MetricId.toMetricId(CPU_METRIC), 12.345)))));
        return metricsManager;
    }

    protected static MetricsConsumers getMetricsConsumers() {
        // Must use a whitelisted dimension to avoid it being removed for the MetricsV2Handler
        var defaultConsumerDimension = new ConsumersConfig.Consumer.Metric.Dimension.Builder()
                .key(REASON).value("default-val");

        var customConsumerDimension = new ConsumersConfig.Consumer.Metric.Dimension.Builder()
                .key(REASON).value("custom-val");

        return new MetricsConsumers(new ConsumersConfig.Builder()
                                            .consumer(new ConsumersConfig.Consumer.Builder()
                                                              .name(defaultMetricsConsumerId.id)
                                                              .metric(new ConsumersConfig.Consumer.Metric.Builder()
                                                                              .name(CPU_METRIC)
                                                                              .outputname(CPU_METRIC))
                                                              .metric(new ConsumersConfig.Consumer.Metric.Builder()
                                                                              .name(METRIC_1)
                                                                              .outputname(METRIC_1)
                                                                              .dimension(defaultConsumerDimension)))
                                            .consumer(new ConsumersConfig.Consumer.Builder()
                                                              .name(CUSTOM_CONSUMER)
                                                              .metric(new ConsumersConfig.Consumer.Metric.Builder()
                                                                              .name(METRIC_1)
                                                                              .outputname(METRIC_1)
                                                                              .dimension(customConsumerDimension)))
                                            .build());
    }

    protected static ApplicationDimensions getApplicationDimensions() {
        return new ApplicationDimensions(new ApplicationDimensionsConfig.Builder().build());
    }

    protected static NodeDimensions getNodeDimensions() {
        return new NodeDimensions(new NodeDimensionsConfig.Builder().build());
    }

}
