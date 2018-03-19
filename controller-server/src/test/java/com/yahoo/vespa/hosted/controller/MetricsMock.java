// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;


import com.yahoo.jdisc.Metric;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author mortent
 */
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
    @SuppressWarnings("unchecked")
    public Context createContext(Map<String, ?> properties) {
        Context ctx = new MapContext((Map<String, String>) properties);
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

    /** Returns metric and context for any metric matching the given dimension predicate */
    public Map<MapContext, Map<String, Number>> getMetrics(Predicate<Map<String, String>> dimensionMatcher) {
        return metrics.entrySet()
                      .stream()
                      .filter(context -> dimensionMatcher.test(((MapContext) context.getKey()).getDimensions()))
                      .collect(Collectors.toMap(entry -> (MapContext) entry.getKey(), Map.Entry::getValue));
    }

    /** Returns metric filtered by dimension and name */
    public Optional<Number> getMetric(Predicate<Map<String, String>> dimensionMatcher, String name) {
        Map<String, Number> metrics = getMetrics(dimensionMatcher).entrySet()
                                                                  .stream()
                                                                  .map(Map.Entry::getValue)
                                                                  .findFirst()
                                                                  .orElseGet(Collections::emptyMap);
        return Optional.ofNullable(metrics.get(name));
    }

    public static class MapContext implements Context {

        private final Map<String, String> dimensions;

        public MapContext(Map<String, String> dimensions) {
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

        public Map<String, String> getDimensions() {
            return dimensions;
        }

    }
}

