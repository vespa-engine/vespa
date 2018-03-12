// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * An indexed (dense) tensor backed by a double array.
 *
 * @author bratseth
 */
public class IndexedTensor implements Tensor {

    /** The prescribed and possibly abstract type this is an instance of */
    private final TensorType type;

    /** The sizes of the dimensions of this in the order of the dimensions of the type */
    private final DimensionSizes dimensionSizes;

    private final double[] values;

    private IndexedTensor(TensorType type, DimensionSizes dimensionSizes, double[] values) {
        this.type = type;
        this.dimensionSizes = dimensionSizes;
        this.values = values;
    }

    @Override
    public long size() {
        return values.length;
    }

    /**
     * Returns an iterator over the cells of this.
     * Cells are returned in order of increasing indexes in each dimension, increasing
     * indexes of later dimensions in the dimension type before earlier.
     */
    @Override
    public Iterator<Cell> cellIterator() {
        return new CellIterator();
    }

    /** Returns an iterator over all the cells in this tensor which matches the given partial address */
    // TODO: Move up to Tensor and create a mixed tensor which can implement it (and subspace iterators) efficiently
    public SubspaceIterator cellIterator(PartialAddress partialAddress, DimensionSizes iterationSizes) {
        long[] startAddress = new long[type().dimensions().size()];
        List<Integer> iterateDimensions = new ArrayList<>();
        for (int i = 0; i < type().dimensions().size(); i++) {
            long partialAddressLabel = partialAddress.numericLabel(type.dimensions().get(i).name());
            if (partialAddressLabel >= 0) // iterate at this label
                startAddress[i] = partialAddressLabel;
            else // iterate over this dimension
                iterateDimensions.add(i);
        }
        return new SubspaceIterator(iterateDimensions, startAddress, iterationSizes);
    }

    /**
     * Returns an iterator over the values of this.
     * Values are returned in order of increasing indexes in each dimension, increasing
     * indexes of later dimensions in the dimension type before earlier.
     */
    @Override
    public Iterator<Double> valueIterator() {
        return new ValueIterator();
    }

    /**
     * Returns an iterator over value iterators where the outer iterator is over each unique value of the dimensions
     * given and the inner iterator is over each unique value of the rest of the dimensions, in the same order as
     * other iterator.
     *
     * @param dimensions the names of the dimensions of the superspace
     * @param sizes the size of each dimension in the space we are returning values for, containing
     *              one value per dimension of this tensor (in order). Each size may be the same or smaller
     *              than the corresponding size of this tensor
     */
    public Iterator<SubspaceIterator> subspaceIterator(Set<String> dimensions, DimensionSizes sizes) {
        return new SuperspaceIterator(dimensions, sizes);
    }

    /** Returns a subspace iterator having the sizes of the dimensions of this tensor */
    public Iterator<SubspaceIterator> subspaceIterator(Set<String> dimensions) {
        return subspaceIterator(dimensions, dimensionSizes);
    }

    /**
     * Returns the value at the given indexes
     *
     * @param indexes the indexes into the dimensions of this. Must be one number per dimension of this
     * @throws IndexOutOfBoundsException if any of the indexes are out of bound or a wrong number of indexes are given
     */
    public double get(long ... indexes) {
        return values[(int)toValueIndex(indexes, dimensionSizes)];
    }

    /** Returns the value at this address, or NaN if there is no value at this address */
    @Override
    public double get(TensorAddress address) {
        // optimize for fast lookup within bounds:
        try {
            return values[(int)toValueIndex(address, dimensionSizes)];
        }
        catch (IndexOutOfBoundsException e) {
            return Double.NaN;
        }
    }

    private double get(long valueIndex) { return values[(int)valueIndex]; }

    private static long toValueIndex(long[] indexes, DimensionSizes sizes) {
        if (indexes.length == 1) return indexes[0]; // for speed
        if (indexes.length == 0) return 0; // for speed

        long valueIndex = 0;
        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] >= sizes.size(i)) {
                throw new IndexOutOfBoundsException();
            }
            valueIndex += productOfDimensionsAfter(i, sizes) * indexes[i];
        }
        return valueIndex;
    }

    private static long toValueIndex(TensorAddress address, DimensionSizes sizes) {
        if (address.isEmpty()) return 0;

        long valueIndex = 0;
        for (int i = 0; i < address.size(); i++) {
            if (address.numericLabel(i) >= sizes.size(i)) {
                throw new IndexOutOfBoundsException();
            }
            valueIndex += productOfDimensionsAfter(i, sizes) * address.numericLabel(i);
        }
        return valueIndex;
    }

    private static long productOfDimensionsAfter(int afterIndex, DimensionSizes sizes) {
        long product = 1;
        for (int i = afterIndex + 1; i < sizes.dimensions(); i++)
            product *= sizes.size(i);
        return product;
    }

    @Override
    public TensorType type() { return type; }

    public DimensionSizes dimensionSizes() {
        return dimensionSizes;
    }

    @Override
    public Map<TensorAddress, Double> cells() {
        if (dimensionSizes.dimensions() == 0)
            return Collections.singletonMap(TensorAddress.of(), values[0]);

        ImmutableMap.Builder<TensorAddress, Double> builder = new ImmutableMap.Builder<>();
        Indexes indexes = Indexes.of(dimensionSizes, dimensionSizes, values.length);
        for (long i = 0; i < values.length; i++) {
            indexes.next();
            builder.put(indexes.toAddress(), values[(int)i]);
        }
        return builder.build();
    }

    @Override
    public int hashCode() { return Arrays.hashCode(values); }

    @Override
    public String toString() { return Tensor.toStandardString(this); }

    @Override
    public boolean equals(Object other) {
        if ( ! ( other instanceof Tensor)) return false;
        return Tensor.equals(this, ((Tensor)other));
    }

    public abstract static class Builder implements Tensor.Builder {

        final TensorType type;

        private Builder(TensorType type) {
            this.type = type;
        }

        public static Builder of(TensorType type) {
            if (type.dimensions().stream().allMatch(d -> d instanceof TensorType.IndexedBoundDimension))
                return new BoundBuilder(type);
            else
                return new UnboundBuilder(type);
        }

        /**
         * Create a builder with dimension size information for this instance. Must be one size entry per dimension,
         * and, agree with the type size information when specified in the type.
         * If sizes are completely specified in the type this size information is redundant.
         */
        public static Builder of(TensorType type, DimensionSizes sizes) {
            // validate
            if (sizes.dimensions() != type.dimensions().size())
                throw new IllegalArgumentException(sizes.dimensions() + " is the wrong number of dimensions " +
                                                   "for " + type);
            for (int i = 0; i < sizes.dimensions(); i++ ) {
                Optional<Long> size = type.dimensions().get(i).size();
                if (size.isPresent() && size.get() < sizes.size(i))
                    throw new IllegalArgumentException("Size of dimension " + type.dimensions().get(i).name() + " is " +
                                                       sizes.size(i) +
                                                       " but cannot be larger than " + size.get() + " in " + type);
            }

            return new BoundBuilder(type, sizes);
        }

        public abstract Builder cell(double value, long ... indexes);

        @Override
        public TensorType type() { return type; }

        @Override
        public abstract IndexedTensor build();

    }

    /** A bound builder can create the double array directly */
    public static class BoundBuilder extends Builder {

        private DimensionSizes sizes;
        private double[] values;

        private BoundBuilder(TensorType type) {
            this(type, dimensionSizesOf(type));
        }

        static DimensionSizes dimensionSizesOf(TensorType type) {
            DimensionSizes.Builder b = new DimensionSizes.Builder(type.dimensions().size());
            for (int i = 0; i < type.dimensions().size(); i++)
                b.set(i, type.dimensions().get(i).size().get());
            return b.build();
        }

        private BoundBuilder(TensorType type, DimensionSizes sizes) {
            super(type);
            if ( sizes.dimensions() != type.dimensions().size())
                throw new IllegalArgumentException("Must have a dimension size entry for each dimension in " + type);
            this.sizes = sizes;
            values = new double[(int)sizes.totalSize()];
        }

        @Override
        public BoundBuilder cell(double value, long ... indexes) {
            values[(int)toValueIndex(indexes, sizes)] = value;
            return this;
        }

        @Override
        public CellBuilder cell() {
            return new CellBuilder(type, this);
        }

        @Override
        public Builder cell(TensorAddress address, double value) {
            values[(int)toValueIndex(address, sizes)] = value;
            return this;
        }

        @Override
        public IndexedTensor build() {
            IndexedTensor tensor = new IndexedTensor(type, sizes, values);
            // prevent further modification
            sizes = null;
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
        public void cellByDirectIndex(long index, double value) {
            values[(int)index] = value;
        }

    }

    /**
     * A builder used when we don't know the size of the dimensions up front.
     * All values is all dimensions must be specified.
     */
    private static class UnboundBuilder extends Builder {

        /** List of List or Double */
        private List<Object> firstDimension = null;

        private UnboundBuilder(TensorType type) {
            super(type);
        }

        @Override
        public IndexedTensor build() {
            if (firstDimension == null) throw new IllegalArgumentException("Tensor of type " + type() + " has no values");

            if (type.dimensions().isEmpty()) // single number
                return new IndexedTensor(type, new DimensionSizes.Builder(type.dimensions().size()).build(), new double[] {(Double) firstDimension.get(0) });

            DimensionSizes dimensionSizes = findDimensionSizes(firstDimension);
            double[] values = new double[(int)dimensionSizes.totalSize()];
            fillValues(0, 0, firstDimension, dimensionSizes, values);
            return new IndexedTensor(type, dimensionSizes, values);
        }

        private DimensionSizes findDimensionSizes(List<Object> firstDimension) {
            List<Long> dimensionSizeList = new ArrayList<>(type.dimensions().size());
            findDimensionSizes(0, dimensionSizeList, firstDimension);
            DimensionSizes.Builder b = new DimensionSizes.Builder(type.dimensions().size()); // may be longer than the list but that's correct
            for (int i = 0; i < b.dimensions(); i++) {
                if (i < dimensionSizeList.size())
                    b.set(i, dimensionSizeList.get(i));
            }
            return b.build();
        }

        @SuppressWarnings("unchecked")
        private void findDimensionSizes(int currentDimensionIndex, List<Long> dimensionSizes, List<Object> currentDimension) {
            if (currentDimensionIndex == dimensionSizes.size())
                dimensionSizes.add((long)currentDimension.size());
            else if (dimensionSizes.get(currentDimensionIndex) != currentDimension.size())
                throw new IllegalArgumentException("Missing values in dimension " +
                                                   type.dimensions().get(currentDimensionIndex) + " in " + type);

            for (Object value : currentDimension)
                if (value instanceof List)
                    findDimensionSizes(currentDimensionIndex + 1, dimensionSizes, (List<Object>)value);
        }

        @SuppressWarnings("unchecked")
        private void fillValues(int currentDimensionIndex, long offset, List<Object> currentDimension,
                                DimensionSizes sizes, double[] values) {
            if (currentDimensionIndex < sizes.dimensions() - 1) { // recurse to next dimension
                for (long i = 0; i < currentDimension.size(); i++)
                    fillValues(currentDimensionIndex + 1,
                               offset + productOfDimensionsAfter(currentDimensionIndex, sizes) * i,
                               (List<Object>) currentDimension.get((int)i), sizes, values);
            } else { // last dimension - fill values
                for (long i = 0; i < currentDimension.size(); i++) {
                    values[(int)(offset + i)] = nullAsZero((Double)currentDimension.get((int)i)); // fill missing values as zero
                }
            }
        }

        private double nullAsZero(Double value) {
            if (value == null) return 0;
            return value;
        }

        @Override
        public CellBuilder cell() {
            return new CellBuilder(type, this);
        }

        @Override
        public Builder cell(TensorAddress address, double value) {
            long[] indexes = new long[address.size()];
            for (int i = 0; i < address.size(); i++) {
                indexes[i] = address.numericLabel(i);
            }
            cell(value, indexes);
            return this;
        }

        /**
         * Set a value using an index API. The number of indexes must be the same as the dimensions in the type of this.
         * Values can be written in any order but all values needed to make this dense must be provided
         * before building this.
         *
         * @return this for chaining
         */
        @SuppressWarnings("unchecked")
        @Override
        public Builder cell(double value, long... indexes) {
            if (indexes.length != type.dimensions().size())
                throw new IllegalArgumentException("Wrong number of indexes (" + indexes.length + ") for " + type);

            if (indexes.length == 0) {
                firstDimension = Collections.singletonList(value);
                return this;
            }

            if (firstDimension == null)
                firstDimension = new ArrayList<>();
            List<Object> currentValues = firstDimension;
            for (int dimensionIndex = 0; dimensionIndex < indexes.length; dimensionIndex++) {
                ensureCapacity(indexes[dimensionIndex], currentValues);
                if (dimensionIndex == indexes.length - 1) { // last dimension
                    currentValues.set((int)indexes[dimensionIndex], value);
                } else {
                    if (currentValues.get((int)indexes[dimensionIndex]) == null)
                        currentValues.set((int)indexes[dimensionIndex], new ArrayList<>());
                    currentValues = (List<Object>) currentValues.get((int)indexes[dimensionIndex]);
                }
            }
            return this;
        }

        /** Fill the given list with nulls if necessary to make sure it has a (possibly null) value at the given index */
        private void ensureCapacity(long index, List<Object> list) {
            while (list.size() <= index)
                list.add(list.size(), null);
        }

    }

    private final class CellIterator implements Iterator<Cell> {

        private long count = 0;
        private final Indexes indexes = Indexes.of(dimensionSizes, dimensionSizes, values.length);
        private final LazyCell reusedCell = new LazyCell(indexes, Double.NaN);

        @Override
        public boolean hasNext() {
            return count < indexes.size();
        }

        @Override
        public Cell next() {
            if ( ! hasNext()) throw new NoSuchElementException("No cell at " + indexes);
            count++;
            indexes.next();
            reusedCell.value = get(indexes.toSourceValueIndex());
            return reusedCell;
        }

    }

    private final class ValueIterator implements Iterator<Double> {

        private long count = 0;

        @Override
        public boolean hasNext() {
            return count < values.length;
        }

        @Override
        public Double next() {
            try {
                return values[(int)count++];
            }
            catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException("No element at position " + count);
            }
        }

    }

    private final class SuperspaceIterator implements Iterator<SubspaceIterator> {

        private final Indexes superindexes;

        /** The indexes this should iterate over */
        private final List<Integer> subdimensionIndexes;

        /**
         * The sizes of the space we'll return values of, one value for each dimension of this tensor,
         * which may be equal to or smaller than the sizes of this tensor
         */
        private final DimensionSizes iterateSizes;

        private long count = 0;

        private SuperspaceIterator(Set<String> superdimensionNames, DimensionSizes iterateSizes) {
            this.iterateSizes = iterateSizes;

            List<Integer> superdimensionIndexes = new ArrayList<>(superdimensionNames.size()); // for outer iterator
            subdimensionIndexes = new ArrayList<>(superdimensionNames.size()); // for inner iterator (max length)
            for (int i = type.dimensions().size() - 1; i >= 0; i-- ) { // iterate inner dimensions first
                if (superdimensionNames.contains(type.dimensions().get(i).name()))
                    superdimensionIndexes.add(i);
                else
                    subdimensionIndexes.add(i);
            }

            superindexes = Indexes.of(IndexedTensor.this.dimensionSizes, iterateSizes, superdimensionIndexes);
        }

        @Override
        public boolean hasNext() {
            return count < superindexes.size();
        }

        @Override
        public SubspaceIterator next() {
            if ( ! hasNext()) throw new NoSuchElementException("No cell at " + superindexes);
            count++;
            superindexes.next();
            return new SubspaceIterator(subdimensionIndexes, superindexes.indexesCopy(), iterateSizes);
        }

    }

    /**
     * An iterator over a subspace of this tensor. This is exposed to allow clients to query the size.
     * NOTE THAT the Cell returned by next is only valid until the next() call is made.
     * This is a concession to performance due to this typically being used in inner loops.
     */
    public final class SubspaceIterator implements Iterator<Tensor.Cell> {

        /**
         * This iterator will iterate over the given dimensions, in the order given
         * (the first dimension index given is incremented to exhaustion first (i.e is etc.).
         * This may be any subset of the dimensions given by address and dimensionSizes.
         */
        private final List<Integer> iterateDimensions;
        private final long[] address;
        private final DimensionSizes iterateSizes;

        private Indexes indexes;
        private long count = 0;

        /** A lazy cell for reuse */
        private final LazyCell reusedCell;

        /**
         * Creates a new subspace iterator
         *
         * @param iterateDimensions the dimensions to iterate over, given as indexes in the dimension order of the
         *                          type of the tensor this iterates over. This iterator will iterate over these
         *                          dimensions to exhaustion in the order given (the first dimension index given is
         *                          incremented  to exhaustion first (i.e is etc.), while other dimensions will be held
         *                          at a constant position.
         *                          This may be any subset of the dimensions given by address and dimensionSizes.
         *                          This is treated as immutable.
         * @param address the address of the first cell of this subspace.
         */
        private SubspaceIterator(List<Integer> iterateDimensions, long[] address, DimensionSizes iterateSizes) {
            this.iterateDimensions = iterateDimensions;
            this.address = address;
            this.iterateSizes = iterateSizes;
            this.indexes = Indexes.of(IndexedTensor.this.dimensionSizes, iterateSizes, iterateDimensions, address);
            reusedCell = new LazyCell(indexes, Double.NaN);
        }

        /** Returns the total number of cells in this subspace */
        public long size() {
            return indexes.size();
        }

        /** Returns the address of the cell this currently points to (which may be an invalid position) */
        public TensorAddress address() { return indexes.toAddress(); }

        /** Rewind this iterator to the first element */
        public void reset() {
            this.count = 0;
            this.indexes = Indexes.of(IndexedTensor.this.dimensionSizes, iterateSizes, iterateDimensions, address);
        }

        @Override
        public boolean hasNext() {
            return count < indexes.size();
        }

        /** Returns the next cell, which is valid until next() is called again */
        @Override
        public Cell next() {
            if ( ! hasNext()) throw new NoSuchElementException("No cell at " + indexes);
            count++;
            indexes.next();
            reusedCell.value = get(indexes.toSourceValueIndex());
            return reusedCell;
        }

    }

    /** A Cell which does not compute its TensorAddress unless it really has to */
    private final static class LazyCell extends Tensor.Cell {

        private double value;
        private Indexes indexes;

        private LazyCell(Indexes indexes, Double value) {
            super(null, value);
            this.indexes = indexes;
        }

        @Override
        long getDirectIndex() { return indexes.toIterationValueIndex(); }

        @Override
        public TensorAddress getKey() {
            return indexes.toAddress();
        }

        @Override
        public Double getValue() { return value; }

    }

    // TODO: Make dimensionSizes a class

    /**
     * An array of indexes into this tensor which are able to find the next index in the value order.
     * next() can be called once per element in the dimensions we iterate over. It must be called once
     * before accessing the first position.
     */
    public abstract static class Indexes {

        private final DimensionSizes sourceSizes;

        private final DimensionSizes iterationSizes;

        protected final long[] indexes;

        public static Indexes of(DimensionSizes sizes) {
            return of(sizes, sizes);
        }

        private static Indexes of(DimensionSizes sourceSizes, DimensionSizes iterateSizes) {
            return of(sourceSizes, iterateSizes, completeIterationOrder(iterateSizes.dimensions()));
        }

        private static Indexes of(DimensionSizes sourceSizes, DimensionSizes iterateSizes, long size) {
            return of(sourceSizes, iterateSizes, completeIterationOrder(iterateSizes.dimensions()), size);
        }

        private static Indexes of(DimensionSizes sourceSizes, DimensionSizes iterateSizes, List<Integer> iterateDimensions) {
            return of(sourceSizes, iterateSizes, iterateDimensions, computeSize(iterateSizes, iterateDimensions));
        }

        private static Indexes of(DimensionSizes sourceSizes, DimensionSizes iterateSizes, List<Integer> iterateDimensions, long size) {
            return of(sourceSizes, iterateSizes, iterateDimensions, new long[iterateSizes.dimensions()], size);
        }

        private static Indexes of(DimensionSizes sourceSizes, DimensionSizes iterateSizes, List<Integer> iterateDimensions, long[] initialIndexes) {
            return of(sourceSizes, iterateSizes, iterateDimensions, initialIndexes, computeSize(iterateSizes, iterateDimensions));
        }

        private static Indexes of(DimensionSizes sourceSizes, DimensionSizes iterateSizes, List<Integer> iterateDimensions, long[] initialIndexes, long size) {
            if (size == 0) {
                return new EmptyIndexes(sourceSizes, iterateSizes, initialIndexes); // we're told explicitly there are truly no values available
            }
            else if (size == 1) {
                return new SingleValueIndexes(sourceSizes, iterateSizes, initialIndexes); // with no (iterating) dimensions, we still return one value, not zero
            }
            else if (iterateDimensions.size() == 1) {
                if (sourceSizes.equals(iterateSizes))
                    return new EqualSizeSingleDimensionIndexes(sourceSizes, iterateDimensions.get(0), initialIndexes, size);
                else
                    return new SingleDimensionIndexes(sourceSizes, iterateSizes, iterateDimensions.get(0), initialIndexes, size); // optimization
            }
            else {
                if (sourceSizes.equals(iterateSizes))
                    return new EqualSizeMultiDimensionIndexes(sourceSizes, iterateDimensions, initialIndexes, size);
                else
                    return new MultiDimensionIndexes(sourceSizes, iterateSizes, iterateDimensions, initialIndexes, size);
            }
        }

        private static List<Integer> completeIterationOrder(int length) {
            List<Integer> iterationDimensions = new ArrayList<>(length);
            for (int i = 0; i < length; i++)
                iterationDimensions.add(length - 1 - i);
            return iterationDimensions;
        }

        private Indexes(DimensionSizes sourceSizes, DimensionSizes iterationSizes, long[] indexes) {
            this.sourceSizes = sourceSizes;
            this.iterationSizes = iterationSizes;
            this.indexes = indexes;
        }

        private static long computeSize(DimensionSizes sizes, List<Integer> iterateDimensions) {
            long size = 1;
            for (int iterateDimension : iterateDimensions)
                size *= sizes.size(iterateDimension);
            return size;
        }

        /** Returns the address of the current position of these indexes */
        private TensorAddress toAddress() {
            return TensorAddress.of(indexes);
        }

        public long[] indexesCopy() {
            return Arrays.copyOf(indexes, indexes.length);
        }

        /** Returns a copy of the indexes of this which must not be modified */
        public long[] indexesForReading() { return indexes; }

        long toSourceValueIndex() {
            return IndexedTensor.toValueIndex(indexes, sourceSizes);
        }

        long toIterationValueIndex() { return IndexedTensor.toValueIndex(indexes, iterationSizes); }

        DimensionSizes dimensionSizes() { return iterationSizes; }

        /** Returns an immutable list containing a copy of the indexes in this */
        public List<Long> toList() {
            ImmutableList.Builder<Long> builder = new ImmutableList.Builder<>();
            for (long index : indexes)
                builder.add(index);
            return builder.build();
        }

        @Override
        public String toString() {
            return "indexes " + Arrays.toString(indexes);
        }

        public abstract long size();

        public abstract void next();

    }

    private final static class EmptyIndexes extends Indexes {

        private EmptyIndexes(DimensionSizes sourceSizes, DimensionSizes iterateSizes, long[] indexes) {
            super(sourceSizes, iterateSizes, indexes);
        }

        @Override
        public long size() { return 0; }

        @Override
        public void next() {}

    }

    private final static class SingleValueIndexes extends Indexes {

        private SingleValueIndexes(DimensionSizes sourceSizes, DimensionSizes iterateSizes, long[] indexes) {
            super(sourceSizes, iterateSizes, indexes);
        }

        @Override
        public long size() { return 1; }

        @Override
        public void next() {}

    }

    private static class MultiDimensionIndexes extends Indexes {

        private final long size;

        private final List<Integer> iterateDimensions;

        private MultiDimensionIndexes(DimensionSizes sourceSizes, DimensionSizes iterateSizes, List<Integer> iterateDimensions, long[] initialIndexes, long size) {
            super(sourceSizes, iterateSizes, initialIndexes);
            this.iterateDimensions = iterateDimensions;
            this.size = size;

            // Initialize to the (virtual) position before the first cell
            indexes[iterateDimensions.get(0)]--;
        }

        /** Returns the number of values this will iterate over - i.e the product if the iterating dimension sizes */
        @Override
        public long size() {
            return size;
        }

        /**
         * Advances this to the next cell in the standard indexed tensor cell order.
         * The first call to this will put it at the first position.
         *
         * @throws RuntimeException if this is called more times than its size
         */
        @Override
        public void next() {
            int iterateDimensionsIndex = 0;
            while ( indexes[iterateDimensions.get(iterateDimensionsIndex)] + 1 == dimensionSizes().size(iterateDimensions.get(iterateDimensionsIndex))) {
                indexes[iterateDimensions.get(iterateDimensionsIndex)] = 0; // carry over
                iterateDimensionsIndex++;
            }
            indexes[iterateDimensions.get(iterateDimensionsIndex)]++;
        }

    }

    /** In this case we can reuse the source index computation for the iteration index */
    private final static class EqualSizeMultiDimensionIndexes extends MultiDimensionIndexes {

        private long lastComputedSourceValueIndex = -1;

        private EqualSizeMultiDimensionIndexes(DimensionSizes sizes, List<Integer> iterateDimensions, long[] initialIndexes, long size) {
            super(sizes, sizes, iterateDimensions, initialIndexes, size);
        }

        @Override
        long toSourceValueIndex() {
            return lastComputedSourceValueIndex = super.toSourceValueIndex();
        }

        // NOTE: We assume the source index always gets computed first. Otherwise using this will produce a runtime exception
        @Override
        long toIterationValueIndex() { return lastComputedSourceValueIndex; }

    }

    /** In this case we can keep track of indexes using a step instead of using the more elaborate computation */
    private final static class SingleDimensionIndexes extends Indexes {

        private final long size;

        private final int iterateDimension;

        /** Maintain this directly as an optimization for 1-d iteration */
        private long currentSourceValueIndex, currentIterationValueIndex;

        /** The iteration step in the value index space */
        private final long sourceStep, iterationStep;

        private SingleDimensionIndexes(DimensionSizes sourceSizes, DimensionSizes iterateSizes,
                                       int iterateDimension, long[] initialIndexes, long size) {
            super(sourceSizes, iterateSizes, initialIndexes);
            this.iterateDimension = iterateDimension;
            this.size = size;
            this.sourceStep = productOfDimensionsAfter(iterateDimension, sourceSizes);
            this.iterationStep = productOfDimensionsAfter(iterateDimension, iterateSizes);

            // Initialize to the (virtual) position before the first cell
            indexes[iterateDimension]--;
            currentSourceValueIndex = IndexedTensor.toValueIndex(indexes, sourceSizes);
            currentIterationValueIndex = IndexedTensor.toValueIndex(indexes, iterateSizes);
        }

        /** Returns the number of values this will iterate over - i.e the product if the iterating dimension sizes */
        @Override
        public long size() {
            return size;
        }

        /**
         * Advances this to the next cell in the standard indexed tensor cell order.
         * The first call to this will put it at the first position.
         *
         * @throws RuntimeException if this is called more times than its size
         */
        @Override
        public void next() {
            indexes[iterateDimension]++;
            currentSourceValueIndex += sourceStep;
            currentIterationValueIndex += iterationStep;
        }

        @Override
        long toSourceValueIndex() { return currentSourceValueIndex; }

        @Override
        long toIterationValueIndex() { return currentIterationValueIndex; }

    }

    /** In this case we only need to keep track of one index */
    private final static class EqualSizeSingleDimensionIndexes extends Indexes {

        private final long size;

        private final int iterateDimension;

        /** Maintain this directly as an optimization for 1-d iteration */
        private long currentValueIndex;

        /** The iteration step in the value index space */
        private final long step;

        private EqualSizeSingleDimensionIndexes(DimensionSizes sizes,
                                                int iterateDimension, long[] initialIndexes, long size) {
            super(sizes, sizes, initialIndexes);
            this.iterateDimension = iterateDimension;
            this.size = size;
            this.step = productOfDimensionsAfter(iterateDimension, sizes);

            // Initialize to the (virtual) position before the first cell
            indexes[iterateDimension]--;
            currentValueIndex = IndexedTensor.toValueIndex(indexes, sizes);
        }

        /** Returns the number of values this will iterate over - i.e the product if the iterating dimension sizes */
        @Override
        public long size() {
            return size;
        }

        /**
         * Advances this to the next cell in the standard indexed tensor cell order.
         * The first call to this will put it at the first position.
         *
         * @throws RuntimeException if this is called more times than its size
         */
        @Override
        public void next() {
            indexes[iterateDimension]++;
            currentValueIndex += step;
        }

        @Override
        long toSourceValueIndex() { return currentValueIndex; }

        @Override
        long toIterationValueIndex() { return currentValueIndex; }

    }

}
