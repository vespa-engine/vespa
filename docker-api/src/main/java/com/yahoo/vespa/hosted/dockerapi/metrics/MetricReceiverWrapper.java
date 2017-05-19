// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.metrics.simple.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Export metrics to both /state/v1/metrics and makes them available programmatically.
 * Each metric belongs to a yamas application
 *
 * @author freva
 */
public class MetricReceiverWrapper {
    // Application names used
    public static final String APPLICATION_DOCKER = "docker";
    public static final String APPLICATION_NODE = "vespa.node";
    public static final String APPLICATION_HOST_LIFE = "host_life";

    private final static ObjectMapper objectMapper = new ObjectMapper();
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

    public void unsetMetricsForContainer(String hostname) {
        synchronized (monitor) {
            applicationMetrics.values()
                              .forEach(m -> m.metricsByDimensions.keySet()
                                                                 .removeIf(d -> d.dimensionsMap.containsKey("host") &&
                                                                         d.dimensionsMap.get("host").equals(hostname)));
        }
    }

    public List<DimensionMetrics> getAllMetrics() {
        synchronized (monitor) {
            List<DimensionMetrics> dimensionMetrics = new ArrayList<>();
            applicationMetrics.entrySet()
                    .forEach(e -> e.getValue().metricsByDimensions().entrySet().stream()
                            .map(entry -> new DimensionMetrics(e.getKey(), entry.getKey(), entry.getValue()))
                            .forEach(dimensionMetrics::add));
            return dimensionMetrics;
        }
    }

    // For testing, returns same as getAllMetrics(), but without "timestamp"
    public Set<Map<String, Object>> getAllMetricsRaw() {
        synchronized (monitor) {
            Set<Map<String, Object>> dimensionMetrics = new HashSet<>();
            applicationMetrics.entrySet()
                    .forEach(e -> e.getValue().metricsByDimensions().entrySet().stream()
                            .map(entry -> new DimensionMetrics(e.getKey(), entry.getKey(), entry.getValue()))
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

    public class DimensionMetrics {
        private final String application;
        private final Dimensions dimensions;
        private final Map<String, Object> metrics;

        DimensionMetrics(String application, Dimensions dimensions, Map<String, MetricValue> metricValues) {
            this.application = application;
            this.dimensions = dimensions;
            this.metrics = metricValues.entrySet().stream().collect(
                    Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue()));
        }

        private Map<String, Object> getMetrics() {
            final Map<String, Object> routing = new HashMap<>();
            final Map<String, Object> routingYamas = new HashMap<>();
            routing.put("yamas", routingYamas);
            routingYamas.put("namespaces", Arrays.asList("Vespa"));

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
    }

    private Map<Dimensions, Map<String, MetricValue>> getOrCreateApplicationMetrics(String application) {
        if (! applicationMetrics.containsKey(application)) {
            ApplicationMetrics metrics = new ApplicationMetrics();
            applicationMetrics.put(application, metrics);
        }
        return applicationMetrics.get(application).metricsByDimensions();
    }

    // Application is yamas application, not Vespa application
    private static class ApplicationMetrics {
        private final Map<Dimensions, Map<String, MetricValue>> metricsByDimensions = new LinkedHashMap<>();

        Map<Dimensions, Map<String, MetricValue>> metricsByDimensions() {
            return metricsByDimensions;
        }
    }
}
