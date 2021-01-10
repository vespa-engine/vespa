// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.profile.compiled.Binding;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.DimensionalMap;
import com.yahoo.search.query.profile.compiled.ValueWithSource;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Compile a set of query profiles into compiled profiles.
 *
 * @author bratseth
 */
public class QueryProfileCompiler {

    private static final Logger log = Logger.getLogger(QueryProfileCompiler.class.getName());

    public static CompiledQueryProfileRegistry compile(QueryProfileRegistry input) {
        CompiledQueryProfileRegistry output = new CompiledQueryProfileRegistry(input.getTypeRegistry());
        for (QueryProfile inputProfile : input.allComponents())
            output.register(compile(inputProfile, output));
        return output;
    }

    public static CompiledQueryProfile compile(QueryProfile in, CompiledQueryProfileRegistry registry) {
        try {
            DimensionalMap.Builder<ValueWithSource> values = new DimensionalMap.Builder<>();
            DimensionalMap.Builder<QueryProfileType> types = new DimensionalMap.Builder<>();
            DimensionalMap.Builder<Object> references = new DimensionalMap.Builder<>();
            DimensionalMap.Builder<Object> unoverridables = new DimensionalMap.Builder<>();

            // Resolve values for each existing variant and combine into a single data structure
            Set<DimensionBindingForPath> variants = collectVariants(CompoundName.empty, in, DimensionBinding.nullBinding);
            variants.add(new DimensionBindingForPath(DimensionBinding.nullBinding, CompoundName.empty)); // if this contains no variants
            log.fine(() -> "Compiling " + in + " having " + variants.size() + " variants");

            CompoundNameChildCache pathCache = new CompoundNameChildCache();
            Map<DimensionBinding, Binding> bindingCache = new HashMap<>();
            for (var variant : variants) {
                log.finer(() -> "Compiling variant " + variant);
                Binding variantBinding = bindingCache.computeIfAbsent(variant.binding(), Binding::createFrom);
                for (var entry : in.visitValues(variant.path(), variant.binding().getContext(), pathCache).valuesWithSource().entrySet()) {
                    CompoundName fullName = pathCache.append(variant.path, entry.getKey());
                    values.put(fullName, variantBinding, entry.getValue());
                    if (entry.getValue().isUnoverridable())
                        unoverridables.put(fullName, variantBinding, Boolean.TRUE);
                    if (entry.getValue().isQueryProfile())
                        references.put(fullName, variantBinding, Boolean.TRUE);
                    if (entry.getValue().queryProfileType() != null)
                        types.put(fullName, variantBinding, entry.getValue().queryProfileType());
                }
            }

            return new CompiledQueryProfile(in.getId(), in.getType(),
                                            values.build(), types.build(), references.build(), unoverridables.build(),
                                            registry);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + in, e);
        }
    }

    /**
     * Returns all the unique combinations of dimension values which have values reachable from this profile.
     *
     * @param profile the profile we are collecting the variants of
     * @param currentVariant the variant we must have to arrive at this point in the query profile graph
     */
    private static Set<DimensionBindingForPath> collectVariants(CompoundName path, QueryProfile profile, DimensionBinding currentVariant) {
        Set<DimensionBindingForPath> variants = new HashSet<>();

        variants.addAll(collectVariantsFromValues(path, profile.getContent(), currentVariant));
        variants.addAll(collectVariantsInThis(path, profile, currentVariant));
        if (profile instanceof BackedOverridableQueryProfile)
            variants.addAll(collectVariantsInThis(path, ((BackedOverridableQueryProfile) profile).getBacking(), currentVariant));

        Set<DimensionBindingForPath> parentVariants;
        for (QueryProfile inheritedProfile : profile.inherited()) {
            parentVariants = collectVariants(path, inheritedProfile, currentVariant);
            variants.addAll(parentVariants);
            variants.addAll(combined(variants, parentVariants)); // parents and children may have different variant dimensions
        }
        variants.addAll(wildcardExpanded(variants));
        return variants;
    }

    /**
     * For variants which are underspecified we must explicitly resolve each possible combination
     * of actual left-side values.
     *
     * I.e if we have the variants [-,b=b1], [a=a1,-], [a=a2,-],
     * this returns the variants [a=a1,b=b1], [a=a2,b=b1]
     *
     * This is necessary because left-specified values takes precedence, and resolving [a=a1,b=b1] would
     * otherwise lead us to the compiled profile [a=a1,-], which may contain default values for properties where
     * we should have preferred variant values in [-,b=b1].
     */
    private static Set<DimensionBindingForPath> wildcardExpanded(Set<DimensionBindingForPath> variants) {
        Set<DimensionBindingForPath> expanded = new HashSet<>();

        PathTree trie = new PathTree();
        for (var variant : variants)
            trie.add(variant);

        // Visit all variant prefixes, grouped on path, and all their unique child bindings.
        trie.forEachPrefixAndChildren((prefixes, childBindings) -> {
            Set<DimensionBinding> processed = new HashSet<>();
            for (DimensionBindingForPath prefix : prefixes)
                if (processed.add(prefix.binding())) // Only compute once for similar bindings, since path is equal.
                    if (hasWildcardBeforeEnd(prefix.binding()))
                        for (DimensionBinding childBinding : childBindings)
                            if (childBinding != prefix.binding()) {
                                DimensionBinding combined = prefix.binding().combineWith(childBinding);
                                if ( ! combined.isInvalid())
                                    expanded.add(new DimensionBindingForPath(combined, prefix.path()));
                            }
        });

        return expanded;
    }

    private static boolean hasWildcardBeforeEnd(DimensionBinding variant) {
        for (int i = 0; i < variant.getValues().size() - 1; i++) { // -1 to not check the rightmost
            if (variant.getValues().get(i) == null)
                return true;
        }
        return false;
    }

    /** Generates a set of all the (legal) combinations of the variants in the given sets */
    private static Set<DimensionBindingForPath> combined(Set<DimensionBindingForPath> v1s,
                                                         Set<DimensionBindingForPath> v2s) {
        Set<DimensionBindingForPath> combinedVariants = new HashSet<>();
        for (DimensionBindingForPath v1 : v1s) {
            if (v1.binding().isNull()) continue;
            for (DimensionBindingForPath v2 : v2s) {
                if (v2.binding().isNull()) continue;

                DimensionBinding combined = v1.binding().combineWith(v2.binding());
                if ( combined.isInvalid() ) continue;

                combinedVariants.add(new DimensionBindingForPath(combined, v1.path()));
            }
        }
        return combinedVariants;
    }

    private static Set<DimensionBindingForPath> collectVariantsInThis(CompoundName path, QueryProfile profile, DimensionBinding currentVariant) {
        QueryProfileVariants profileVariants = profile.getVariants();
        Set<DimensionBindingForPath> variants = new HashSet<>();
        if (profileVariants != null) {
            for (QueryProfileVariant variant : profile.getVariants().getVariants()) {
                // Allow switching order since we're entering another profile
                DimensionBinding combinedVariant =
                        DimensionBinding.createFrom(profile.getDimensions(), variant.getDimensionValues()).combineWith(currentVariant);

                if (combinedVariant.isInvalid()) continue; // values at this point in the graph are unreachable

                variants.add(new DimensionBindingForPath(combinedVariant, path));
                variants.addAll(collectVariantsFromValues(path, variant.values(), combinedVariant));
                for (QueryProfile variantInheritedProfile : variant.inherited())
                    variants.addAll(collectVariants(path, variantInheritedProfile, combinedVariant));
            }
        }
        return variants;
    }

    private static Set<DimensionBindingForPath>  collectVariantsFromValues(CompoundName path,
                                                                           Map<String, Object> values,
                                                                           DimensionBinding currentVariant) {
        Set<DimensionBindingForPath> variants = new HashSet<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() instanceof QueryProfile)
                variants.addAll(collectVariants(path.append(entry.getKey()), (QueryProfile)entry.getValue(), currentVariant));
        }
        return variants;
    }

    private static class DimensionBindingForPath {

        private final DimensionBinding binding;
        private final CompoundName path;

        public DimensionBindingForPath(DimensionBinding binding, CompoundName path) {
            this.binding = binding;
            this.path = path;
        }

        public DimensionBinding binding() { return binding; }
        public CompoundName path() { return path; }

        @Override
        public boolean equals(Object o) {
            if ( o == this ) return true;
            if ( ! (o instanceof DimensionBindingForPath)) return false;
            DimensionBindingForPath other = (DimensionBindingForPath)o;
            return other.binding.equals(this.binding) && other.path.equals(this.path);
        }

        @Override
        public int hashCode() {
           return binding.hashCode() + 17 * path.hashCode();
        }

        @Override
        public String toString() {
            return binding + " for path " + path;
        }

    }


    /**
     * Simple trie for CompoundName paths.
     *
     * @author jonmv
     */
    static class PathTree {

        private final Node root = new Node(0);

        void add(DimensionBindingForPath entry) {
            root.add(entry);
        }

        /** Performs action on sets of path prefixes against all their (common) children. */
        void forEachPrefixAndChildren(BiConsumer<Collection<DimensionBindingForPath>, Collection<DimensionBinding>> action) {
            root.visit(action);
        }

        private static class Node {

            private final int depth;
            private final SortedMap<String, Node> children = new TreeMap<>();
            private final List<DimensionBindingForPath> elements = new ArrayList<>();

            private Node(int depth) {
                this.depth = depth;
            }

            /** Performs action on the elements of this against all child element bindings, then returns the union of these two sets. */
            Set<DimensionBinding> visit(BiConsumer<Collection<DimensionBindingForPath>, Collection<DimensionBinding>> action) {
                Set<DimensionBinding> allChildren = new HashSet<>();
                for (Node child : children.values())
                    allChildren.addAll(child.visit(action));

                for (DimensionBindingForPath element : elements)
                    if ( ! element.binding().isNull())
                        allChildren.add(element.binding());

                action.accept(elements, allChildren);

                return allChildren;
            }

            void add(DimensionBindingForPath entry) {
                if (depth == entry.path().size())
                    elements.add(entry);
                else
                    children.computeIfAbsent(entry.path().get(depth),
                                             __ -> new Node(depth + 1)).add(entry);
            }

        }

    }


}
