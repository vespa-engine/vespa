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
    
        public static Builder of(TensorType type) { return new Builder(type); }

        private Builder(TensorType type) {
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
