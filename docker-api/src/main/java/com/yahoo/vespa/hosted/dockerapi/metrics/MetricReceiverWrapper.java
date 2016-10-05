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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Export metrics to both /state/v1/metrics and makes them available programatically.
 *
 * @author valerijf
 */
public class MetricReceiverWrapper implements Iterable<MetricReceiverWrapper.DimensionMetrics> {
    private final static ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Map<String, Object>, Map<String, MetricValue>> metricsByDimensions = new ConcurrentHashMap<>();
    private final MetricReceiver metricReceiver;

    @Inject
    public MetricReceiverWrapper(MetricReceiver metricReceiver) {
        this.metricReceiver = metricReceiver;
    }

    public CounterWrapper declareCounter(Map<String, Object> dimensions, String name) {
        if (! metricsByDimensions.containsKey(dimensions)) metricsByDimensions.put(dimensions, new ConcurrentHashMap<>());
        if (! metricsByDimensions.get(dimensions).containsKey(name)) {
            CounterWrapper counter = new CounterWrapper(metricReceiver.declareCounter(name, new Point(dimensions)));
            metricsByDimensions.get(dimensions).put(name, counter);
        }

        return (CounterWrapper) metricsByDimensions.get(dimensions).get(name);
    }

    public GaugeWrapper declareGauge(Map<String, Object> dimensions, String name) {
        if (! metricsByDimensions.containsKey(dimensions)) metricsByDimensions.put(dimensions, new ConcurrentHashMap<>());
        if (! metricsByDimensions.get(dimensions).containsKey(name)) {
            GaugeWrapper gauge = new GaugeWrapper(metricReceiver.declareGauge(name, new Point(dimensions)));
            metricsByDimensions.get(dimensions).put(name, gauge);
        }

        return (GaugeWrapper) metricsByDimensions.get(dimensions).get(name);
    }

    public void unsetMetricsForContainer(String hostname) {
        Set<Map<String, Object>> dimensions = metricsByDimensions.keySet();
        for (Map<String, Object> dimension : dimensions) {
            if (dimension.containsKey("host") && dimension.get("host").equals(hostname)) {
                metricsByDimensions.remove(dimensions);
            }
        }
    }


    @Override
    public Iterator<DimensionMetrics> iterator() {
        return metricsByDimensions.entrySet().stream().map(entry ->
                new DimensionMetrics(entry.getKey(), entry.getValue())).iterator();
    }

    public class DimensionMetrics {
        private final Map<String, Object> dimensions;
        private final Map<String, Object> metrics;

        DimensionMetrics(Map<String, Object> dimensions, Map<String, MetricValue> metricValues) {
            this.dimensions = new HashMap<>(dimensions);
            this.metrics = metricValues.entrySet().stream().collect(
                    Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue()));
        }

        public String toSecretAgentReport() throws JsonProcessingException {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("application", "docker");
            report.put("timestamp", System.currentTimeMillis() / 1000);
            report.put("dimensions", dimensions);
            report.put("metrics", metrics);

            return objectMapper.writeValueAsString(report);
        }
    }
}
