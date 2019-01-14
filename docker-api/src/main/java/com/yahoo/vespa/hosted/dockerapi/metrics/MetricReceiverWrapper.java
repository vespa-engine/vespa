// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi.metrics;

import com.google.inject.Inject;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.metrics.simple.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Export metrics to both /state/v1/metrics and makes them available programmatically.
 * Each metric belongs to a monitoring application
 *
 * @author freva
 */
public class MetricReceiverWrapper {
    // Application names used
    public static final String APPLICATION_DOCKER = "docker";
    public static final String APPLICATION_HOST = "vespa.host";
    public static final String APPLICATION_NODE = "vespa.node";

    private final Object monitor = new Object();
    private final Map<String, ApplicationMetrics> applicationMetrics = new HashMap<>(); // key is application name
    private final MetricReceiver metricReceiver;

    @Inject
    public MetricReceiverWrapper(MetricReceiver metricReceiver) {
        this.metricReceiver = metricReceiver;
    }

    /**
     *  Declaring the same dimensions and name results in the same CounterWrapper instance (idempotent).
     */
    public CounterWrapper declareCounter(String application, Dimensions dimensions, String name) {
        synchronized (monitor) {
            Map<Dimensions, Map<String, MetricValue>> metricsByDimensions = getOrCreateApplicationMetrics(application);
            if (!metricsByDimensions.containsKey(dimensions)) metricsByDimensions.put(dimensions, new HashMap<>());
            if (!metricsByDimensions.get(dimensions).containsKey(name)) {
                CounterWrapper counter = new CounterWrapper(metricReceiver.declareCounter(name, new Point(dimensions.dimensionsMap)));
                metricsByDimensions.get(dimensions).put(name, counter);
            }

            return (CounterWrapper) metricsByDimensions.get(dimensions).get(name);
        }
    }

    /**
     *  Declaring the same dimensions and name results in the same GaugeWrapper instance (idempotent).
     */
    public GaugeWrapper declareGauge(String application, Dimensions dimensions, String name) {
        synchronized (monitor) {
            Map<Dimensions, Map<String, MetricValue>> metricsByDimensions = getOrCreateApplicationMetrics(application);
            if (!metricsByDimensions.containsKey(dimensions))
                metricsByDimensions.put(dimensions, new HashMap<>());
            if (!metricsByDimensions.get(dimensions).containsKey(name)) {
                GaugeWrapper gauge = new GaugeWrapper(metricReceiver.declareGauge(name, new Point(dimensions.dimensionsMap)));
                metricsByDimensions.get(dimensions).put(name, gauge);
            }

            return (GaugeWrapper) metricsByDimensions.get(dimensions).get(name);
        }
    }

    public List<DimensionMetrics> getAllMetrics() {
        synchronized (monitor) {
            List<DimensionMetrics> dimensionMetrics = new ArrayList<>();
            applicationMetrics.forEach((application, applicationMetrics) -> applicationMetrics.metricsByDimensions().entrySet().stream()
                    .map(entry -> new DimensionMetrics(application, entry.getKey(),
                            entry.getValue().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, value -> value.getValue().getValue()))))
                    .forEach(dimensionMetrics::add));
            return dimensionMetrics;
        }
    }

    // For testing, returns same as getAllMetrics(), but without "timestamp"
    public Set<Map<String, Object>> getAllMetricsRaw() {
        synchronized (monitor) {
            Set<Map<String, Object>> dimensionMetrics = new HashSet<>();
            applicationMetrics.forEach((application, applicationMetrics) -> applicationMetrics.metricsByDimensions().entrySet().stream()
                    .map(entry -> new DimensionMetrics(application, entry.getKey(),
                            entry.getValue().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, value -> value.getValue().getValue()))))
                    .map(DimensionMetrics::getMetrics)
                    .forEach(dimensionMetrics::add));
            return dimensionMetrics;
        }
    }

    // For testing
    Map<String, Number> getMetricsForDimension(String application, Dimensions dimensions) {
        synchronized (monitor) {
            Map<Dimensions, Map<String, MetricValue>> metricsByDimensions = getOrCreateApplicationMetrics(application);
            return metricsByDimensions.get(dimensions).entrySet().stream().collect(
                    Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue()));
        }
    }

    private Map<Dimensions, Map<String, MetricValue>> getOrCreateApplicationMetrics(String application) {
        if (! applicationMetrics.containsKey(application)) {
            ApplicationMetrics metrics = new ApplicationMetrics();
            applicationMetrics.put(application, metrics);
        }
        return applicationMetrics.get(application).metricsByDimensions();
    }

    // "Application" is the monitoring application, not Vespa application
    private static class ApplicationMetrics {
        private final Map<Dimensions, Map<String, MetricValue>> metricsByDimensions = new LinkedHashMap<>();

        Map<Dimensions, Map<String, MetricValue>> metricsByDimensions() {
            return metricsByDimensions;
        }
    }
}
