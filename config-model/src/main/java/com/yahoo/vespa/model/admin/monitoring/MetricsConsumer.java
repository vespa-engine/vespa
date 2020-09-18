// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import javax.annotation.concurrent.Immutable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.unmodifiableList;

/**
 * A metric consumer is a set of metrics given an id that can be requested at runtime.
 *
 * @author trygve
 * @author gjoranv
 */
// TODO: This construct seems redundant when we have metrics sets
@Immutable
public class MetricsConsumer {

    private final String id;
    private final MetricSet metricSet;

    private final List<CloudWatch> cloudWatches = new ArrayList<>();

    /**
     * @param id the consumer
     * @param metricSet the metrics for this consumer
     */
    public MetricsConsumer(String id, MetricSet metricSet) {
        this.id = Objects.requireNonNull(id, "A consumer must have a non-null id.");;
        this.metricSet = Objects.requireNonNull(metricSet, "A consumer must have a non-null metric set.");
    }

    public String getId() {
        return id;
    }

    public MetricSet getMetricSet() { return metricSet; }

    /**
     * @return map of metric with metric name as key
     */
    public Map<String, Metric> getMetrics() {
        return metricSet.getMetrics();
    }

    public void addCloudWatch(CloudWatch cloudWatch) {
        cloudWatches.add(cloudWatch);
    }

    public List<CloudWatch> cloudWatches() {
        return unmodifiableList(cloudWatches);
    }

}
