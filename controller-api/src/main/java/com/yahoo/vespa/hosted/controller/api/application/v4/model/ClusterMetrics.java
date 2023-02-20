// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import java.util.Map;
import java.util.Optional;

/**
 * @author olaa
 */
public class ClusterMetrics {

    // These field names originate from the DeploymentMetricsResponse class
    public static final String QUERIES_PER_SECOND = "queriesPerSecond";
    public static final String FEED_PER_SECOND = "feedPerSecond";
    public static final String DOCUMENT_COUNT = "documentCount";
    public static final String FEED_LATENCY = "feedLatency";
    public static final String QUERY_LATENCY  = "queryLatency";
    public static final String MEMORY_UTIL = "memoryUtil";
    public static final String MEMORY_FEED_BLOCK_LIMIT = "memoryFeedBlockLimit";
    public static final String DISK_UTIL = "diskUtil";
    public static final String DISK_FEED_BLOCK_LIMIT = "diskFeedBlockLimit";

    private final String clusterId;
    private final String clusterType;
    private final Map<String, Double> metrics;

    public ClusterMetrics(String clusterId, String clusterType, Map<String, Double> metrics) {
        this.clusterId = clusterId;
        this.clusterType = clusterType;
        this.metrics = Map.copyOf(metrics);
    }

    public String getClusterId() {
        return clusterId;
    }

    public String getClusterType() {
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

    public Optional<Double> memoryUtil() {
        return Optional.ofNullable(metrics.get(MEMORY_UTIL));
    }

    public Optional<Double> memoryFeedBlockLimit() {
        return Optional.ofNullable(metrics.get(MEMORY_FEED_BLOCK_LIMIT));
    }

    public Optional<Double> diskUtil() {
        return Optional.ofNullable(metrics.get(DISK_UTIL));
    }

    public Optional<Double> diskFeedBlockLimit() {
        return Optional.ofNullable(metrics.get(DISK_FEED_BLOCK_LIMIT));
    }
}
