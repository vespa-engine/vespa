// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import java.util.Arrays;

/**
 * The sizes of a set of dimensions.
 *
 * @author bratseth
 */
public final class DimensionSizes {

    private final long[] sizes;

    private DimensionSizes(Builder builder) {
        this.sizes = builder.sizes;
        builder.sizes = null; // invalidate builder to avoid copying the array
    }

    /**
     * Returns the length of this in the nth dimension
     *
     * @throws IndexOutOfBoundsException if the index is larger than the number of dimensions in this tensor minus one
     */
    public long size(int dimensionIndex) { return sizes[dimensionIndex]; }

    /** Returns the number of dimensions this provides the size of */
    public int dimensions() { return sizes.length; }

    /** Returns the product of the sizes of this */
    public long totalSize() {
        long productSize = 1;
        for (long dimensionSize : sizes )
            productSize *= dimensionSize;
        return productSize;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof DimensionSizes)) return false;
        return Arrays.equals(((DimensionSizes) o).sizes, this.sizes);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(sizes); }

    /**
     * Builder of a set of dimension sizes.
     * Dimensions whose size is not set before building will get size 0.
     */
    public final static class Builder {

        private long[] sizes;

        public Builder(int dimensions) {
            this.sizes = new long[dimensions];
        }

        public Builder set(int dimensionIndex, long size) {
            sizes[dimensionIndex] = size;
            return this;
        }

        /**
         * Returns the length of this in the nth dimension
         *
         * @throws IndexOutOfBoundsException if the index is larger than the number of dimensions in this tensor minus one
         */
        public long size(int dimensionIndex) { return sizes[dimensionIndex]; }

        /** Returns the number of dimensions this provides the size of */
        public int dimensions() { return sizes.length; }

        /** Build this. This builder becomes invalid after calling this. */
        public DimensionSizes build() { return new DimensionSizes(this); }

    }

}
