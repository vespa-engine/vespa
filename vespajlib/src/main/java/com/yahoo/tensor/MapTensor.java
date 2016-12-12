// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A sparse implementation of a tensor backed by a Map of cells to values.
 *
 * @author bratseth
 */
@Beta
public class MapTensor implements Tensor {

    // TODO: Enforce that all addresses are dense (and then avoid storing keys in TensorAddress)
    
    private final TensorType type;

    private final ImmutableMap<TensorAddress, Double> cells;

    /** Creates a sparse tensor. The cell addresses must match the type. */
    public MapTensor(TensorType type, Map<TensorAddress, Double> cells) {
        this.type = type;
        this.cells = ImmutableMap.copyOf(cells);
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
            if (s.startsWith("tensor("))
                return fromTypedTensor(s);
            else if (s.startsWith("{"))
                return fromCellString(typeFromCellString(s), s);
            else
                return fromNumber(Double.parseDouble(s));
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Excepted a number or a string starting by { or tensor(, got '" + s + "'");
        }
    }

    private static MapTensor fromTypedTensor(String s) {
        int colonIndex = s.indexOf(':');
        if (colonIndex < 0 || s.length() < colonIndex + 1)
            throw new IllegalArgumentException("Expected tensorType:tensorValue, but got '" + s + "'");
        String typeSpec = s.substring(0, colonIndex);
        String valueSpec = s.substring(colonIndex +1 );
        return fromCellString(TensorTypeParser.fromSpec(typeSpec), valueSpec);
    }

    /** Derive the tensor type from the first address string in the given tensor string */
    private static TensorType typeFromCellString(String s) {
        s = s.substring(1).trim(); // remove tensor start
        int firstKeyOrEmptyTensorEnd = s.indexOf('}');
        String addressBody = s.substring(0, firstKeyOrEmptyTensorEnd).trim();
        if (addressBody.isEmpty()) return TensorType.empty; // Empty tensor

        addressBody = addressBody.substring(1); // remove key start
        if (addressBody.isEmpty()) return TensorType.empty; // Empty key

        TensorType.Builder builder = new TensorType.Builder();
        for (String elementString : addressBody.split(",")) {
            String[] pair = elementString.split(":");
            if (pair.length != 2)
                throw new IllegalArgumentException("Expecting argument elements to be on the form dimension:label, " +
                                                   "got '" + elementString + "'");
            builder.mapped(pair[0].trim());
        }

        return builder.build();
    }

    private static MapTensor fromCellString(TensorType type, String s) {
        s = s.trim().substring(1).trim();
        ImmutableMap.Builder<TensorAddress, Double> cells = new ImmutableMap.Builder<>();
        while (s.length() > 1) {
            int keyEnd = s.indexOf('}');
            TensorAddress address = addressFrom(type, s.substring(0, keyEnd+1));
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
        return new MapTensor(type, cellMap);
    }

    /** Creates a tenor address from a string on the form {dimension1:label1,dimension2:label2,...} */
    private static TensorAddress addressFrom(TensorType type, String mapAddressString) {
        mapAddressString = mapAddressString.trim();
        if ( ! (mapAddressString.startsWith("{") && mapAddressString.endsWith("}")))
            throw new IllegalArgumentException("Expecting a tensor address to be enclosed in {}, got '" + mapAddressString + "'");

        String addressBody = mapAddressString.substring(1, mapAddressString.length() - 1).trim();
        if (addressBody.isEmpty()) return TensorAddress.empty;

        TensorAddress.Builder builder = new TensorAddress.Builder(type);
        for (String elementString : addressBody.split(",")) {
            String[] pair = elementString.split(":");
            if (pair.length != 2)
                throw new IllegalArgumentException("Expecting argument elements to be on the form dimension:label, " +
                                                   "got '" + elementString + "'");
            builder.add(pair[0].trim(), pair[1].trim());
        }
        return builder.build();
    }

    private static MapTensor fromNumber(double number) {
        ImmutableMap.Builder<TensorAddress, Double> singleCell = new ImmutableMap.Builder<>();
        singleCell.put(TensorAddress.empty, number);
        return new MapTensor(TensorType.empty, singleCell.build());
    }
    
    private static Double asDouble(TensorAddress address, String s) {
        try {
            return Double.valueOf(s);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("At " + address + ": Expected a floating point number, got '" + s + "'");
        }
    }

    @Override
    public TensorType type() { return type; }

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
