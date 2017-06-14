// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.compiled;

import com.yahoo.search.query.profile.DimensionBinding;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * An immutable binding of a set of dimensions to values.
 * This binding is minimal in that it only includes dimensions which actually have values.
 *
 * @author bratseth
 */
public class Binding implements Comparable<Binding> {

    private static final int maxDimensions = 31;

    /**
     * A higher number means this is more general. This accounts for both the number and position of the bindings
     * in the dimensional space, such that bindings in earlier dimensions are matched before bindings in
     * later dimensions
     */
    private final int generality;

    /** The dimensions of this. Unenforced invariant: Content never changes. */
    private final String[] dimensions;

    /** The values of those dimensions. Unenforced invariant: Content never changes. */
    private final String[] dimensionValues;

    private final int hashCode;

    @SuppressWarnings("unchecked")
    public static final Binding nullBinding= new Binding(Integer.MAX_VALUE, Collections.<String,String>emptyMap());

    public static Binding createFrom(DimensionBinding dimensionBinding) {
        if (dimensionBinding.getDimensions().size() > maxDimensions)
            throw new IllegalArgumentException("More than 31 dimensions is not supported");

        int generality = 0;
        Map<String, String> context = new HashMap<>();
        if (dimensionBinding.getDimensions() == null || dimensionBinding.getDimensions().isEmpty()) { // TODO: Just have this return the nullBinding
            generality = Integer.MAX_VALUE;
        }
        else {
            for (int i = 0; i <= maxDimensions; i++) {
                String value = i < dimensionBinding.getDimensions().size() ? dimensionBinding.getValues().get(i) : null;
                if (value == null)
                    generality += Math.pow(2, maxDimensions - i-1);
                else
                    context.put(dimensionBinding.getDimensions().get(i), value);
            }
        }
        return new Binding(generality, context);
    }

    private Binding(int generality, Map<String, String> binding) {
        this.generality = generality;

        // Map -> arrays to limit memory consumption and speed up evaluation
        dimensions = new String[binding.size()];
        dimensionValues = new String[binding.size()];

        int i = 0;
        int bindingHash = 0;
        for (Map.Entry<String,String> entry : binding.entrySet()) {
            dimensions[i] = entry.getKey();
            dimensionValues[i] = entry.getValue();
            bindingHash += i * entry.getKey().hashCode() +  11 * i * entry.getValue().hashCode();
            i++;
        }
        this.hashCode = bindingHash;
    }

    /** Returns true only if this binding is null (contains no values for its dimensions (if any) */
    public boolean isNull() { return dimensions.length == 0; }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Binding[");
        for (int i = 0; i < dimensions.length; i++)
            b.append(dimensions[i]).append("=").append(dimensionValues[i]).append(",");
        if (dimensions.length > 0)
            b.setLength(b.length()-1);
        b.append("] (generality " + generality + ")");
        return b.toString();
    }

    /** Returns whether the given binding has exactly the same values as this */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (! (o instanceof Binding)) return false;
        Binding other = (Binding)o;
        return Arrays.equals(this.dimensions, other.dimensions)
            && Arrays.equals(this.dimensionValues, other.dimensionValues);
    }

    @Override
    public int hashCode() { return hashCode; }

    /**
     * Returns true if all the dimension values in this have the same values
     * in the given context.
     */
    public boolean matches(Map<String,String> context) {
        for (int i = 0; i < dimensions.length; i++) {
            if ( ! dimensionValues[i].equals(context.get(dimensions[i]))) return false;
        }
        return true;
    }

    /**
     * Implements a partial ordering where more specific bindings come before less specific ones,
     * taking both the number of bindings and their positions into account (earlier dimensions
     * take precedence over later ones.
     * <p>
     * The order is not well defined for bindings in different dimensional spaces.
     */
    @Override
    public int compareTo(Binding other) {
        return Integer.compare(this.generality, other.generality);
    }

}
