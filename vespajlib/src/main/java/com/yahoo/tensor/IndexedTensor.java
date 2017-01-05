// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.annotations.Beta;
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
@Beta
public class IndexedTensor implements Tensor {

    /** The prescribed and possibly abstract type this is an instance of */
    private final TensorType type;
    
    /** The sizes of the dimensions of this in the order of the dimensions of the type */
    private final int[] dimensionSizes;
    
    private final double[] values;
    
    private IndexedTensor(TensorType type, int[] dimensionSizes, double[] values) {
        this.type = type;
        this.dimensionSizes = dimensionSizes;
        this.values = values;
    }

    @Override
    public int size() {
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
     * @param dimensionSizes the size of each dimension in the space we are returning values for, containing
     *                       one value per dimension of this tensor (in order). Each size may be the same or smaller
     *                       than the corresponding size of this tensor
     */
    public Iterator<SubspaceIterator> subspaceIterator(Set<String> dimensions, int[] dimensionSizes) {
        return new SuperspaceIterator(dimensions, dimensionSizes);
    }

    /** Returns a subspace iterator having the sizes of the dimensions of this tensor */
    public Iterator<SubspaceIterator> subspaceIterator(Set<String> dimensions) {
        return subspaceIterator(dimensions, dimensionSizes);
    }

    /** Returns whether the dimensions sizes of this are equal to the given sizes */
    // TODO: Replace by returning immutable sizes when DimensionSizes are a class
    public boolean dimensionSizesAre(int[] dimensionSizes) {
        return Arrays.equals(dimensionSizes, this.dimensionSizes);
    }
    
    /** 
     * Returns the value at the given indexes
     * 
     * @param indexes the indexes into the dimensions of this. Must be one number per dimension of this
     * @throws IndexOutOfBoundsException if any of the indexes are out of bound or a wrong number of indexes are given
     */
    public double get(int ... indexes) {
        if (values.length == 0) return Double.NaN;
        return values[toValueIndex(indexes, dimensionSizes)];
    }

    /** Returns the value at this address, or NaN if there is no value at this address */
    @Override
    public double get(TensorAddress address) {
        // optimize for fast lookup within bounds:
        try {
            return values[toValueIndex(address, dimensionSizes)];
        }
        catch (IndexOutOfBoundsException e) {
            return Double.NaN;
        }
    }

    double get(int valueIndex) { return values[valueIndex]; }
    
    /** Returns the value at these indexes */
    private double get(Indexes indexes) {
        return values[toValueIndex(indexes.indexesForReading(), dimensionSizes)];
    }

    private static int toValueIndex(int[] indexes, int[] dimensionSizes) {
        if (indexes.length == 1) return indexes[0]; // for speed
        if (indexes.length == 0) return 0; // for speed

        int valueIndex = 0;
        for (int i = 0; i < indexes.length; i++)
            valueIndex += productOfDimensionsAfter(i, dimensionSizes) * indexes[i];
        return valueIndex;
    }

    private static int toValueIndex(TensorAddress address, int[] dimensionSizes) {
        if (address.isEmpty()) return 0;

        int valueIndex = 0;
        for (int i = 0; i < address.size(); i++)
            valueIndex += productOfDimensionsAfter(i, dimensionSizes) * address.intLabel(i);
        return valueIndex;
    }

    private static int productOfDimensionsAfter(int afterIndex, int[] dimensionSizes) {
        int product = 1;
        for (int i = afterIndex + 1; i < dimensionSizes.length; i++)
            product *= dimensionSizes[i];
        return product;
    }

    @Override
    public TensorType type() { return type; }

    /**
     * Returns the length of this in the nth dimension
     *
     * @throws IndexOutOfBoundsException if the index is larger than the number of dimensions in this tensor minus one
     */
    public int size(int dimension) {
        return dimensionSizes[dimension];
    }

    @Override
    public Map<TensorAddress, Double> cells() {
        if (dimensionSizes.length == 0)
            return values.length == 0 ? Collections.emptyMap() : Collections.singletonMap(TensorAddress.empty, values[0]);
        
        ImmutableMap.Builder<TensorAddress, Double> builder = new ImmutableMap.Builder<>();
        Indexes indexes = Indexes.of(dimensionSizes, dimensionSizes, values.length);
        for (int i = 0; i < values.length; i++) {
            indexes.next();
            builder.put(indexes.toAddress(), values[i]);
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
        public static Builder of(TensorType type, int[] dimensionSizes) {
            // validate
            if (dimensionSizes.length != type.dimensions().size())
                throw new IllegalArgumentException(dimensionSizes.length + " is the wrong number of dimension sizes " + 
                                                   " for " + type);
            for (int i = 0; i < dimensionSizes.length; i++ ) {
                Optional<Integer> size = type.dimensions().get(i).size();
                if (size.isPresent() && size.get() < dimensionSizes[i])
                    throw new IllegalArgumentException("Size of " + type.dimensions() + " is " + dimensionSizes[i] +
                                                       " but cannot be larger than " + size.get());
            }
            
            return new BoundBuilder(type, dimensionSizes);
        }

        public abstract Builder cell(double value, int ... indexes);

        protected double[] arrayFor(int[] dimensionSizes) {
            int productSize = 1;
            for (int dimensionSize : dimensionSizes)
                productSize *= dimensionSize;
            return new double[productSize];
        }

        @Override
        public TensorType type() { return type; }

        @Override
        public abstract IndexedTensor build();

    }
    
    /** A bound builder can create the double array directly */
    private static class BoundBuilder extends Builder {

        private int[] dimensionSizes;
        private double[] values;

        private BoundBuilder(TensorType type) {
            this(type, dimensionSizesOf(type));
        }

        private BoundBuilder(TensorType type, int[] dimensionSizes) {
            super(type);
            if ( dimensionSizes.length != type.dimensions().size())
                throw new IllegalArgumentException("Must have a dimension size entry for each dimension in " + type);
            this.dimensionSizes = dimensionSizes;
            values = arrayFor(dimensionSizes);
            Arrays.fill(values, Double.NaN);
        }
        
        private static int[] dimensionSizesOf(TensorType type) {
            int[] dimensionSizes = new int[type.dimensions().size()];
            for (int i = 0; i < type.dimensions().size(); i++)
                dimensionSizes[i] = type.dimensions().get(i).size().get();
            return dimensionSizes;
        }

        @Override
        public BoundBuilder cell(double value, int ... indexes) {
            values[toValueIndex(indexes, dimensionSizes)] = value;
            return this;
        }
        
        @Override
        public CellBuilder cell() {
            return new CellBuilder(type, this);
        }

        @Override
        public Builder cell(TensorAddress address, double value) {
            values[toValueIndex(address, dimensionSizes)] = value;
            return this;
        }

        @Override
        public IndexedTensor build() {
            // Note that we do not check for no NaN's here for performance reasons. 
            // NaN's don't get lost so leaving them in place should be quite benign 
            if (values.length == 1 && Double.isNaN(values[0]))
                values = new double[0];
            IndexedTensor tensor = new IndexedTensor(type, dimensionSizes, values);
            // prevent further modification
            dimensionSizes = null;
            values = null;
            return tensor;
        }

        @Override
        public Builder cell(Cell cell, double value) {
            // TODO: Use internal index if applicable
            // values[internalIndex] = value;
            // return this;
            int directIndex = cell.getDirectIndex();
            if (directIndex >= 0) // optimization
                values[directIndex] = value;
            else
                super.cell(cell, value);
            return this;
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
            if (firstDimension == null) // empty
                return new IndexedTensor(type, new int[type.dimensions().size()], new double[] {});
            if (type.dimensions().isEmpty()) // single number
                return new IndexedTensor(type, new int[type.dimensions().size()], new double[] {(Double) firstDimension.get(0) });

            int[] dimensionSizes = findDimensionSizes(firstDimension);
            double[] values = arrayFor(dimensionSizes);
            fillValues(0, 0, firstDimension, dimensionSizes, values);
            return new IndexedTensor(type, dimensionSizes, values);
        }
        
        private int[] findDimensionSizes(List<Object> firstDimension) {
            List<Integer> dimensionSizeList = new ArrayList<>(type.dimensions().size());
            findDimensionSizes(0, dimensionSizeList, firstDimension);
            int[] dimensionSizes = new int[type.dimensions().size()]; // may be longer than the list but that's correct
            for (int i = 0; i < dimensionSizes.length; i++)
                dimensionSizes[i] = dimensionSizeList.get(i);
            return dimensionSizes;
        }

        @SuppressWarnings("unchecked")
        private void findDimensionSizes(int currentDimensionIndex, List<Integer> dimensionSizes, List<Object> currentDimension) {
            if (currentDimensionIndex == dimensionSizes.size())
                dimensionSizes.add(currentDimension.size());
            else if (dimensionSizes.get(currentDimensionIndex) != currentDimension.size())
                throw new IllegalArgumentException("Missing values in dimension " + 
                                                   type.dimensions().get(currentDimensionIndex) + " in " + type);
            
            for (Object value : currentDimension)
                if (value instanceof List)
                    findDimensionSizes(currentDimensionIndex + 1, dimensionSizes, (List<Object>)value);
        }

        @SuppressWarnings("unchecked")
        private void fillValues(int currentDimensionIndex, int offset, List<Object> currentDimension, 
                                int[] dimensionSizes, double[] values) {
            if (currentDimensionIndex < dimensionSizes.length - 1) { // recurse to next dimension
                for (int i = 0; i < currentDimension.size(); i++)
                    fillValues(currentDimensionIndex + 1,
                               offset + productOfDimensionsAfter(currentDimensionIndex, dimensionSizes) * i,
                               (List<Object>) currentDimension.get(i), dimensionSizes, values);
            } else { // last dimension - fill values
                for (int i = 0; i < currentDimension.size(); i++)
                    values[offset + i] = (double) currentDimension.get(i);
            }
        }

        @Override
        public CellBuilder cell() {
            return new CellBuilder(type, this);
        }

        @Override
        public Builder cell(TensorAddress address, double value) {
            int[] indexes = new int[address.size()];
            for (int i = 0; i < address.size(); i++) {
                indexes[i] = address.intLabel(i);
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
        public Builder cell(double value, int... indexes) {
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
                    currentValues.set(indexes[dimensionIndex], value);
                } else {
                    if (currentValues.get(indexes[dimensionIndex]) == null)
                        currentValues.set(indexes[dimensionIndex], new ArrayList<>());
                    currentValues = (List<Object>) currentValues.get(indexes[dimensionIndex]);
                }
            }
            return this;
        }

        /** Fill the given list with nulls if necessary to make sure it has a (possibly null) value at the given index */
        private void ensureCapacity(int index, List<Object> list) {
            while (list.size() <= index)
                list.add(list.size(), null);
        }

    }
    
    private final class CellIterator implements Iterator<Cell> {

        private int count = 0;
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

        private int count = 0;

        @Override
        public boolean hasNext() {
            return count < values.length;
        }

        @Override
        public Double next() {
            try {
                return values[count++];
            }
            catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException("No element at position " + count);
            }
        }

    }
    
    private final class SuperspaceIterator implements Iterator<SubspaceIterator> {

        private final Indexes superindexes;

        /** Those indexes this should iterate over */
        private final List<Integer> subdimensionIndexes;
        
        /** 
         * The sizes of the space we'll return values of, one value for each dimension of this tensor,
         * which may be equal to or smaller than the sizes of this tensor 
         */
        private final int[] iterateDimensionSizes;

        private int count = 0;
        
        private SuperspaceIterator(Set<String> superdimensionNames, int[] iterateDimensionSizes) {
            this.iterateDimensionSizes = iterateDimensionSizes;
            
            List<Integer> superdimensionIndexes = new ArrayList<>(superdimensionNames.size()); // for outer iterator
            subdimensionIndexes = new ArrayList<>(superdimensionNames.size()); // for inner iterator (max length)
            for (int i = type.dimensions().size() - 1; i >= 0; i-- ) { // iterate inner dimensions first
                if (superdimensionNames.contains(type.dimensions().get(i).name()))
                    superdimensionIndexes.add(i);
                else
                    subdimensionIndexes.add(i);
            }
            
            superindexes = Indexes.of(IndexedTensor.this.dimensionSizes, iterateDimensionSizes, superdimensionIndexes);
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
            return new SubspaceIterator(subdimensionIndexes, superindexes.indexesCopy(), iterateDimensionSizes);
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
        private final int[] address;
        private final int[] iterateDimensionSizes;

        private Indexes indexes;
        private int count = 0;
        
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
        private SubspaceIterator(List<Integer> iterateDimensions, int[] address, int[] iterateDimensionSizes) {
            this.iterateDimensions = iterateDimensions;
            this.address = address;
            this.iterateDimensionSizes = iterateDimensionSizes;
            this.indexes = Indexes.of(IndexedTensor.this.dimensionSizes, iterateDimensionSizes, iterateDimensions, address);
            reusedCell = new LazyCell(indexes, Double.NaN);
        }
        
        /** Returns the total number of cells in this subspace */
        public int size() { 
            return indexes.size();
        }
        
        /** Returns the address of the cell this currently points to (which may be an invalid position) */
        public TensorAddress address() { return indexes.toAddress(); }
        
        /** Rewind this iterator to the first element */
        public void reset() { 
            this.count = 0;
            this.indexes = Indexes.of(IndexedTensor.this.dimensionSizes, iterateDimensionSizes, iterateDimensions, address); 
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
        int getDirectIndex() { return indexes.toIterationValueIndex(); }

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

        private final int[] sourceDimensionSizes;

        private final int[] iterationDimensionSizes;

        protected final int[] indexes;
        
        public static Indexes of(int[] dimensionSizes) {
            return of(dimensionSizes, dimensionSizes);
        }

        private static Indexes of(int[] sourceDimensionSizes, int[] iterateDimensionSizes) {
            return of(sourceDimensionSizes, iterateDimensionSizes, completeIterationOrder(iterateDimensionSizes.length));
        }

        private static Indexes of(int[] sourceDimensionSizes, int[] iterateDimensionSizes, int size) {
            return of(sourceDimensionSizes, iterateDimensionSizes, completeIterationOrder(iterateDimensionSizes.length), size);
        }

        private static Indexes of(int[] sourceDimensionSizes, int[] iterateDimensionSizes, List<Integer> iterateDimensions) {
            return of(sourceDimensionSizes, iterateDimensionSizes, iterateDimensions, computeSize(iterateDimensionSizes, iterateDimensions));
        }

        private static Indexes of(int[] sourceDimensionSizes, int[] iterateDimensionSizes, List<Integer> iterateDimensions, int size) {
            return of(sourceDimensionSizes, iterateDimensionSizes, iterateDimensions, new int[iterateDimensionSizes.length], size);
        }

        private static Indexes of(int[] sourceDimensionSizes, int[] iterateDimensionSizes, List<Integer> iterateDimensions, int[] initialIndexes) {
            return of(sourceDimensionSizes, iterateDimensionSizes, iterateDimensions, initialIndexes, computeSize(iterateDimensionSizes, iterateDimensions));
        }

        private static Indexes of(int[] sourceDimensionSizes, int[] iterateDimensionSizes, List<Integer> iterateDimensions, int[] initialIndexes, int size) {
            if (size == 0) {
                return new EmptyIndexes(sourceDimensionSizes, iterateDimensionSizes, initialIndexes); // we're told explicitly there are truly no values available
            }
            else if (size == 1) {
                return new SingleValueIndexes(sourceDimensionSizes, iterateDimensionSizes, initialIndexes); // with no (iterating) dimensions, we still return one value, not zero
            }
            else if (iterateDimensions.size() == 1) {
                if (Arrays.equals(sourceDimensionSizes, iterateDimensionSizes))
                    return new EqualSizeSingleDimensionIndexes(sourceDimensionSizes, iterateDimensions.get(0), initialIndexes, size);
                else
                    return new SingleDimensionIndexes(sourceDimensionSizes, iterateDimensionSizes, iterateDimensions.get(0), initialIndexes, size); // optimization
            }
            else {
                if (Arrays.equals(sourceDimensionSizes, iterateDimensionSizes))
                    return new EqualSizeMultiDimensionIndexes(sourceDimensionSizes, iterateDimensions, initialIndexes, size);
                else
                    return new MultiDimensionIndexes(sourceDimensionSizes, iterateDimensionSizes, iterateDimensions, initialIndexes, size);
            }
        }
        
        private static List<Integer> completeIterationOrder(int length) {
            List<Integer> iterationDimensions = new ArrayList<>(length);
            for (int i = 0; i < length; i++)
                iterationDimensions.add(length - 1 - i);
            return iterationDimensions;
        }
        
        private Indexes(int[] sourceDimensionSizes, int[] iterationDimensionSizes, int[] indexes) {
            this.sourceDimensionSizes = sourceDimensionSizes;
            this.iterationDimensionSizes = iterationDimensionSizes;
            this.indexes = indexes;
        }

        private static int computeSize(int[] dimensionSizes, List<Integer> iterateDimensions) {
            int size = 1;
            for (int iterateDimension : iterateDimensions)
                size *= dimensionSizes[iterateDimension];
            return size;
        }

        /** Returns the address of the current position of these indexes */
        private TensorAddress toAddress() {
            return TensorAddress.of(indexes);
        }

        public int[] indexesCopy() {
            return Arrays.copyOf(indexes, indexes.length);
        }

        /** Returns a copy of the indexes of this which must not be modified */
        public int[] indexesForReading() { return indexes; }
        
        int toSourceValueIndex() { 
            return IndexedTensor.toValueIndex(indexes, sourceDimensionSizes); 
        }

        int toIterationValueIndex() { return IndexedTensor.toValueIndex(indexes, iterationDimensionSizes); }

        /** Returns the dimension sizes of this. Do not modify the return value */
        int[] dimensionSizes() { return iterationDimensionSizes; }

        /** Returns an immutable list containing a copy of the indexes in this */
        public List<Integer> toList() {
            ImmutableList.Builder<Integer> builder = new ImmutableList.Builder<>();
            for (int index : indexes)
                builder.add(index);
            return builder.build();
        }

        @Override
        public String toString() {
            return "indexes " + Arrays.toString(indexes);
        }
        
        public abstract int size();
        
        public abstract void next();

    }

    private final static class EmptyIndexes extends Indexes {

        private EmptyIndexes(int[] sourceDimensionSizes, int[] iterateDimensionSizes, int[] indexes) {
            super(sourceDimensionSizes, iterateDimensionSizes, indexes);
        }

        @Override
        public int size() { return 0; }

        @Override
        public void next() {}

    }

    private final static class SingleValueIndexes extends Indexes {

        private SingleValueIndexes(int[] sourceDimensionSizes, int[] iterateDimensionSizes, int[] indexes) {
            super(sourceDimensionSizes, iterateDimensionSizes, indexes);
        }

        @Override
        public int size() { return 1; }

        @Override
        public void next() {}

    }
    
    private static class MultiDimensionIndexes extends Indexes {

        private final int size;

        private final List<Integer> iterateDimensions;
        
        private MultiDimensionIndexes(int[] sourceDimensionSizes, int[] iterateDimensionSizes, List<Integer> iterateDimensions, int[] initialIndexes, int size) {
            super(sourceDimensionSizes, iterateDimensionSizes, initialIndexes);
            this.iterateDimensions = iterateDimensions;
            this.size = size;
            
            // Initialize to the (virtual) position before the first cell
            indexes[iterateDimensions.get(0)]--;
        }

        /** Returns the number of values this will iterate over - i.e the product if the iterating dimension sizes */
        @Override
        public int size() {
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
            while ( indexes[iterateDimensions.get(iterateDimensionsIndex)] + 1 == dimensionSizes()[iterateDimensions.get(iterateDimensionsIndex)]) {
                indexes[iterateDimensions.get(iterateDimensionsIndex)] = 0; // carry over
                iterateDimensionsIndex++;
            }
            indexes[iterateDimensions.get(iterateDimensionsIndex)]++;
        }

    }
    
    /** In this case we can reuse the source index computation for the iteration index */
    private final static class EqualSizeMultiDimensionIndexes extends MultiDimensionIndexes {

        private int lastComputedSourceValueIndex = -1;
        
        private EqualSizeMultiDimensionIndexes(int[] dimensionSizes, List<Integer> iterateDimensions, int[] initialIndexes, int size) {
            super(dimensionSizes, dimensionSizes, iterateDimensions, initialIndexes, size);
        }

        int toSourceValueIndex() {
            return lastComputedSourceValueIndex = super.toSourceValueIndex();
        }

        // NOTE: We assume the source index always gets computed first. Otherwise using this will produce a runtime exception
        int toIterationValueIndex() { return lastComputedSourceValueIndex; }

    }

    /** In this case we can keep track of indexes using a step instead of using the more elaborate computation */
    private final static class SingleDimensionIndexes extends Indexes {

        private final int size;

        private final int iterateDimension;
        
        /** Maintain this directly as an optimization for 1-d iteration */
        private int currentSourceValueIndex, currentIterationValueIndex;

        /** The iteration step in the value index space */
        private final int sourceStep, iterationStep;

        private SingleDimensionIndexes(int[] sourceDimensionSizes, int[] iterateDimensionSizes,
                                       int iterateDimension, int[] initialIndexes, int size) {
            super(sourceDimensionSizes, iterateDimensionSizes, initialIndexes);
            this.iterateDimension = iterateDimension;
            this.size = size;
            this.sourceStep = productOfDimensionsAfter(iterateDimension, sourceDimensionSizes);
            this.iterationStep = productOfDimensionsAfter(iterateDimension, iterateDimensionSizes);

            // Initialize to the (virtual) position before the first cell
            indexes[iterateDimension]--;
            currentSourceValueIndex = IndexedTensor.toValueIndex(indexes, sourceDimensionSizes);
            currentIterationValueIndex = IndexedTensor.toValueIndex(indexes, iterateDimensionSizes);
        }
        
        /** Returns the number of values this will iterate over - i.e the product if the iterating dimension sizes */
        @Override
        public int size() {
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
        int toSourceValueIndex() { return currentSourceValueIndex; }

        @Override
        int toIterationValueIndex() { return currentIterationValueIndex; }

    }

    /** In this case we only need to keep track of one index */
    private final static class EqualSizeSingleDimensionIndexes extends Indexes {

        private final int size;

        private final int iterateDimension;

        /** Maintain this directly as an optimization for 1-d iteration */
        private int currentValueIndex;

        /** The iteration step in the value index space */
        private final int step;

        private EqualSizeSingleDimensionIndexes(int[] dimensionSizes,
                                                int iterateDimension, int[] initialIndexes, int size) {
            super(dimensionSizes, dimensionSizes, initialIndexes);
            this.iterateDimension = iterateDimension;
            this.size = size;
            this.step = productOfDimensionsAfter(iterateDimension, dimensionSizes);

            // Initialize to the (virtual) position before the first cell
            indexes[iterateDimension]--;
            currentValueIndex = IndexedTensor.toValueIndex(indexes, dimensionSizes);
        }

        /** Returns the number of values this will iterate over - i.e the product if the iterating dimension sizes */
        @Override
        public int size() {
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
        int toSourceValueIndex() { return currentValueIndex; }

        @Override
        int toIterationValueIndex() { return currentValueIndex; }

    }

}
