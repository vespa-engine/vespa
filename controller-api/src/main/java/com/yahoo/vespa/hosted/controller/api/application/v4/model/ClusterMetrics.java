// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author olaa
 */
public class ClusterMetrics {

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

    public Map<String, Double> getMetrics() {
        return Collections.unmodifiableMap(metrics);
    }

    public void addMetric(String name, double value) {
        metrics.put(name, value);
    }

    public enum ClusterType {content, container};
}
