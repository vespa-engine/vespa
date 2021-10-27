// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import java.util.Arrays;

/**
 * An indexed tensor implementation holding values as floats
 *
 * @author bratseth
 */
class IndexedFloatTensor extends IndexedTensor {

    private final float[] values;

    IndexedFloatTensor(TensorType type, DimensionSizes dimensionSizes, float[] values) {
        super(type, dimensionSizes);
        this.values = values;
    }

    @Override
    public long size() {
        return values.length;
    }

    @Override
    public double get(long valueIndex) { return getFloat(valueIndex); }

    @Override
    public float getFloat(long valueIndex) { return values[(int)valueIndex]; }

    @Override
    public IndexedTensor withType(TensorType type) {
        throwOnIncompatibleType(type);
        return new IndexedFloatTensor(type, dimensionSizes(), values);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(values); }

    /** A bound builder can create the float array directly */
    public static class BoundFloatBuilder extends BoundBuilder {

        private float[] values;

        BoundFloatBuilder(TensorType type, DimensionSizes sizes) {
            this(type, sizes, new float[(int)sizes.totalSize()]);
        }

        BoundFloatBuilder(TensorType type, DimensionSizes sizes, float[] values) {
            super(type, sizes);
            if (sizes.totalSize() != values.length) {
                throw new IllegalArgumentException("Invalid size("  + values.length + ") of supplied value vector." +
                                                   " Type specifies that size should be " + sizes.totalSize());
            }
            this.values = values;
        }

        @Override
        public IndexedTensor.BoundBuilder cell(double value, long ... indexes) {
            return cell((float)value, indexes);
        }

        @Override
        public IndexedTensor.BoundBuilder cell(float value, long ... indexes) {
            values[(int)toValueIndex(indexes, sizes())] = value;
            return this;
        }

        @Override
        public CellBuilder cell() {
            return new CellBuilder(type, this);
        }

        @Override
        public Builder cell(TensorAddress address, double value) {
            return cell(address, (float)value);
        }

        @Override
        public Builder cell(TensorAddress address, float value) {
            values[(int)toValueIndex(address, sizes(), type)] = value;
            return this;
        }

        @Override
        public IndexedTensor build() {
            IndexedTensor tensor = new IndexedFloatTensor(type, sizes(), values);
            // prevent further modification
            values = null;
            return tensor;
        }

        @Override
        public Builder cell(Cell cell, double value) {
            return cell(cell, (float)value);
        }

        @Override
        public Builder cell(Cell cell, float value) {
            long directIndex = cell.getDirectIndex();
            if (directIndex >= 0) // optimization
                values[(int)directIndex] = value;
            else
                super.cell(cell, value);
            return this;
        }

        @Override
        public void cellByDirectIndex(long index, double value) {
            cellByDirectIndex(index, (float)value);
        }

        @Override
        public void cellByDirectIndex(long index, float value) {
            values[(int)index] = value;
        }

    }

}
