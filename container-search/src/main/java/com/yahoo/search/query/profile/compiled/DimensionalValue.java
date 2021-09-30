// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.compiled;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.profile.DimensionBinding;
import com.yahoo.search.query.profile.SubstituteString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Contains the values a given key in a DimensionalMap may take for different dimensional contexts.
 *
 * @author bratseth
 */
public class DimensionalValue<VALUE> {

    private final Map<Binding, VALUE> indexedVariants;
    private final List<BindingSpec> bindingSpecs;

    private DimensionalValue(List<Value<VALUE>> variants) {
        Collections.sort(variants);

        // If there are inconsistent definitions of the same property, we should pick the first in the sort order
        this.indexedVariants = new HashMap<>();
        for (Value<VALUE> variant : variants)
            indexedVariants.putIfAbsent(variant.binding(), variant.value());

        this.bindingSpecs = new ArrayList<>();
        for (Value<VALUE> variant : variants) {
            BindingSpec spec = new BindingSpec(variant.binding());
            if ( ! bindingSpecs.contains(spec))
                bindingSpecs.add(spec);
        }
    }

    /** Returns the value matching this context, or null if none */
    public VALUE get(Map<String, String> context) {
        if (context == null)
            context = Collections.emptyMap();

        for (BindingSpec spec : bindingSpecs) {
            if ( ! spec.matches(context)) continue;
            VALUE value = indexedVariants.get(new Binding(spec, context));
            if (value != null)
                return value;
        }
        return null;
    }

    public boolean isEmpty() { return indexedVariants.isEmpty(); }

    @Override
    public String toString() {
        return indexedVariants.toString();
    }

    public static class Builder<VALUE> {

        /** The variants of the value of this key */
        private final Map<VALUE, Value.Builder<VALUE>> buildableVariants = new HashMap<>();

        /** Returns the value for the given binding, or null if none */
        public VALUE valueFor(Binding variantBinding) {
            for (var entry : buildableVariants.entrySet()) {
                if (entry.getValue().variants.contains(variantBinding))
                    return entry.getKey();
            }
            return null;
        }

        public void add(VALUE value, Binding variantBinding) {
            // Note: We know we can index by the value because its possible types are constrained
            // to what query profiles allow: String, primitives and query profiles (wrapped as a ValueWithSource)
            buildableVariants.computeIfAbsent(value, Value.Builder::new)
                             .addVariant(variantBinding, value);
        }

        public DimensionalValue<VALUE> build(Map<CompoundName, DimensionalValue.Builder<VALUE>> entries) {
            List<Value<VALUE>> variants = new ArrayList<>();
            if (buildableVariants.size() == 1) {
                // Compact size 1 as it is common and easy to do. To compact size > 1 we would need to
                // compact within generic intervals having the same value
                for (Value.Builder<VALUE> buildableVariant : buildableVariants.values())
                    buildableVariant.compact();
            }
            for (Value.Builder<VALUE> buildableVariant : buildableVariants.values()) {
                variants.addAll(buildableVariant.build(entries));
            }
            return new DimensionalValue<>(variants);
        }

    }

    /** A value for a particular binding */
    private static class Value<VALUE> implements Comparable<Value<VALUE>> {

        private final VALUE value;

        /** The minimal binding this holds for */
        private final Binding binding;

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

        private static class Builder<VALUE> {

            private VALUE value;

            /**
             * The set of bindings this value is for.
             * Some of these are more general versions of others.
             * We need to keep both to allow interleaving a different value with medium generality.
             */
            private List<Binding> variants = new ArrayList<>();

            public Builder(VALUE value) {
                this.value = value;
            }

            /** Add a binding this holds for */
            @SuppressWarnings("unchecked")
            public void addVariant(Binding binding, VALUE newValue) {
                variants.add(binding);

                // We're combining values for efficiency, so remove incorrect provenance info
                if (value instanceof ValueWithSource) {
                    ValueWithSource v1 = (ValueWithSource)value;
                    ValueWithSource v2 = (ValueWithSource)newValue;

                    if (v1.source() != null && ! v1.source().equals(v2.source()))
                        v1 = v1.withSource(null);

                    // We could keep the more general variant here (when matching), but that situation is rare
                    if (v1.variant().isPresent() && ! v1.variant().equals(v2.variant()))
                        v1 = v1.withVariant(Optional.empty());

                    value = (VALUE)v1;
                }
            }

            /** Remove variants that are specializations of other variants in this */
            void compact() {
                Collections.sort(variants);
                List<Binding> compacted = new ArrayList<>();

                if (variants.get(variants.size() - 1).dimensions().length == 0) { // Shortcut
                    variants = List.of(variants.get(variants.size() - 1));
                }
                else {
                    for (int i = variants.size() - 1; i >= 0; i--) {
                        if ( ! containsGeneralizationOf(variants.get(i), compacted))
                            compacted.add(variants.get(i));
                    }
                    Collections.reverse(compacted);
                    variants = compacted;
                }
            }

            private boolean containsGeneralizationOf(Binding binding, List<Binding> bindings) {
                for (Binding candidate : bindings) {
                    if (candidate.generalizes(binding))
                        return true;
                }
                return false;
            }

            /** Build a separate value object for each dimension combination which has this value */
            public List<Value<VALUE>> build(Map<CompoundName, DimensionalValue.Builder<VALUE>> entries) {
                if (variants.size() == 1) { // Shortcut
                    return List.of(new Value<>(substituteIfRelative(value, variants.iterator().next(), entries),
                                               variants.iterator().next()));
                }

                List<Value<VALUE>> values = new ArrayList<>(variants.size());
                for (Binding variant : variants) {
                    values.add(new Value<>(substituteIfRelative(value, variant, entries), variant));
                }
                return values;
            }

            public Object value() {
                return value;
            }

            // TODO: Move this
            @SuppressWarnings("unchecked")
            private VALUE substituteIfRelative(VALUE value,
                                               Binding variant,
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
                                                                       Arrays.toString(variant.dimensionValues()));
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

    /** A list of dimensions for which there exist one or more bindings in this */
    static class BindingSpec {

        /** The dimensions of this. Unenforced invariant: Content never changes. */
        private final String[] dimensions;

        public BindingSpec(Binding binding) {
            this.dimensions = binding.dimensions();
        }

        /** Do not change the returned array */
        String[] dimensions() { return dimensions; }

        /** Returns whether this context contains all the keys of this */
        public boolean matches(Map<String, String> context) {
            for (int i = 0; i < dimensions.length; i++)
                if ( ! context.containsKey(dimensions[i])) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(dimensions);
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) return true;
            if ( ! (other instanceof BindingSpec)) return false;
            return Arrays.equals(((BindingSpec)other).dimensions, this.dimensions);
        }

    }

}
