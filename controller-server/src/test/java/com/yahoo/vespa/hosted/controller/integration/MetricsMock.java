// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;


import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.Metric;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author mortent
 */
public class MetricsMock implements Metric {

    private final LinkedHashMap<Context, Map<String, Number>> metrics = new LinkedHashMap<>();

    @Override
    public void set(String key, Number val, Context ctx) {
        metrics.putIfAbsent(ctx, new HashMap<>());
        Map<String, Number> metricsMap = metrics.get(ctx);
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
                      .collect(Collectors.toMap(kv -> (MapContext) kv.getKey(),
                                                Map.Entry::getValue,
                                                (v1, v2) -> { throw new IllegalStateException("Duplicate keys for values '" + v1 + "' and '" + v2 + "'."); },
                                                LinkedHashMap::new));
    }

    /** Returns the most recently added metric matching given dimension and name */
    public Optional<Number> getMetric(Predicate<Map<String, String>> dimensionMatcher, String name) {
        var metrics = List.copyOf(getMetrics(dimensionMatcher).values());
        for (int i = metrics.size() - 1; i >= 0; i--) {
            var metric = metrics.get(i).get(name);
            if (metric != null) return Optional.of(metric);
        }
        return Optional.empty();
    }

    /** Returns the most recently added metric for given instance */
    public Optional<Number> getMetric(ApplicationId instance, String name) {
        return getMetric(d -> instance.toFullString().equals(d.get("applicationId")), name);
    }

    public static class MapContext implements Context {

        private final Map<String, String> dimensions;

        public MapContext(Map<String, String> dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MapContext that = (MapContext) o;
            return dimensions.equals(that.dimensions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dimensions);
        }

        public Map<String, String> getDimensions() {
            return dimensions;
        }

    }
}

