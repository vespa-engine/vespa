// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.yahoo.jdisc.Metric;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A context implementation whose identity is the key and values such that this can be used as
 * a key in metrics lookups.
 *
 * @author Simon Thoresen Hult
 */
public final class StateMetricContext implements MetricDimensions, Metric.Context {

    private final Map<String, String> data; // effectively immutable
    private final int hashCode;

    private StateMetricContext(Map<String, String> data) {
        this.data = data;
        this.hashCode = data.hashCode();
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return data.entrySet().iterator();
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj == this) ||
               (obj instanceof StateMetricContext && ((StateMetricContext)obj).data.equals(data));
    }

    public static StateMetricContext newInstance(Map<String, ?> properties) {
        Map<String, String> data;
        if (properties != null) {
            data = new HashMap<>(properties.size());
            for (Map.Entry<String, ?> entry : properties.entrySet()) {
                data.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
            }
            data = Collections.unmodifiableMap(data);
        } else {
            data = Collections.emptyMap();
        }
        return new StateMetricContext(data);
    }

}
