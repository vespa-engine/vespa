// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.collect.ImmutableMap;

import java.util.Iterator;
import java.util.Map;

/**
 * A sparse implementation of a tensor backed by a Map of cells to values.
 *
 * @author bratseth
 */
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
    public long size() { return cells.size(); }

    @Override
    public double get(TensorAddress address) { return cells.getOrDefault(address, Double.NaN); }

    @Override
    public Iterator<Cell> cellIterator() { return new CellIteratorAdaptor(cells.entrySet().iterator()); }

    @Override
    public Iterator<Double> valueIterator() { return cells.values().iterator(); }

    @Override
    public Map<TensorAddress, Double> cells() { return cells; }

    @Override
    public int hashCode() { return cells.hashCode(); }

    @Override
    public String toString() { return Tensor.toStandardString(this); }

    @Override
    public boolean equals(Object other) {
        if ( ! ( other instanceof Tensor)) return false;
        return Tensor.equals(this, ((Tensor)other));
    }

    public static class Builder implements Tensor.Builder {

        private final TensorType type;
        private final ImmutableMap.Builder<TensorAddress, Double> cells = new ImmutableMap.Builder<>();

        public static Builder of(TensorType type) { return new Builder(type); }

        private Builder(TensorType type) {
            this.type = type;
        }

        public CellBuilder cell() {
            return new CellBuilder(type, this);
        }

        @Override
        public TensorType type() { return type; }

        @Override
        public Builder cell(TensorAddress address, double value) {
            cells.put(address, value);
            return this;
        }

        @Override
        public Builder cell(double value, long... labels) {
            cells.put(TensorAddress.of(labels), value);
            return this;
        }

        @Override
        public MappedTensor build() {
            return new MappedTensor(type, cells.build());
        }

    }

    private static class CellIteratorAdaptor implements Iterator<Cell> {

        private final Iterator<Map.Entry<TensorAddress, Double>> adaptedIterator;

        private CellIteratorAdaptor(Iterator<Map.Entry<TensorAddress, Double>> adaptedIterator) {
            this.adaptedIterator = adaptedIterator;
        }

        @Override
        public boolean hasNext() { return adaptedIterator.hasNext(); }

        @Override
        public Cell next() {
            Map.Entry<TensorAddress, Double> entry = adaptedIterator.next();
            return new Cell(entry.getKey(), entry.getValue());
        }

    }

}
