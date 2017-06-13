// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.compiled;

import com.google.common.collect.ImmutableMap;
import com.yahoo.search.query.profile.DimensionBinding;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A map which may return different values depending on the values given in a context
 * supplied with the key on all operations.
 * <p>
 * Dimensional maps are immutable and created through a DimensionalMap.Builder
 *
 * @author bratseth
 */
public class DimensionalMap<KEY, VALUE> {

    private final Map<KEY, DimensionalValue<VALUE>> values;

    private DimensionalMap(Map<KEY, DimensionalValue<VALUE>> values) {
        this.values = ImmutableMap.copyOf(values);
    }

    /** Returns the value for this key matching a context, or null if none */
    public VALUE get(KEY key, Map<String, String> context) {
        DimensionalValue<VALUE> variants = values.get(key);
        if (variants == null) return null;
        return variants.get(context);
    }

    /** Returns the set of dimensional entries across all contexts. */
    public Set<Map.Entry<KEY, DimensionalValue<VALUE>>> entrySet() {
        return values.entrySet();
    }

    /** Returns true if this is empty for all contexts. */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    public static class Builder<KEY, VALUE> {

        private Map<KEY, DimensionalValue.Builder<VALUE>> entries = new HashMap<>();

        // TODO: DimensionBinding -> Binding?
        public void put(KEY key, DimensionBinding binding, VALUE value) {
            DimensionalValue.Builder<VALUE> entry = entries.get(key);
            if (entry == null) {
                entry = new DimensionalValue.Builder<>();
                entries.put(key, entry);
            }
            entry.add(value, binding);
        }

        public DimensionalMap<KEY, VALUE> build() {
            Map<KEY, DimensionalValue<VALUE>> map = new HashMap<>();
            for (Map.Entry<KEY, DimensionalValue.Builder<VALUE>> entry : entries.entrySet()) {
                map.put(entry.getKey(), entry.getValue().build());
            }
            return new DimensionalMap<>(map);
        }

    }

}
