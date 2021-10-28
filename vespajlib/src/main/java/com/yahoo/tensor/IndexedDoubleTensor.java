// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    @Override
    public double get(long valueIndex) { return values[(int)valueIndex]; }

    @Override
    public float getFloat(long valueIndex) { return (float)get(valueIndex); }

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

        BoundDoubleBuilder(TensorType type, DimensionSizes sizes) {
            this(type, sizes, new double[(int)sizes.totalSize()]);
        }

        BoundDoubleBuilder(TensorType type, DimensionSizes sizes, double[] values) {
            super(type, sizes);
            this.values = values;
        }

        @Override
        public IndexedTensor.BoundBuilder cell(float value, long ... indexes) {
            return cell((double)value, indexes);
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
        public Builder cell(TensorAddress address, float value) {
            return cell(address, (double)value);
        }

        @Override
        public Builder cell(TensorAddress address, double value) {
            values[(int)toValueIndex(address, sizes(), type)] = value;
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
        public Builder cell(Cell cell, float value) {
            return cell(cell, (double)value);
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

        @Override
        public void cellByDirectIndex(long index, float value) {
            cellByDirectIndex(index, (double)value);
        }

        @Override
        public void cellByDirectIndex(long index, double value) {
            try {
                values[(int) index] = value;
            }
            catch (IndexOutOfBoundsException e) {
                throw new IllegalArgumentException("Can not set the cell at position " + index + " in a tensor " +
                                                   "of type " + type + ": Index is too large");
            }
        }

    }

}
