// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.compiled;

import com.yahoo.search.query.profile.DimensionBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contains the values a given key in a DimensionalMap may take for different dimensional contexts.
 *
 * @author bratseth
 */
public class DimensionalValue<VALUE> {

    private final List<Value<VALUE>> values;

    /** Create a set of variants which is a single value regardless of dimensions */
    public DimensionalValue(Value<VALUE> value) {
        this.values = Collections.singletonList(value);
    }

    public DimensionalValue(List<Value<VALUE>> valueVariants) {
        if (valueVariants.size() == 1) { // special cased for efficiency
            this.values = Collections.singletonList(valueVariants.get(0));
        }
        else {
            this.values = new ArrayList<>(valueVariants);
            Collections.sort(this.values);
        }
    }

    /** Returns the value matching this context, or null if none */
    public VALUE get(Map<String, String> context) {
        if (context == null)
            context = Collections.emptyMap();
        for (Value<VALUE> value : values) {
            if (value.matches(context))
                return value.value();
        }
        return null;
    }

    public boolean isEmpty() { return values.isEmpty(); }

    @Override
    public String toString() {
        return values.toString();
    }

    public static class Builder<VALUE> {

        /** The minimal set of variants needed to capture all values at this key */
        private Map<VALUE, Value.Builder<VALUE>> buildableVariants = new HashMap<>();

        public void add(VALUE value, DimensionBinding variantBinding) {
            // Note: We know we can index by the value because its possible types are constrained
            // to what query profiles allow: String, primitives and query profiles
            Value.Builder variant = buildableVariants.get(value);
            if (variant == null) {
                variant = new Value.Builder<>(value);
                buildableVariants.put(value, variant);
            }
            variant.addVariant(variantBinding);
        }

        public DimensionalValue<VALUE> build() {
            List<Value> variants = new ArrayList<>();
            for (Value.Builder buildableVariant : buildableVariants.values()) {
                variants.addAll(buildableVariant.build());
            }
            return new DimensionalValue(variants);
        }

    }

    /** A value for a particular binding */
    private static class Value<VALUE> implements Comparable<Value> {

        private VALUE value = null;

        /** The minimal binding this holds for */
        private Binding binding = null;

        public Value(VALUE value, Binding binding) {
            this.value = value;
            this.binding = binding;
        }

        /** Returns the value at this entry or null if none */
        public VALUE value() { return value; }

        /** Returns the binding that must match for this to be a valid entry, or Binding.nullBinding if none */
        public Binding binding() {
            if (binding == null) return Binding.nullBinding;
            return binding;
        }

        public boolean matches(Map<String, String> context) {
            return binding.matches(context);
        }

        @Override
        public int compareTo(Value other) {
            return this.binding.compareTo(other.binding);
        }

        @Override
        public String toString() {
            return " value '" + value + "' for " + binding;
        }

        /**
         * A single value with the minimal set of dimension combinations it holds for.
         */
        private static class Builder<VALUE> {

            private final VALUE value;

            /**
             * The set of bindings this value is for.
             * Some of these are more general versions of others.
             * We need to keep both to allow interleaving a different value with medium generality.
             */
            private Set<DimensionBinding> variants = new HashSet<>();

            public Builder(VALUE value) {
                this.value = value;
            }

            /** Add a binding this holds for */
            public void addVariant(DimensionBinding binding) {
                variants.add(binding);
            }

            /** Build a separate value object for each dimension combination which has this value */
            public List<Value<VALUE>> build() {
                // Shortcut for efficiency of the normal case
                if (variants.size()==1)
                    return Collections.singletonList(new Value<>(value, Binding.createFrom(variants.iterator().next())));

                List<Value<VALUE>> values = new ArrayList<>(variants.size());
                for (DimensionBinding variant : variants)
                    values.add(new Value<>(value, Binding.createFrom(variant)));
                return values;
            }

            public Object value() {
                return value;
            }

        }
    }
}
