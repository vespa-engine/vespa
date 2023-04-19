// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import ai.vespa.metricsproxy.core.VespaMetrics;
import ai.vespa.metricsproxy.http.ValuesFetcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.yahoo.vespa.model.admin.monitoring.AutoscalingMetrics.autoscalingMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.DefaultMetrics.defaultMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.NetworkMetrics.networkMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.SystemMetrics.systemMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.VespaMetricSet.vespaMetricSet;
import static java.util.Collections.unmodifiableList;

/**
 * A metric consumer is a set of metrics given an id that can be requested at runtime.
 *
 * @author Trygve Berdal
 * @author gjoranv
 */
// TODO: This construct seems redundant when we have metrics sets
public class MetricsConsumer {

    // Pre-defined consumers
    // See also ConsumersConfigGenerator and MetricsBuilder where these must be enumerated
    public static final MetricsConsumer vespa =
            consumer(VespaMetrics.vespaMetricsConsumerId.id, vespaMetricSet, systemMetricSet, networkMetricSet);
    public static final MetricsConsumer defaultConsumer =
            consumer(ValuesFetcher.defaultMetricsConsumerId.id, defaultMetricSet, systemMetricSet);
    // Referenced from com.yahoo.vespa.hosted.provision.autoscale.NodeMetricsFetcher
    public static final MetricsConsumer autoscaling =
            consumer("autoscaling", autoscalingMetricSet);
    public static final MetricsConsumer vespaCloud =
            consumer("vespa-cloud", vespaMetricSet, systemMetricSet, networkMetricSet);

    private final String id;
    private final MetricSet metricSet;

    /**
     * @param id the consumer
     * @param metricSet the metrics for this consumer
     */
    public MetricsConsumer(String id, MetricSet metricSet) {
        this.id = Objects.requireNonNull(id, "A consumer must have a non-null id.");;
        this.metricSet = Objects.requireNonNull(metricSet, "A consumer must have a non-null metric set.");
    }

    public String id() {
        return id;
    }

    public MetricSet metricSet() { return metricSet; }

    /**
     * @return map of metric with metric name as key
     */
    public Map<String, Metric> metrics() {
        return metricSet.getMetrics();
    }

    private static MetricsConsumer consumer(String id, MetricSet ... metricSets) {
        return new MetricsConsumer(id, new MetricSet(id + "-consumer-metrics", List.of(), Arrays.asList(metricSets)));
    }

}
