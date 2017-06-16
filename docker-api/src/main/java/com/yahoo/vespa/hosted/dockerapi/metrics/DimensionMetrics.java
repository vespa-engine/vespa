// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author freva
 */
public class DimensionMetrics {
    private final static ObjectMapper objectMapper = new ObjectMapper();

    private final String application;
    private final Dimensions dimensions;
    private final Map<String, Number> metrics;

    DimensionMetrics(String application, Dimensions dimensions, Map<String, Number> metrics) {
        this.application = application;
        this.dimensions = dimensions;
        this.metrics = metrics;
    }

    Map<String, Object> getMetrics() {
        final Map<String, Object> routing = new HashMap<>();
        final Map<String, Object> routingYamas = new HashMap<>();
        routing.put("yamas", routingYamas);
        routingYamas.put("namespaces", Collections.singletonList("Vespa"));

        Map<String, Object> report = new HashMap<>();
        report.put("application", application);
        report.put("dimensions", dimensions.dimensionsMap);
        report.put("metrics", metrics);
        report.put("routing", routing);
        return report;
    }

    public String toSecretAgentReport() throws JsonProcessingException {
        Map<String, Object> report = getMetrics();
        report.put("timestamp", System.currentTimeMillis() / 1000);

        return objectMapper.writeValueAsString(report);
    }

    public static class Builder {
        private final String application;
        private final Dimensions dimensions;
        private final Map<String, Number> metrics = new HashMap<>();

        public Builder(String application, Dimensions dimensions) {
            this.application = application;
            this.dimensions = dimensions;
        }

        public Builder withMetric(String metricName, Number metricValue) {
            metrics.put(metricName, metricValue);
            return this;
        }

        public DimensionMetrics build() {
            return new DimensionMetrics(application, dimensions, metrics);
        }
    }
}
