package com.yahoo.tensor;

import com.google.common.annotations.Beta;

import java.util.Arrays;

/**
 * The sizes of a set of dimensions.
 * 
 * @author bratseth
 */
@Beta
public final class DimensionSizes {

    private final int[] sizes;

    private DimensionSizes(Builder builder) {
        this.sizes = builder.sizes;
        builder.sizes = null; // invalidate builder to avoid copying the array
    }

    /**
     * Returns the length of this in the nth dimension
     *
     * @throws IndexOutOfBoundsException if the index is larger than the number of dimensions in this tensor minus one
     */
    public int size(int dimensionIndex) { return sizes[dimensionIndex]; }

    /** Returns the number of dimensions this provides the size of */
    public int dimensions() { return sizes.length; }

    /** Returns the product of the sizes of this */
    public int totalSize() {
        int productSize = 1;
        for (int dimensionSize : sizes )
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

        private int[] sizes;

        public Builder(int dimensions) {
            this.sizes = new int[dimensions];
        }

        public Builder set(int dimensionIndex, int size) {
            sizes[dimensionIndex] = size;
            return this;
        }

        /**
         * Returns the length of this in the nth dimension
         *
         * @throws IndexOutOfBoundsException if the index is larger than the number of dimensions in this tensor minus one
         */
        public int size(int dimensionIndex) { return sizes[dimensionIndex]; }

        /** Returns the number of dimensions this provides the size of */
        public int dimensions() { return sizes.length; }

        /** Build this. This builder becomes invalid after calling this. */
        public DimensionSizes build() { return new DimensionSizes(this); }

    }

}
