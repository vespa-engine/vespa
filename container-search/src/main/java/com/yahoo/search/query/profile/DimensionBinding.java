// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable, binding of a list of dimensions to dimension values
 *
 * @author bratseth
 */
public class DimensionBinding {

    /** The dimensions of this */
    private List<String> dimensions;

    /** The values matching those dimensions */
    private DimensionValues values;

    /** The binding from those dimensions to values, and possibly other values */
    private Map<String, String> context;

    public static final DimensionBinding nullBinding =
        new DimensionBinding(List.of(), DimensionValues.empty, null);

    public static final DimensionBinding invalidBinding =
        new DimensionBinding(List.of(), DimensionValues.empty, null);

    /** Whether the value array contains only nulls */
    private final boolean containsAllNulls;

    // NOTE: Map must be ordered
    public static DimensionBinding createFrom(Map<String, String> values) {
        return createFrom(new ArrayList<>(values.keySet()), values);
    }

    /** Creates a binding from a variant and a context. Any of the arguments may be null. */
    // NOTE: Map must be ordered
    public static DimensionBinding createFrom(List<String> dimensions, Map<String, String> context) {
        if (dimensions == null || dimensions.size() == 0) {
            if (context == null) return nullBinding;
            if (dimensions == null) return new DimensionBinding(null, DimensionValues.empty, context); // Null, but must preserve context
        }

        return new DimensionBinding(dimensions, extractDimensionValues(dimensions, context), context);
    }

    /** Creates a binding from a variant and a context. Any of the arguments may be null. */
    public static DimensionBinding createFrom(List<String> dimensions, DimensionValues dimensionValues) {
        if (dimensionValues == null || dimensionValues == DimensionValues.empty) return nullBinding;

        // If null, preserve raw material for creating a context later (in createFor)
        if (dimensions == null) return new DimensionBinding(null, dimensionValues, null);

        return new DimensionBinding(dimensions, dimensionValues, null);
    }

    /** Returns a binding for a (possibly) new set of variants. Variants may be null, but not bindings */
    public DimensionBinding createFor(List<String> newDimensions) {
        if (newDimensions == null) return this; // Note: Not necessarily null - if no new variants then keep the existing binding
        if (this.dimensions == newDimensions) return this; // Avoid creating a new object if the dimensions are the same

        Map<String,String> context = this.context;
        if (context == null)
            context = this.values.asContext(this.dimensions != null ? this.dimensions : newDimensions);
        return new DimensionBinding(newDimensions, extractDimensionValues(newDimensions, context), context);
    }

    /**
     * Creates a dimension binding. The dimensions list given should be unmodifiable.
     * The array will not be modified. The context is needed in order to convert this binding to another
     * given another set of variant dimensions.
     */
    private DimensionBinding(List<String> dimensions, DimensionValues values, Map<String, String> context) {
        this.dimensions = dimensions;
        this.values = values;
        this.context = context;
        containsAllNulls = values.isEmpty();
    }

    /** Returns a read-only list of the dimensions of this. This value is undefined if this isNull() */
    public List<String> getDimensions() { return dimensions; }

    /** Returns a context created from the dimensions and values of this */
    public Map<String, String> getContext() {
        if (context != null) return context;
        context = values.asContext(dimensions);
        return context;
    }

    /**
     * Returns the values for the dimensions of this. This value is undefined if this isEmpty()
     * This array is always of the same length as the
     * length of the dimension list - missing elements are represented as nulls.
     * This is never null but may be empty.
     */
    public DimensionValues getValues() { return values; }

    /** Returns true only if this binding is null (contains no values for its dimensions (if any) */
    public boolean isNull() { return dimensions == null || containsAllNulls; }

    /**
     * Returns an array of the dimension values corresponding to the dimensions of this from the given context,
     * in the corresponding order. The array is always of the same length as the number of dimensions.
     * Dimensions which are not set in this context get a null value.
     */
    private static DimensionValues extractDimensionValues(List<String> dimensions, Map<String,String> context) {
        String[] dimensionValues = new String[dimensions.size()];
        if (context == null || context.size() == 0) return DimensionValues.createFrom(dimensionValues);
        for (int i = 0; i < dimensions.size(); i++)
            dimensionValues[i] = context.get(dimensions.get(i));
        return DimensionValues.createFrom(dimensionValues);
    }

    /**
     * Combines this binding with another if compatible.
     * Two bindings are incompatible if
     * <ul>
     *     <li>They contain a different value for the same key, or</li>
     *     <li>They contain the same pair of dimensions in a different order</li>
     * </ul>
     *
     * @return the combined binding, or the special invalidBinding if these two bindings are incompatible
     */
    public DimensionBinding combineWith(DimensionBinding other) {
        List<String> d1 = getDimensions();
        List<String> d2 = other.getDimensions();
        DimensionValues v1 = getValues();
        DimensionValues v2 = other.getValues();
        List<String> dimensions = new ArrayList<>();
        List<String> values = new ArrayList<>();
        int i1 = 0, i2 = 0;
        while (i1 < d1.size() && i2 < d2.size()) {
            if (d1.get(i1).equals(d2.get(i2))) { // agreement on next dimension
                String s1 = v1.get(i1), s2 = v2.get(i2);
                if (s1 == null)
                    values.add(s2);
                else if (s2 == null || s1.equals(s2))
                    values.add(s1);
                else
                    return invalidBinding; // disagreement on next value

                dimensions.add(d1.get(i1));
                i1++;
                i2++;
            }
            else if ( ! d2.contains(d1.get(i1))) { // next dimension in d1 is independent from d2
                dimensions.add(d1.get(i1));
                values.add(v1.get(i1));
                i1++;
            }
            else if ( ! d1.contains(d2.get(i2))) { // next dimension in d2 is independent from d1
                dimensions.add(d2.get(i2));
                values.add(v2.get(i2));
                i2++;
            }
            else {
                return invalidBinding; // not independent and no agreement
            }
        }
        while (i1 < d1.size()) {
            dimensions.add(d1.get(i1));
            values.add(v1.get(i1));
            i1++;
        }
        while (i2 < d2.size()) {
            dimensions.add(d2.get(i2));
            values.add(v2.get(i2));
            i2++;
        }

        return DimensionBinding.createFrom(dimensions, DimensionValues.createFrom(values.toArray(new String[0])));
    }

    /** Returns true if this == invalidBinding */
    public boolean isInvalid() { return this == invalidBinding; }

    @Override
    public String toString() {
        if (isInvalid()) return "Invalid DimensionBinding";
        if (dimensions == null) return "DimensionBinding []";
        StringBuilder b = new StringBuilder("DimensionBinding [");
        for (int i = 0; i < dimensions.size(); i++) {
            b.append(dimensions.get(i)).append("=").append(values.get(i));
            if (i < dimensions.size()-1)
                b.append(", ");
        }
        b.append("]");
        return b.toString();
    }

    /** Two bindings are equal if they contain the same dimensions and the same non-null values */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (! (o instanceof DimensionBinding)) return false;
        DimensionBinding other = (DimensionBinding)o;
        if ( ! this.dimensions.equals(other.dimensions)) return false;
        if ( ! this.values.equals(other.values)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimensions, values);
    }

}
