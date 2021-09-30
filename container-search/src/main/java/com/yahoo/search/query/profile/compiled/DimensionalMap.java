// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.compiled;

import com.google.common.collect.ImmutableMap;
import com.yahoo.processing.request.CompoundName;
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
public class DimensionalMap<VALUE> {

    private final Map<CompoundName, DimensionalValue<VALUE>> values;

    private DimensionalMap(Map<CompoundName, DimensionalValue<VALUE>> values) {
        this.values = ImmutableMap.copyOf(values);
    }

    /** Returns the value for this key matching a context, or null if none */
    public VALUE get(CompoundName key, Map<String, String> context) {
        DimensionalValue<VALUE> variants = values.get(key);
        if (variants == null) return null;
        return variants.get(context);
    }

    /** Returns the set of dimensional entries across all contexts. */
    public Set<Map.Entry<CompoundName, DimensionalValue<VALUE>>> entrySet() {
        return values.entrySet();
    }

    /** Returns true if this is empty for all contexts. */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    public static class Builder<VALUE> {

        private final Map<CompoundName, DimensionalValue.Builder<VALUE>> entries = new HashMap<>();

        public void put(CompoundName key, Binding binding, VALUE value) {
            entries.computeIfAbsent(key, __ -> new DimensionalValue.Builder<>())
                   .add(value, binding);
        }

        public DimensionalMap<VALUE> build() {
            Map<CompoundName, DimensionalValue<VALUE>> map = new HashMap<>();
            for (Map.Entry<CompoundName, DimensionalValue.Builder<VALUE>> entry : entries.entrySet()) {
                map.put(entry.getKey(), entry.getValue().build(entries));
            }
            return new DimensionalMap<>(map);
        }

    }

}
