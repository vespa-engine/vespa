// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers.test;

import com.yahoo.jdisc.Metric;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
* @author bratseth
*/
class MockMetric implements Metric {

    private Map<Context, Map<String, Number>> metrics = new HashMap<>();

    public Map<String, Number> values(Context context) {
        return metricsForContext(context);
    }

    @Override
    public void set(String key, Number val, Context context) {
        metricsForContext(context).put(key, val);
    }

    @Override
    public void add(String key, Number value, Context context) {
        Number previousValue = metricsForContext(context).get(key);
        if (previousValue == null)
            previousValue = 0;
        metricsForContext(context).put(key, value.doubleValue() + previousValue.doubleValue());
    }

    /** Returns the metrics for a given context, never null */
    private Map<String, Number> metricsForContext(Context context) {
        Map<String, Number> metricsForContext = metrics.get(context);
        if (metricsForContext == null) {
            metricsForContext = new HashMap<>();
            metrics.put(context, metricsForContext);
        }
        return metricsForContext;
    }

    @Override
    public Context createContext(Map<String, ?> dimensions) {
        return new MapContext(dimensions);
    }

    /** Creates a context containing a single dimension */
    public Metric.Context createContext(String dimensionName, String dimensionValue) {
        if (dimensionName.isEmpty())
            return createContext(Collections.emptyMap());
        return createContext(Collections.singletonMap(dimensionName, dimensionValue));
    }

    private class MapContext implements Metric.Context {

        private final Map<String, ?> dimensions;

        public MapContext(Map<String, ?> dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public int hashCode() {
            return dimensions.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if ( ! (o instanceof MapContext)) return false;
            return dimensions.equals(((MapContext)o).dimensions);
        }

    }

}
