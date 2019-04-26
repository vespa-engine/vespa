// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import java.util.Arrays;

/**
 * An indexed tensor implementation holding values as doubles
 *
 * @author bratseth
 */
class IndexedDoubleTensor extends IndexedTensor {

    private final double[] values;

    IndexedDoubleTensor(TensorType type, DimensionSizes dimensionSizes, double[] values) {
        super(type, dimensionSizes);
        this.values = values;
    }

    @Override
    public long size() {
        return values.length;
    }

    /**
     * Returns the value at the given index by direct lookup. Only use
     * if you know the underlying data layout.
     *
     * @param valueIndex the direct index into the underlying data.
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    @Override
    public double get(long valueIndex) { return values[(int)valueIndex]; }

    @Override
    public IndexedTensor withType(TensorType type) {
        throwOnIncompatibleType(type);
        return new IndexedDoubleTensor(type, dimensionSizes(), values);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(values); }

    /** A bound builder can create the double array directly */
    public static class BoundDoubleBuilder extends BoundBuilder {

        private double[] values;

        BoundDoubleBuilder(TensorType type) {
            this(type, dimensionSizesOf(type));
        }

        BoundDoubleBuilder(TensorType type, DimensionSizes sizes) {
            super(type, sizes);
            values = new double[(int)sizes.totalSize()];
        }

        @Override
        public IndexedTensor.BoundBuilder cell(double value, long ... indexes) {
            values[(int)toValueIndex(indexes, sizes())] = value;
            return this;
        }

        @Override
        public CellBuilder cell() {
            return new CellBuilder(type, this);
        }

        @Override
        public Builder cell(TensorAddress address, double value) {
            values[(int)toValueIndex(address, sizes())] = value;
            return this;
        }

        @Override
        public IndexedTensor build() {
            IndexedTensor tensor = new IndexedDoubleTensor(type, sizes(), values);
            // prevent further modification
            values = null;
            return tensor;
        }

        @Override
        public Builder cell(Cell cell, double value) {
            long directIndex = cell.getDirectIndex();
            if (directIndex >= 0) // optimization
                values[(int)directIndex] = value;
            else
                super.cell(cell, value);
            return this;
        }

        /**
         * Set a cell value by the index in the internal layout of this cell.
         * This requires knowledge of the internal layout of cells in this implementation, and should therefore
         * probably not be used (but when it can be used it is fast).
         */
        @Override
        public void cellByDirectIndex(long index, double value) {
            values[(int)index] = value;
        }

    }

}
