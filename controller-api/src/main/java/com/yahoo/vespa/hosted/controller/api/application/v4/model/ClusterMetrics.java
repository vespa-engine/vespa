// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author olaa
 */
public class ClusterMetrics {

    // These field names originate from the MetricsResponse class
    private static final String QUERIES_PER_SECOND = "queriesPerSecond";
    private static final String FEED_PER_SECOND = "feedPerSecond";
    private static final String DOCUMENT_COUNT = "documentCount";
    private static final String FEED_LATENCY = "feedLatency";
    private static final String QUERY_LATENCY  = "queryLatency";

    private final String clusterId;
    private final ClusterType clusterType;
    private final Map<String, Double> metrics;

    public ClusterMetrics(String clusterId, ClusterType clusterType) {
        this.clusterId = clusterId;
        this.clusterType = clusterType;
        this.metrics = new HashMap<>();
    }

    public String getClusterId() {
        return clusterId;
    }

    public ClusterType getClusterType() {
        return clusterType;
    }

    public Optional<Double> queriesPerSecond() {
        return Optional.ofNullable(metrics.get(QUERIES_PER_SECOND));
    }

    public Optional<Double> feedPerSecond() {
        return Optional.ofNullable(metrics.get(FEED_PER_SECOND));
    }

    public Optional<Double> documentCount() {
        return Optional.ofNullable(metrics.get(DOCUMENT_COUNT));
    }

    public Optional<Double> feedLatency() {
        return Optional.ofNullable(metrics.get(FEED_LATENCY));
    }

    public Optional<Double> queryLatency() {
        return Optional.ofNullable(metrics.get(QUERY_LATENCY));
    }

    public void addMetric(String name, double value) {
        metrics.put(name, value);
    }

    public enum ClusterType {content, container};
}
