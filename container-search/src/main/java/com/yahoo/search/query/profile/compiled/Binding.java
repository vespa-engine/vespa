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

    public static final Binding nullBinding = new Binding(Integer.MAX_VALUE, Map.of());

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

    /** Creates a binding from a map containing the exact bindings this will have */
    private Binding(int generality, Map<String, String> bindings) {
        this.generality = generality;

        // Map -> arrays to limit memory consumption and speed up evaluation
        dimensions = new String[bindings.size()];
        dimensionValues = new String[bindings.size()];

        int i = 0;
        for (Map.Entry<String,String> entry : bindings.entrySet()) {
            dimensions[i] = entry.getKey();
            dimensionValues[i] = entry.getValue();
            i++;
        }
        this.hashCode = Arrays.hashCode(dimensions) + 11 * Arrays.hashCode(dimensionValues);
    }

    Binding(DimensionalValue.BindingSpec spec, Map<String, String> bindings) {
        this.generality = 0; // Not used here

        // Map -> arrays to limit memory consumption and speed up evaluation
        dimensions = spec.dimensions();
        dimensionValues = new String[spec.dimensions().length];
        for (int i = 0; i < dimensions.length; i++) {
            dimensionValues[i] = bindings.get(dimensions[i]);
        }
        this.hashCode = Arrays.hashCode(dimensions) + 11 * Arrays.hashCode(dimensionValues);
    }

    /**
     * Returns whether this binding is a proper generalization of the given binding:
     * Meaning it contains a proper subset of the given bindings.
     */
    public boolean generalizes(Binding other) {
        if ( this.dimensions.length >= other.dimensions.length) return false;
        for (int i = 0; i < this.dimensions.length; i++) {
            int otherIndexOfDimension = this.indexOf(dimensions[i], other.dimensions);
            if (otherIndexOfDimension < 0) return false;
            if ( ! this.dimensionValues[i].equals(other.dimensionValues[otherIndexOfDimension])) return false;
        }
        return true;
    }

    private int indexOf(String value, String[] array) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value))
                return i;
        }
        return -1;
    }

    /** Returns true only if this binding is null (contains no values for its dimensions (if any) */
    public boolean isNull() { return dimensions.length == 0; }

    /** Do not change the returtned array */
    String[] dimensions() { return dimensions; }

    String[] dimensionValues() { return dimensionValues; }

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
    public boolean matches(Map<String, String> context) {
        for (int i = 0; i < dimensions.length; i++) {
            if ( ! dimensionValues[i].equals(context.get(dimensions[i]))) return false;
        }
        return true;
    }

    /**
     * Implements a partial ordering where more specific bindings come before less specific ones,
     * taking both the number of bindings and their positions into account (earlier dimensions
     * take precedence over later ones).
     * <p>
     * The order is not well defined for bindings in different dimensional spaces.
     */
    @Override
    public int compareTo(Binding other) {
        return Integer.compare(this.generality, other.generality);
    }

}
