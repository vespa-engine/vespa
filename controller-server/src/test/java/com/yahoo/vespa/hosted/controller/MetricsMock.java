// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;


import com.yahoo.jdisc.Metric;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class MetricsMock implements Metric {

    private final Map<Context, Map<String, Number>> metrics = new HashMap<>();

    @Override
    public void set(String key, Number val, Context ctx) {
        Map<String, Number> metricsMap = metrics.getOrDefault(ctx, new HashMap<>());
        metricsMap.put(key, val);
    }

    @Override
    public void add(String key, Number val, Context ctx) {
        Map<String, Number> metricsMap = metrics.getOrDefault(ctx, new HashMap<>());
        metricsMap.compute(key, (k, v) -> v == null ? val : sum(v, val));
    }

    private Number sum(Number n1, Number n2) {
        return n1.doubleValue() + n2.doubleValue();
    }

    @Override
    public Context createContext(Map<String, ?> properties) {
        Context ctx = new MapContext(properties);
        metrics.putIfAbsent(ctx, new HashMap<>());
        return ctx;
    }

    public Map<Context, Map<String, Number>> getMetrics() {
        return metrics;
    }
    
    /** Returns a zero-context metric by name, or null if it is not present */
    public Number getMetric(String name) {
        Map<String, Number> valuesForEmptyContext = metrics.get(createContext(Collections.emptyMap()));
        if (valuesForEmptyContext == null) return null;
        return valuesForEmptyContext.get(name);
    }

    public Map<MapContext, Map<String, Number>> getMetricsFilteredByHost(String hostname) {
        return getMetrics().entrySet().stream()
                .filter(entry -> ((MapContext)entry.getKey()).containsDimensionValue("host", hostname))
                .collect(Collectors.toMap(entry -> (MapContext) entry.getKey(), Map.Entry::getValue));
    }

    public static class MapContext implements Context {
        final Map<String, ?> dimensions;

        public MapContext(Map<String, ?> dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public boolean equals(Object obj) {
            return Objects.deepEquals(obj, dimensions);
        }

        @Override
        public int hashCode() {
            return Objects.toString(dimensions).hashCode();
        }

        public Map<String, ?> getDimensions() {
            return dimensions;
        }

        public boolean containsDimensionValue(String dimension, Object value) {
            return value.equals(dimensions.get(dimension));
        }
    }
}

