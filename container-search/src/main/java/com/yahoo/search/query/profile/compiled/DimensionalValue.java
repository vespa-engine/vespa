// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.compiled;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.profile.DimensionBinding;
import com.yahoo.search.query.profile.SubstituteString;

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

        /** Returns the value for the given binding, or null if none */
        public VALUE valueFor(DimensionBinding variantBinding) {
            for (var entry : buildableVariants.entrySet()) {
                if (entry.getValue().variants.contains(variantBinding))
                    return entry.getKey();
            }
            return null;
        }

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

        public DimensionalValue<VALUE> build(Map<?, DimensionalValue.Builder<VALUE>> entries) {
            List<Value> variants = new ArrayList<>();
            for (Value.Builder buildableVariant : buildableVariants.values()) {
                variants.addAll(buildableVariant.build(entries));
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
            public List<Value<VALUE>> build(Map<CompoundName, DimensionalValue.Builder<VALUE>> entries) {
                // Shortcut for efficiency of the normal case
                if (variants.size() == 1) {
                    return Collections.singletonList(new Value<>(substituteIfRelative(value, variants.iterator().next(), entries),
                                                                 Binding.createFrom(variants.iterator().next())));
                }

                List<Value<VALUE>> values = new ArrayList<>(variants.size());
                for (DimensionBinding variant : variants) {
                    values.add(new Value<>(substituteIfRelative(value, variant, entries), Binding.createFrom(variant)));
                }
                return values;
            }

            public Object value() {
                return value;
            }

            // TODO: Move this
            @SuppressWarnings("unchecked")
            private VALUE substituteIfRelative(VALUE value,
                                               DimensionBinding variant,
                                               Map<CompoundName, DimensionalValue.Builder<VALUE>> entries) {
                if (value instanceof ValueWithSource && ((ValueWithSource)value).value() instanceof SubstituteString) {
                    ValueWithSource valueWithSource = (ValueWithSource)value;
                    SubstituteString substitute = (SubstituteString)valueWithSource.value();
                    if (substitute.hasRelative()) {
                        List<SubstituteString.Component> resolvedComponents = new ArrayList<>(substitute.components().size());
                        for (SubstituteString.Component component : substitute.components()) {
                            if (component instanceof SubstituteString.RelativePropertyComponent) {
                                SubstituteString.RelativePropertyComponent relativeComponent = (SubstituteString.RelativePropertyComponent)component;
                                var substituteValues = lookupByLocalName(relativeComponent.fieldName(), entries);
                                if (substituteValues == null)
                                    throw new IllegalArgumentException("Could not resolve local substitution '" +
                                                                       relativeComponent.fieldName() + "' in variant " +
                                                                       variant);
                                ValueWithSource resolved = (ValueWithSource)substituteValues.valueFor(variant);
                                resolvedComponents.add(new SubstituteString.StringComponent(resolved.value().toString()));
                            }
                            else {
                                resolvedComponents.add(component);
                            }
                        }
                        return (VALUE)valueWithSource.withValue(new SubstituteString(resolvedComponents, substitute.stringValue()));
                    }
                }
                return value;
            }

            private DimensionalValue.Builder<VALUE> lookupByLocalName(String localName,
                                                                      Map<CompoundName, DimensionalValue.Builder<VALUE>> entries) {
                for (var entry : entries.entrySet()) {
                    if (entry.getKey().last().equals(localName))
                        return entry.getValue();
                }
                return null;
            }

        }

    }

}
