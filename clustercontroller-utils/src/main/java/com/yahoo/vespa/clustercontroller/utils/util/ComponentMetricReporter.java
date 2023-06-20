// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.util;

import java.util.Map;
import java.util.TreeMap;

/**
 * Metric reporter wrapper to add component name prefix and common dimensions.
 */
public class ComponentMetricReporter implements MetricReporter {

    private final MetricReporter impl;
    private final String prefix;
    private final Map<String, String> defaultDimensions = new TreeMap<>();
    private Context defaultContext;

    public ComponentMetricReporter(MetricReporter impl, String prefix) {
        this.impl = impl;
        this.prefix = prefix;
        defaultContext = impl.createContext(defaultDimensions);
    }

    public ComponentMetricReporter addDimension(String key, String value) {
        defaultDimensions.put(key, value);
        defaultContext = impl.createContext(defaultDimensions);
        return this;
    }

    public void set(String name, Number value) {
        impl.set(prefix + name, value, defaultContext);
    }

    public void add(String name, Number value) {
        impl.add(prefix + name, value, defaultContext);
    }

    @Override
    public void set(String name, Number value, Context context) {
        impl.set(prefix + name, value, context);
    }

    @Override
    public void add(String name, Number value, Context context) {
        impl.add(prefix + name, value, context);
    }

    @Override
    public Context createContext(Map<String, ?> stringMap) {
        if (stringMap == null) return defaultContext;
        Map<String, Object> m = new TreeMap<>(stringMap);
        for(String key : defaultDimensions.keySet()) {
            if (!m.containsKey(key)) m.put(key, defaultDimensions.get(key));
        }
        return impl.createContext(m);
    }

}
