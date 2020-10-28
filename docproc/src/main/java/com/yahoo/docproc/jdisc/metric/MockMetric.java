// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc.metric;

import com.yahoo.jdisc.Metric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Metric implementation for tests.
 *
 * @author jonmv
 */
public class MockMetric implements Metric {

    private final Map<String, Map<Map<String, ?>, Double>> metrics = new ConcurrentHashMap<>();

    @Override
    public void set(String key, Number val, Context ctx) {
        metrics.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
               .put(MapContext.emptyIfNull(ctx).dimensions, val.doubleValue());
    }

    @Override
    public void add(String key, Number val, Context ctx) {
        metrics.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
               .merge(MapContext.emptyIfNull(ctx).dimensions, val.doubleValue(), Double::sum);
    }

    @Override
    public Context createContext(Map<String, ?> properties) {
        return properties == null ? MapContext.empty : new MapContext(properties);
    }

    public Map<String, Map<Map<String, ?>, Double>> metrics() { return metrics; }

    private static class MapContext implements Context {

        private static final MapContext empty = new MapContext(Map.of());

        private final Map<String, ?> dimensions;

        private MapContext(Map<String, ?> dimensions) {
            this.dimensions = dimensions;
        }

        private static MapContext emptyIfNull(Context context) {
            return context == null ? empty : (MapContext) context;
        }

    }

}
