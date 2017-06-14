// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import javax.annotation.concurrent.Immutable;
import java.util.Map;

/**
 * Represents an arbitrary metric consumer
 *
 * @author trygve
 */
@Immutable
public class MetricsConsumer {
    private final String id;
    private final MetricSet metricSet;

    /**
     * @param id The consumer
     * @param metricSet  The metrics for this consumer
     */
    public MetricsConsumer(String id, MetricSet metricSet) {
        this.id = id;
        this.metricSet = metricSet;
    }

    public String getId() {
        return id;
    }

    public MetricSet getMetricSet() { return metricSet; }

    /**
     * @return Map of metric with metric name as key
     */
    public Map<String, Metric> getMetrics() {
        return metricSet.getMetrics();
    }

}
