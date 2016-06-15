// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.DimensionalMap;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
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
        for (QueryProfile inputProfile : input.allComponents()) {
            output.register(compile(inputProfile, output));
        }
        return output;
    }

    public static CompiledQueryProfile compile(QueryProfile in, CompiledQueryProfileRegistry registry) {
        DimensionalMap.Builder<CompoundName, Object> values = new DimensionalMap.Builder<>();
        DimensionalMap.Builder<CompoundName, QueryProfileType> types = new DimensionalMap.Builder<>();
        DimensionalMap.Builder<CompoundName, Object> references = new DimensionalMap.Builder<>();
        DimensionalMap.Builder<CompoundName, Object> unoverridables = new DimensionalMap.Builder<>();

        // Resolve values for each existing variant and combine into a single data structure
        Set<DimensionBindingForPath> variants = new HashSet<>();
        collectVariants(CompoundName.empty, in, DimensionBinding.nullBinding, variants);
        variants.add(new DimensionBindingForPath(DimensionBinding.nullBinding, CompoundName.empty)); // if this contains no variants
        if (log.isLoggable(Level.FINE))
            log.fine("Compiling " + in.toString() + " having " + variants.size() + " variants");
        int i = 0;
        for (DimensionBindingForPath variant : variants) {
            if (log.isLoggable(Level.FINER))
                log.finer("    Compiling variant " + i++ + ": " + variant);
            for (Map.Entry<String, Object> entry : in.listValues(variant.path(), variant.binding().getContext(), null).entrySet())
                values.put(variant.path().append(entry.getKey()), variant.binding(), entry.getValue());
            for (Map.Entry<CompoundName, QueryProfileType> entry : in.listTypes(variant.path(), variant.binding().getContext()).entrySet())
                types.put(variant.path().append(entry.getKey()), variant.binding(), entry.getValue());
            for (CompoundName reference : in.listReferences(variant.path(), variant.binding().getContext()))
                references.put(variant.path().append(reference), variant.binding(), Boolean.TRUE); // Used as a set; value is ignored
            for (CompoundName name : in.listUnoverridable(variant.path(), variant.binding().getContext()))
                unoverridables.put(variant.path().append(name), variant.binding(), Boolean.TRUE); // Used as a set; value is ignored
        }

        return new CompiledQueryProfile(in.getId(), in.getType(),
                                        values.build(), types.build(), references.build(), unoverridables.build(),
                                        registry);
    }

    /**
     * Returns all the unique combinations of dimension values which have values set reachable from this profile.
     *
     * @param profile the profile we are collecting the variants of
     * @param currentVariant the variant we must have to arrive at this point in the query profile graph
     * @param allVariants the set of all variants accumulated so far
     */
    private static void collectVariants(CompoundName path, QueryProfile profile, DimensionBinding currentVariant, Set<DimensionBindingForPath> allVariants) {
        for (QueryProfile inheritedProfile : profile.inherited())
            collectVariants(path, inheritedProfile, currentVariant, allVariants);

        collectVariantsFromValues(path, profile.getContent(), currentVariant, allVariants);

        collectVariantsInThis(path, profile, currentVariant, allVariants);
        if (profile instanceof BackedOverridableQueryProfile)
            collectVariantsInThis(path, ((BackedOverridableQueryProfile) profile).getBacking(), currentVariant, allVariants);
    }

    private static void collectVariantsInThis(CompoundName path, QueryProfile profile, DimensionBinding currentVariant, Set<DimensionBindingForPath> allVariants) {
        QueryProfileVariants profileVariants = profile.getVariants();
        if (profileVariants != null) {
            for (QueryProfileVariant variant : profile.getVariants().getVariants()) {
                DimensionBinding combinedVariant =
                        DimensionBinding.createFrom(profile.getDimensions(), variant.getDimensionValues()).combineWith(currentVariant);
                if (combinedVariant.isInvalid()) continue; // values at this point in the graph are unreachable
                collectVariantsFromValues(path, variant.values(), combinedVariant, allVariants);
                for (QueryProfile variantInheritedProfile : variant.inherited())
                    collectVariants(path, variantInheritedProfile, combinedVariant, allVariants);
            }
        }
    }

    private static void collectVariantsFromValues(CompoundName path, Map<String, Object> values, DimensionBinding currentVariant, Set<DimensionBindingForPath> allVariants) {
        if ( ! values.isEmpty())
            allVariants.add(new DimensionBindingForPath(currentVariant, path)); // there are actual values for this variant

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() instanceof QueryProfile)
                collectVariants(path.append(entry.getKey()), (QueryProfile)entry.getValue(), currentVariant, allVariants);
        }
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
           return binding.hashCode() + 17*path.hashCode();
        }

        @Override
        public String toString() {
            return binding + " for path " + path;
        }

    }

}
