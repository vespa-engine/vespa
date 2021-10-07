// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author freva
 */
public class DimensionMetrics {

    private final String application;
    private final Dimensions dimensions;
    private final Map<String, Number> metrics;

    DimensionMetrics(String application, Dimensions dimensions, Map<String, Number> metrics) {
        this.application = Objects.requireNonNull(application);
        this.dimensions = Objects.requireNonNull(dimensions);
        this.metrics = metrics.entrySet().stream()
                .filter(DimensionMetrics::metricIsFinite)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public String getApplication() {
        return application;
    }

    public Dimensions getDimensions() {
        return dimensions;
    }

    public Map<String, Number> getMetrics() {
        return metrics;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DimensionMetrics that = (DimensionMetrics) o;
        return application.equals(that.application) &&
                dimensions.equals(that.dimensions) &&
                metrics.equals(that.metrics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(application, dimensions, metrics);
    }

    private static boolean metricIsFinite(Map.Entry<String, Number> metric) {
        return ! (metric.getValue() instanceof Double) || Double.isFinite((double) metric.getValue());
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
