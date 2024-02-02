package com.yahoo.tensor;

/**
 * Utility class for efficient access and iteration along dimensions in Indexed tensors.
 * Usage: Use setIndex to lock the indexes of the dimensions that don't change in this iteration.
 *        long base = addr.getDirectIndex();
 *        long stride = addr.getStride(dimension)
 *        i = 0...size_of_dimension
 *            double value = tensor.get(base + i * stride);
 *
 * @author baldersheim
 */
public final class DirectIndexedAddress {

    private final DimensionSizes sizes;
    private final int[] indexes;
    private long directIndex;

    private DirectIndexedAddress(DimensionSizes sizes) {
        this.sizes = sizes;
        indexes = new int[sizes.dimensions()];
        directIndex = 0;
    }

    public static DirectIndexedAddress of(DimensionSizes sizes) {
        return new DirectIndexedAddress(sizes);
    }

    /** Sets the current index of a dimension */
    public void setIndex(int dimension, int index) {
        if (index < 0 || index >= sizes.size(dimension)) {
            throw new IndexOutOfBoundsException("Index " + index + " outside of [0," + sizes.size(dimension) + ">");
        }
        int diff = index - indexes[dimension];
        directIndex += getStride(dimension) * diff;
        indexes[dimension] = index;
    }

    /** Retrieve the index that can be used for direct lookup in an indexed tensor. */
    public long getDirectIndex() { return directIndex; }

    public long [] getIndexes() {
        long[] asLong = new long[indexes.length];
        for (int i=0; i < indexes.length; i++) {
            asLong[i] = indexes[i];
        }
        return asLong;
    }

    /** returns the stride to be used for the given dimension */
    public long getStride(int dimension) {
        return sizes.productOfDimensionsAfter(dimension);
    }

}
