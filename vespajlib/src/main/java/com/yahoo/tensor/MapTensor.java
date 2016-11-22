// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * A sparse implementation of a tensor backed by a Map of cells to values.
 *
 * @author bratseth
 */
@Beta
public class MapTensor implements Tensor {

    private final ImmutableSet<String> dimensions;

    private final ImmutableMap<TensorAddress, Double> cells;

    /** Creates a sparse tensor where the dimensions are determined by the cells */
    public MapTensor(Map<TensorAddress, Double> cells) {
        this(dimensionsOf(cells.keySet()), cells);
    }

    /** Creates a sparse tensor */
    public MapTensor(Set<String> dimensions, Map<TensorAddress, Double> cells) {
        ensureValidDimensions(cells, dimensions);
        this.dimensions = ImmutableSet.copyOf(dimensions);
        this.cells = ImmutableMap.copyOf(cells);
    }

    private void ensureValidDimensions(Map<TensorAddress, Double> cells, Set<String> dimensions) {
        for (TensorAddress address : cells.keySet())
            if ( ! dimensions.containsAll(address.dimensions()))
                throw new IllegalArgumentException("Cell address " + address + " is outside this tensors dimensions " +
                                                   dimensions);
    }

    /**
     * Creates a tensor from the string form returned by the {@link #toString} of this.
     *
     * @param s the tensor string
     * @throws IllegalArgumentException if the string is not in the correct format
     */
    public static MapTensor from(String s) {
        s = s.trim();
        try {
            if (s.startsWith("("))
                return fromTensorWithEmptyDimensions(s);
            else if (s.startsWith("{"))
                return fromTensor(s, Collections.emptySet());
            else
                return fromNumber(Double.parseDouble(s));
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Excepted a number or a string starting by { or (, got '" + s + "'");
        }
    }

    private static MapTensor fromTensorWithEmptyDimensions(String s) {
        s = s.substring(1).trim();
        int multiplier = s.indexOf("*");
        if (multiplier < 0 || ! s.endsWith(")"))
            throw new IllegalArgumentException("Expected a tensor on the form ({dimension:-,...}*{{cells}}), got '" + s + "'");
        MapTensor dimensionTensor = fromTensor(s.substring(0, multiplier).trim(), Collections.emptySet());
        return fromTensor(s.substring(multiplier + 1, s.length() - 1), dimensionTensor.dimensions());
    }

    private static MapTensor fromTensor(String s, Set<String> additionalDimensions) {
        s = s.trim().substring(1).trim();
        ImmutableMap.Builder<TensorAddress, Double> cells = new ImmutableMap.Builder<>();
        while (s.length() > 1) {
            int keyEnd = s.indexOf('}');
            TensorAddress address = TensorAddress.from(s.substring(0, keyEnd+1));
            s = s.substring(keyEnd + 1).trim();
            if ( ! s.startsWith(":"))
                throw new IllegalArgumentException("Expecting a ':' after " + address + ", got '" + s + "'");
            int valueEnd = s.indexOf(',');
            if (valueEnd < 0) { // last value
                valueEnd = s.indexOf("}");
                if (valueEnd < 0)
                    throw new IllegalArgumentException("A tensor string must end by '}'");
            }
            Double value = asDouble(address, s.substring(1, valueEnd).trim());
            cells.put(address, value);
            s = s.substring(valueEnd+1).trim();
        }

        ImmutableMap<TensorAddress, Double> cellMap = cells.build();
        Set<String> dimensions = dimensionsOf(cellMap.keySet());
        dimensions.addAll(additionalDimensions);
        return new MapTensor(dimensions, cellMap);
    }
    
    private static MapTensor fromNumber(double number) {
        ImmutableMap.Builder<TensorAddress, Double> singleCell = new ImmutableMap.Builder<>();
        singleCell.put(TensorAddress.empty, number);
        return new MapTensor(ImmutableSet.of(), singleCell.build());
    }

    private static Double asDouble(TensorAddress address, String s) {
        try {
            return Double.valueOf(s);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("At " + address + ": Expected a floating point number, got '" + s + "'");
        }
    }

    private static Set<String> dimensionsOf(Set<TensorAddress> addresses) {
        Set<String> dimensions = new HashSet<>();
        for (TensorAddress address : addresses)
            for (TensorAddress.Element element : address.elements())
                dimensions.add(element.dimension());
        return dimensions;
    }

    @Override
    public Set<String> dimensions() { return dimensions; }

    @Override
    public Map<TensorAddress, Double> cells() { return cells; }

    @Override
    public double get(TensorAddress address) { return cells.getOrDefault(address, Double.NaN); }

    @Override
    public int hashCode() { return cells.hashCode(); }

    @Override
    public String toString() { return Tensor.toStandardString(this); }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof Tensor)) return false;
        return Tensor.equals(this, (Tensor)o);
    }

}
