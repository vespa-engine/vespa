// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        // optimize for fast lookup within bounds
        try {
            return values[toValueIndex(address, dimensionSizes)];
        }
        catch (IndexOutOfBoundsException e) {
            return Double.NaN;
        }
    }
    
    private static int toValueIndex(int[] indexes, int[] dimensionSizes) {
        if (indexes.length == 0) return 0;

        int valueIndex = 0;
        for (int i = 0; i < indexes.length; i++)
            valueIndex += productOfDimensionsAfter(i, dimensionSizes) * indexes[i];
        return valueIndex;
    }
    
    private static int toValueIndex(TensorAddress address, int[] dimensionSizes) {
        if (address.labels().isEmpty()) return 0;

        int valueIndex = 0;
        for (int i = 0; i < address.labels().size(); i++)
            valueIndex += productOfDimensionsAfter(i, dimensionSizes) * Integer.parseInt(address.labels().get(i));
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
     * Returns the lenght of this in the nth dimension
     *
     * @throws IndexOutOfBoundsException if the index is larger than the number of dimensions in this tensor minus one
     */
    public int length(int dimension) {
        return dimensionSizes[dimension];
    }

    @Override
    // TODO: Replace this with iterator
    public Map<TensorAddress, Double> cells() {
        if (dimensionSizes.length == 0) 
            return values.length == 0 ? Collections.emptyMap() : Collections.singletonMap(TensorAddress.empty, values[0]);
        
        ImmutableMap.Builder<TensorAddress, Double> builder = new ImmutableMap.Builder<>();
        int[] tensorIndexes = new int[dimensionSizes.length];
        for (int i = 0; i < values.length; i++) {
            builder.put(new TensorAddress(tensorIndexes), values[i]);
            if (i < values.length -1)
                next(tensorIndexes.length - 1, tensorIndexes, dimensionSizes);
        }
        return builder.build();
    }
    
    private void next(int dimension, int[] tensorIndexes, int[] dimensionSizes) {
        if (tensorIndexes[dimension] + 1 == dimensionSizes[dimension]) {
            tensorIndexes[dimension] = 0;
            next(dimension - 1, tensorIndexes, dimensionSizes);
        }
        else {
            tensorIndexes[dimension]++;
        }
    }

    @Override
    public int hashCode() { return Arrays.hashCode(values); }

    @Override
    public String toString() { return Tensor.toStandardString(this); }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof Tensor)) return false;
        return Tensor.equals(this, (Tensor)o);
    }

    public abstract static class Builder implements Tensor.Builder {
        
        final TensorType type;
        
        private Builder(TensorType type) {
            this.type = type;
        }

        // TODO: Let other tensor builders be created by this method as well (and update system tests)
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
                if (size.isPresent() && size.get() != dimensionSizes[i])
                    throw new IllegalArgumentException("Size of " + type.dimensions() + " must be " + size.get() + 
                                                       ", not " + dimensionSizes[i]);
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

        // TODO: Can this be pushed up to Tensor.Builder?
        public class IndexedCellBuilder implements Tensor.Builder.CellBuilder {

            private final TensorAddress.Builder addressBuilder = new TensorAddress.Builder(IndexedTensor.Builder.this.type);

            @Override
            public IndexedCellBuilder label(String dimension, String label) {
                addressBuilder.add(dimension, label);
                return this;
            }

            @Override
            public IndexedCellBuilder label(String dimension, int label) {
                return label(dimension, String.valueOf(label));
            }

            @Override
            public Builder value(double cellValue) {
                return (Builder)Builder.this.cell(addressBuilder.build(), cellValue);
            }

        }

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
            return new IndexedCellBuilder();
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
        public IndexedCellBuilder cell() {
            return new IndexedCellBuilder();
        }

        @Override
        public Builder cell(TensorAddress address, double value) {
            int[] indexes = new int[address.labels().size()];
            for (int i = 0; i < address.labels().size(); i++) {
                try {
                    indexes[i] = Integer.parseInt(address.labels().get(i));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Labels in an indexed tensor must be integers, not '" +
                                                       address.labels().get(i) + "'");
                }
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

}
