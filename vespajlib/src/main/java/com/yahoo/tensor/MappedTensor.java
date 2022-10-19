// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.collect.ImmutableMap;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;

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
    public double get(TensorAddress address) { return cells.getOrDefault(address, 0.0); }

    @Override
    public boolean has(TensorAddress address) { return cells.containsKey(address); }

    @Override
    public Iterator<Cell> cellIterator() { return new CellIteratorAdaptor(cells.entrySet().iterator()); }

    @Override
    public Iterator<Double> valueIterator() { return cells.values().iterator(); }

    @Override
    public Map<TensorAddress, Double> cells() { return cells; }

    @Override
    public Tensor withType(TensorType other) {
        if (!this.type.isRenamableTo(type)) {
            throw new IllegalArgumentException("MappedTensor.withType: types are not compatible. Current type: '" +
                    this.type + "', requested type: '" + type.toString() + "'");
        }
        return new MappedTensor(other, cells);
    }

    @Override
    public Tensor remove(Set<TensorAddress> addresses) {
        Tensor.Builder builder = Tensor.Builder.of(type());
        for (Iterator<Tensor.Cell> i = cellIterator(); i.hasNext(); ) {
            Tensor.Cell cell = i.next();
            TensorAddress address = cell.getKey();
            if ( ! addresses.contains(address)) {
                builder.cell(address, cell.getValue());
            }
        }
        return builder.build();
    }

    @Override
    public int hashCode() { return cells.hashCode(); }

    @Override
    public String toString() { return toString(true, true); }

    @Override
    public String toString(boolean withType, boolean shortForms) { return toString(withType, shortForms, Long.MAX_VALUE); }

    @Override
    public String toAbbreviatedString(boolean withType, boolean shortForms) {
        return toString(withType, shortForms, Math.max(2, 10 / (type().dimensions().stream().filter(d -> d.isMapped()).count() + 1)));
    }

    private String toString(boolean withType, boolean shortForms, long maxCells) {
        return Tensor.toStandardString(this, withType, shortForms, maxCells);
    }

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
        public Builder cell(TensorAddress address, float value) {
            return cell(address, (double)value);
        }

        @Override
        public Builder cell(TensorAddress address, double value) {
            cells.put(address, value);
            return this;
        }

        @Override
        public Builder cell(float value, long... labels) {
            return cell((double)value, labels);
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
