// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.search.query.profile.OverridableQueryProfile;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileVariants;

import java.util.*;

/**
 * Represents a set of query profile variants (more or less) as they were declared -
 * a helper to produce config, which is also using the "declared" representation
 *
 * @author bratseth
 */
public class DeclaredQueryProfileVariants {

    private final Map<String, VariantQueryProfile> variantQueryProfiles =new LinkedHashMap<>();

    public DeclaredQueryProfileVariants(QueryProfile profile) {
        // Recreates the declared view (settings per set of dimensions)
        // from the runtime view (dimension-value pairs per variable)
        // yes, this is a little backwards, but the complexity of two representations
        // is contained right here...
        // TODO: This has become unnecessary as the variants now retains the original structure
        for (Map.Entry<String, QueryProfileVariants.FieldValues> fieldValueEntry : profile.getVariants().getFieldValues().entrySet()) {
            for (QueryProfileVariants.FieldValue fieldValue : fieldValueEntry.getValue().asList()) {
                addVariant(fieldValueEntry.getKey(),
                           fieldValue.getValue(),
                           profile.getVariants().getVariant(fieldValue.getDimensionValues(), false).isOverridable(fieldValueEntry.getKey()),
                           fieldValue.getDimensionValues().getValues());
            }
        }

        for (QueryProfileVariants.FieldValue fieldValue : profile.getVariants().getInherited().asList()) {
            for (QueryProfile inherited : (List<QueryProfile>)fieldValue.getValue())
                addVariantInherited(inherited,fieldValue.getDimensionValues().getValues());
        }

        dereferenceCompoundedVariants(profile, "");
    }

    private void addVariant(String name, Object value, Boolean overridable, String[] dimensionValues) {
        String dimensionString = toCanonicalString(dimensionValues);
        VariantQueryProfile variant = variantQueryProfiles.get(dimensionString);
        if (variant == null) {
            variant = new VariantQueryProfile(dimensionValues);
            variantQueryProfiles.put(dimensionString, variant);
        }
        variant.getValues().put(name, value);
        if (overridable != null)
            variant.getOverriable().put(name, overridable);
    }

    private void addVariantInherited(QueryProfile inherited,String[] dimensionValues) {
        String dimensionString = toCanonicalString(dimensionValues);
        VariantQueryProfile variant = variantQueryProfiles.get(dimensionString);
        if (variant == null) {
            variant = new VariantQueryProfile(dimensionValues);
            variantQueryProfiles.put(dimensionString, variant);
        }
        variant.inherit(inherited);
    }

    private void dereferenceCompoundedVariants(QueryProfile profile,String prefix) {
        // A variant of a.b is represented as the value a pointing to an anonymous profile a
        // having the variants
        for (Map.Entry<String,Object> entry : profile.declaredContent().entrySet()) {
            if ( ! (entry.getValue() instanceof QueryProfile)) continue;
            QueryProfile subProfile=(QueryProfile)entry.getValue();
            // Export if defined implicitly in this, or if this contains overrides
            if (!subProfile.isExplicit() || subProfile instanceof OverridableQueryProfile) {
                String entryPrefix=prefix + entry.getKey() + ".";
                dereferenceCompoundedVariants(subProfile.getVariants(),entryPrefix);
                dereferenceCompoundedVariants(subProfile,entryPrefix);
            }
        }

        if (profile.getVariants()==null) return;
        // We need to do the same dereferencing to overridables pointed to by variants of this
        for (Map.Entry<String,QueryProfileVariants.FieldValues> fieldValueEntry : profile.getVariants().getFieldValues().entrySet()) {
            for (QueryProfileVariants.FieldValue fieldValue : fieldValueEntry.getValue().asList()) {
                if ( ! (fieldValue.getValue() instanceof QueryProfile)) continue;
                QueryProfile subProfile=(QueryProfile)fieldValue.getValue();
                // Export if defined implicitly in this, or if this contains overrides
                if (!subProfile.isExplicit() || subProfile instanceof OverridableQueryProfile) {
                    String entryPrefix=prefix + fieldValueEntry.getKey() + ".";
                    dereferenceCompoundedVariants(subProfile.getVariants(),entryPrefix);
                    dereferenceCompoundedVariants(subProfile,entryPrefix);
                }
            }
        }
    }

    private void dereferenceCompoundedVariants(QueryProfileVariants profileVariants, String prefix) {
        if (profileVariants == null) return;
        for (Map.Entry<String, QueryProfileVariants.FieldValues> fieldVariant : profileVariants.getFieldValues().entrySet()) {
            for (QueryProfileVariants.FieldValue variantValue : fieldVariant.getValue().asList()) {
                addVariant(prefix + fieldVariant.getKey(), variantValue.getValue(), null, variantValue.getDimensionValues().getValues());
            }
        }
    }

    public String toCanonicalString(String[] dimensionValues) {
        StringBuilder b = new StringBuilder();
        for (String dimensionValue : dimensionValues) {
            if (dimensionValue != null)
                b.append(dimensionValue);
            else
                b.append("*");
            b.append(",");
        }
        b.deleteCharAt(b.length() - 1); // Remove last,
        return b.toString();
    }

    public Map<String, VariantQueryProfile> getVariantQueryProfiles() { return variantQueryProfiles; }

    public static class VariantQueryProfile {

        private final Map<String, Object> values = new LinkedHashMap<>();

        private final Map<String, Boolean> overridable = new HashMap<>();

        private final List<QueryProfile> inherited = new ArrayList<>();

        private final String[] dimensionValues;

        public VariantQueryProfile(String[] dimensionValues) {
            this.dimensionValues = dimensionValues;
        }

        public String[] getDimensionValues() { return dimensionValues; }

        public void inherit(QueryProfile inheritedProfile) {
            inherited.add(inheritedProfile);
        }

        public List<QueryProfile> inherited() { return inherited; }

        public Map<String, Object> getValues() { return values; }

        public Map<String, Boolean> getOverriable() { return overridable; }

    }

}
