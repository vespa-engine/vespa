// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import java.util.Map;

/**
 * Represents an arbitrary metric consumer
 *
 * @author trygve
 */
public class MetricsConsumer {
    private final String consumer;
    private final Map<String, Metric> metrics;

    /**
     * @param consumer The consumer
     * @param metrics  The metrics for the the consumer
     */
    public MetricsConsumer(String consumer, Map<String, Metric> metrics) {
        this.consumer = consumer;
        this.metrics = metrics;
    }

    public String getConsumer() {
        return consumer;
    }

    /**
     * @return Map of metric with metric name as key
     */
    public Map<String, Metric> getMetrics() {
        return metrics;
    }

}
