// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.metrics.simple.Point;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Export metrics to both /state/v1/metrics and makes them available programatically.
 *
 * @author valerijf
 */
public class MetricReceiverWrapper implements Iterable<MetricReceiverWrapper.DimensionMetrics> {
    private final static ObjectMapper objectMapper = new ObjectMapper();
    private final Object monitor = new Object();
    private final Map<Dimensions, Map<String, MetricValue>> metricsByDimensions = new HashMap<>();
    private final MetricReceiver metricReceiver;

    @Inject
    public MetricReceiverWrapper(MetricReceiver metricReceiver) {
        this.metricReceiver = metricReceiver;
    }

    /**
     *  Declaring the same dimensions and name results in the same CounterWrapper instance (idempotent).
     */
    public CounterWrapper declareCounter(Dimensions dimensions, String name) {
        synchronized (monitor) {
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
    public GaugeWrapper declareGauge(Dimensions dimensions, String name) {
        synchronized (monitor) {
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
            Set<Dimensions> dimensions = metricsByDimensions.keySet();
            for (Dimensions dimension : dimensions) {
                if (dimension.dimensionsMap.containsKey("host") && dimension.dimensionsMap.get("host").equals(hostname)) {
                    metricsByDimensions.remove(dimension);
                }
            }
        }
    }


    @Override
    public Iterator<DimensionMetrics> iterator() {
        synchronized (monitor) {
            return metricsByDimensions.entrySet().stream().map(entry ->
                    new DimensionMetrics(entry.getKey(), entry.getValue())).iterator();
        }
    }

    // For testing
    Map<String, Number> getMetricsForDimension(Dimensions dimensions) {
        synchronized (monitor) {
            return metricsByDimensions.get(dimensions).entrySet().stream().collect(
                    Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue()));
        }
    }

    public class DimensionMetrics {
        private final Dimensions dimensions;
        private final Map<String, Object> metrics;

        DimensionMetrics(Dimensions dimensions, Map<String, MetricValue> metricValues) {
            this.dimensions = dimensions;
            this.metrics = metricValues.entrySet().stream().collect(
                    Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue()));
        }

        public String toSecretAgentReport() throws JsonProcessingException {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("application", "docker");
            report.put("timestamp", System.currentTimeMillis() / 1000);
            report.put("dimensions", dimensions.dimensionsMap);
            report.put("metrics", metrics);

            return objectMapper.writeValueAsString(report);
        }
    }
}
