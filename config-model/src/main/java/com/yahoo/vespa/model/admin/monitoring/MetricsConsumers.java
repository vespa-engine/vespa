// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import ai.vespa.metricsproxy.core.VespaMetrics;
import ai.vespa.metricsproxy.http.ValuesFetcher;

import java.util.Arrays;
import java.util.List;

import static com.yahoo.vespa.model.admin.monitoring.DefaultMetrics.defaultMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.NetworkMetrics.networkMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.SystemMetrics.systemMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.VespaMetricSet.vespaMetricSet;

/**
 * Built-in metric consumers
 *
 * @author bratseth
 */
public class MetricsConsumers {

    public static final String defaultMetricsConsumerId = ValuesFetcher.defaultMetricsConsumerId.id;
    public static final String vespaMetricsConsumerId = VespaMetrics.vespaMetricsConsumerId.id;

    public static MetricsConsumer defaultMetricsConsumer() {
        return consumer("default-consumer-metrics", defaultMetricSet, systemMetricSet);
    }

    public static MetricsConsumer vespaMetricsConsumer() {
        return consumer("vespa-consumer-metrics", vespaMetricSet, systemMetricSet, networkMetricSet);
    }

    private static MetricsConsumer consumer(String id, MetricSet ... metricSets) {
        return new MetricsConsumer(vespaMetricsConsumerId, new MetricSet(id, List.of(), Arrays.asList(metricSets)));
    }

}
