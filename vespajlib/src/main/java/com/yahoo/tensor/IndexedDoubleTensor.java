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
        if ( ! this.type().isRenamableTo(type))
            throw new IllegalArgumentException("Can not change type from " + this.type() + " to " + type +
                                               ": Types are not compatible");
        return new IndexedDoubleTensor(type, dimensionSizes(), values);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(values); }

}
