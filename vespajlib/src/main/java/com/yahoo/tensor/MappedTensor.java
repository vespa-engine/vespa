// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * A sparse implementation of a tensor backed by a Map of cells to values.
 *
 * @author bratseth
 */
@Beta
public class MappedTensor implements Tensor {

    private final TensorType type;

    private final ImmutableMap<TensorAddress, Double> cells;

    /** Creates a sparse tensor. The cell addresses must match the type. */
    private MappedTensor(TensorType type, Map<TensorAddress, Double> cells) {
        this.type = type;
        this.cells = ImmutableMap.copyOf(cells);
    }

    static MappedTensor from(TensorType type, String tensorString) {
        tensorString = tensorString.trim();
        if ( ! tensorString.startsWith("{"))
            throw new IllegalArgumentException("Expecting a tensor starting by {, got '" + tensorString+ "'");
        tensorString = tensorString.substring(1).trim();
        ImmutableMap.Builder<TensorAddress, Double> cells = new ImmutableMap.Builder<>();
        while (tensorString.length() > 1) {
            int keyEnd = tensorString.indexOf('}');
            TensorAddress address = addressFrom(type, tensorString.substring(0, keyEnd+1));
            tensorString = tensorString.substring(keyEnd + 1).trim();
            if ( ! tensorString.startsWith(":"))
                throw new IllegalArgumentException("Expecting a ':' after " + address + ", got '" + tensorString+ "'");
            int valueEnd = tensorString.indexOf(',');
            if (valueEnd < 0) { // last value
                valueEnd = tensorString.indexOf("}");
                if (valueEnd < 0)
                    throw new IllegalArgumentException("A tensor string must end by '}'");
            }
            Double value = asDouble(address, tensorString.substring(1, valueEnd).trim());
            cells.put(address, value);
            tensorString = tensorString.substring(valueEnd+1).trim();
        }

        ImmutableMap<TensorAddress, Double> cellMap = cells.build();
        return new MappedTensor(type, cellMap);
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

    public static class Builder implements Tensor.Builder {
    
        private final TensorType type;
        private final ImmutableMap.Builder<TensorAddress, Double> cells = new ImmutableMap.Builder<>();
    
        public Builder(TensorType type) {
            this.type = type;
        }
    
        public MappedCellBuilder cell() {
            return new MappedCellBuilder();
        }

        @Override
        public TensorType type() { return type; }

        @Override
        public Builder cell(TensorAddress address, double value) {
            cells.put(address, value);
            return this;
        }

        @Override
        public Builder cell(double value, int... labels) {
            cells.put(new TensorAddress(labels), value);
            return this;
        }

        @Override
        public MappedTensor build() {
            return new MappedTensor(type, cells.build());
        }
    
        public class MappedCellBuilder implements Tensor.Builder.CellBuilder {
    
            private final TensorAddress.Builder addressBuilder = new TensorAddress.Builder(MappedTensor.Builder.this.type);
    
            @Override
            public MappedCellBuilder label(String dimension, String label) {
                addressBuilder.add(dimension, label);
                return this;
            }

            @Override
            public MappedCellBuilder label(String dimension, int label) {
                return label(dimension, String.valueOf(label));
            }

            @Override
            public Builder value(double cellValue) {
                return MappedTensor.Builder.this.cell(addressBuilder.build(), cellValue);
            }
    
        }
    
    }
}
