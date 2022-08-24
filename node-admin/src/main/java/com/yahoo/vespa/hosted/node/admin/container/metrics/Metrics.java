// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container.metrics;

import com.yahoo.component.annotation.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Stores the latest metric for the given application, name, dimension triplet in memory
 *
 * @author freva
 */
public class Metrics {
    // Application names used
    public static final String APPLICATION_HOST = "vespa.host";
    public static final String APPLICATION_NODE = "vespa.node";

    private final Object monitor = new Object();
    private final Map<DimensionType, Map<String, ApplicationMetrics>> metrics = new HashMap<>();

    @Inject
    public Metrics() { }

    /**
     * Creates a counter metric under vespa.host application, with no dimensions and default dimension type
     * See {@link #declareCounter(String, String, Dimensions, DimensionType)}
     */
    public Counter declareCounter(String name) {
        return declareCounter(name, Dimensions.NONE);
    }

    /**
     * Creates a counter metric under vespa.host application, with the given dimensions and default dimension type
     * See {@link #declareCounter(String, String, Dimensions, DimensionType)}
     */
    public Counter declareCounter(String name, Dimensions dimensions) {
        return declareCounter(APPLICATION_HOST, name, dimensions, DimensionType.DEFAULT);
    }

    /** Creates a counter metric. This method is idempotent. */
    public Counter declareCounter(String application, String name, Dimensions dimensions, DimensionType type) {
        synchronized (monitor) {
            return (Counter) getOrCreateApplicationMetrics(application, type)
                    .computeIfAbsent(dimensions, d -> new HashMap<>())
                    .computeIfAbsent(name, n -> new Counter());
        }
    }

    /**
     * Creates a gauge metric under vespa.host application, with no dimensions and default dimension type
     * See {@link #declareGauge(String, String, Dimensions, DimensionType)}
     */
    public Gauge declareGauge(String name) {
        return declareGauge(name, Dimensions.NONE);
    }

    /**
     * Creates a gauge metric under vespa.host application, with the given dimensions and default dimension type
     * See {@link #declareGauge(String, String, Dimensions, DimensionType)}
     */
    public Gauge declareGauge(String name, Dimensions dimensions) {
        return declareGauge(APPLICATION_HOST, name, dimensions, DimensionType.DEFAULT);
    }

    /** Creates a gauge metric. This method is idempotent */
    public Gauge declareGauge(String application, String name, Dimensions dimensions, DimensionType type) {
        synchronized (monitor) {
            return (Gauge) getOrCreateApplicationMetrics(application, type)
                    .computeIfAbsent(dimensions, d -> new HashMap<>())
                    .computeIfAbsent(name, n -> new Gauge());
        }
    }

    public List<DimensionMetrics> getDefaultMetrics() {
        return getMetricsByType(DimensionType.DEFAULT);
    }

    public List<DimensionMetrics> getMetricsByType(DimensionType type) {
        synchronized (monitor) {
            List<DimensionMetrics> dimensionMetrics = new ArrayList<>();
            metrics.getOrDefault(type, Map.of())
                    .forEach((application, applicationMetrics) -> applicationMetrics.metricsByDimensions().entrySet().stream()
                    .map(entry -> new DimensionMetrics(application, entry.getKey(),
                            entry.getValue().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, value -> value.getValue().getValue()))))
                    .forEach(dimensionMetrics::add));
            return dimensionMetrics;
        }
    }

    public void deleteMetricByDimension(String name,  Dimensions dimensionsToRemove, DimensionType type) {
        synchronized (monitor) {
            Optional.ofNullable(metrics.get(type))
                    .map(m -> m.get(name))
                    .map(ApplicationMetrics::metricsByDimensions)
                    .ifPresent(m -> m.remove(dimensionsToRemove));
        }
    }

    Map<Dimensions, Map<String, MetricValue>> getOrCreateApplicationMetrics(String application, DimensionType type) {
        return metrics.computeIfAbsent(type, m -> new HashMap<>())
                .computeIfAbsent(application, app -> new ApplicationMetrics())
                .metricsByDimensions();
    }

    // "Application" is the monitoring application, not Vespa application
    private static class ApplicationMetrics {
        private final Map<Dimensions, Map<String, MetricValue>> metricsByDimensions = new LinkedHashMap<>();

        Map<Dimensions, Map<String, MetricValue>> metricsByDimensions() {
            return metricsByDimensions;
        }
    }

    // Used to distinguish whether metrics have been populated with all tag values
    public enum DimensionType {
        /** Default metrics get added default dimensions set in check config */
        DEFAULT,

        /** Pretagged metrics will only get the dimensions explicitly set when creating the counter/gauge */
        PRETAGGED
    }
}
