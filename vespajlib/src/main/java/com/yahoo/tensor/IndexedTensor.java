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
 * An indexed (dense) tensor backed by array lists.
 *
 * @author bratseth
 */
@Beta
public class IndexedTensor implements Tensor {

    private final TensorType type;

    private final IndexedDimension firstDimension;
    
    private IndexedTensor(TensorType type, IndexedDimension firstDimension) {
        this.type = type;
        this.firstDimension = firstDimension;
    }

    static IndexedTensor from(TensorType type, String tensorString) {
        tensorString = tensorString.trim();
        try {
            if (tensorString.startsWith("{"))
                return fromCellString(type, tensorString);
            else
                return fromNumber(Double.parseDouble(tensorString));
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Excepted a number or a string starting by { or tensor(, got '" + 
                                               tensorString + "'");
        }
    }

    private static IndexedTensor fromCellString(TensorType type, String s) {
        IndexedTensor.Builder builder = new IndexedTensor.Builder(type);
        s = s.trim().substring(1).trim();
        while (s.length() > 1) {
            int keyEnd = s.indexOf('}');
            int[] cellIndexes = indexesFrom(type, s.substring(0, keyEnd+1));
            s = s.substring(keyEnd + 1).trim();
            if ( ! s.startsWith(":"))
                throw new IllegalArgumentException("Expecting a ':' after " + s + ", got '" + s + "'");
            int valueEnd = s.indexOf(',');
            if (valueEnd < 0) { // last value
                valueEnd = s.indexOf("}");
                if (valueEnd < 0)
                    throw new IllegalArgumentException("A tensor string must end by '}'");
            }
            Double value = asDouble(cellIndexes, s.substring(1, valueEnd).trim());
            
            builder.set(value, cellIndexes);
            s = s.substring(valueEnd+1).trim();
        }
        return builder.build();
    }

    /** Creates a tenor address from a string on the form {dimension1:label1,dimension2:label2,...} */
    private static int[] indexesFrom(TensorType type, String mapAddressString) {
        mapAddressString = mapAddressString.trim();
        if ( ! (mapAddressString.startsWith("{") && mapAddressString.endsWith("}")))
            throw new IllegalArgumentException("Expecting a tensor address to be enclosed in {}, got '" + mapAddressString + "'");

        String addressBody = mapAddressString.substring(1, mapAddressString.length() - 1).trim();
        if (addressBody.isEmpty()) return new int[0];

        int[] indexes = new int[type.dimensions().size()];
        for (String elementString : addressBody.split(",")) {
            String[] pair = elementString.split(":");
            if (pair.length != 2)
                throw new IllegalArgumentException("Expecting argument elements to be on the form dimension:label, " +
                                                   "got '" + elementString + "'");
            String dimension = pair[0].trim();
            Optional<Integer> dimensionIndex = type.indexOfDimension(dimension);
            if ( ! dimensionIndex.isPresent())
                throw new IllegalArgumentException("Dimension '" + dimension + "' is not present in " + type);
            indexes[dimensionIndex.get()] = asInteger(pair[1].trim(), dimension);
        }
        return indexes;
    }
    
    private static int asInteger(String value, String dimension) {
        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected an integer value for dimension '" + dimension + "', " +
                                               "but got '" + value + "'");
        }
        
    }

    private static IndexedTensor fromNumber(double number) {
        return new IndexedTensor(TensorType.empty, new IndexedDimension(number));
    }
    
    private static Double asDouble(int[] indexes, String s) {
        try {
            return Double.valueOf(s);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("At " + Arrays.toString(indexes) + 
                                               ": Expected a floating point number, got '" + s + "'");
        }
    }

    @Override
    public TensorType type() { return type; }

    @Override
    public Map<TensorAddress, Double> cells() {
        ImmutableMap.Builder<TensorAddress, Double> builder = new ImmutableMap.Builder<>();
        populateRecursively(builder, firstDimension, new TensorAddress.Builder(type), new ArrayList<>(type.dimensions()));
        return builder.build();
    }

    private void populateRecursively(ImmutableMap.Builder<TensorAddress, Double> valueBuilder, IndexedDimension dimensionValues, 
                                     TensorAddress.Builder partialAddress, List<TensorType.Dimension> remainingDimensions) {
        if (remainingDimensions.size() == 0) {
            return;
        }
        else if (remainingDimensions.size() == 1) {
            for (int i = 0; i < dimensionValues.values().size(); i++)
                valueBuilder.put(partialAddress.copy().add(remainingDimensions.get(0).name(), String.valueOf(i)).build(), 
                                 (Double)dimensionValues.values().get(i));
        }
        else {
            List<TensorType.Dimension> nestedRemainingDimensions = new ArrayList<>(remainingDimensions);
            TensorType.Dimension currentDimension = nestedRemainingDimensions.remove(0);
            for (int i = 0; i < dimensionValues.values().size(); i++) {
                populateRecursively(valueBuilder, (IndexedDimension)dimensionValues.values().get(i), 
                                    partialAddress.copy().add(currentDimension.name(), String.valueOf(i)),
                                    nestedRemainingDimensions);
            }
        }
    }

    @Override
    public double get(TensorAddress address) { throw new IllegalArgumentException("Remove?"); } // TODO: Keep this method?

    @Override
    public int hashCode() { return firstDimension.hashCode(); }

    @Override
    public String toString() { return Tensor.toStandardString(this); }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof Tensor)) return false;
        return Tensor.equals(this, (Tensor)o);
    }

    /** An indexed dimension containing another indexed dimension */
    private static class IndexedDimension {

        private final ImmutableList<Object> values;

        public IndexedDimension() { values = ImmutableList.of(); }

        public IndexedDimension(Double value) { values = ImmutableList.of(value); }

        public IndexedDimension(List<Object> values) {
            this.values = ImmutableList.copyOf(values);
        }
        
        public ImmutableList<Object> values() { return values; }

    }

    public static class Builder {
    
        private final TensorType type;
        
        /** List of List or Double */
        private List<Object> firstDimension = null;
    
        public Builder(TensorType type) {
            this.type = type;
        }
    
        public IndexedTensor build() {
            if (firstDimension == null) // empty
                return new IndexedTensor(type, new IndexedDimension());
            if (type.dimensions().isEmpty()) // single number
                return new IndexedTensor(type, 
                                         new IndexedDimension((Double)firstDimension.get(0)));

            List<TensorType.Dimension> dimensions = new ArrayList<>(type.dimensions());
            IndexedDimension firstDimension = buildRecursively(dimensions, this.firstDimension);
            return new IndexedTensor(type, firstDimension);
        }
        
        @SuppressWarnings("unchecked")
        private IndexedDimension buildRecursively(List<TensorType.Dimension> remainingDimensions, 
                                                  List<Object> currentDimensionValues) {
            if (remainingDimensions.size() == 1) { // last dimension
                for (int i = 0; i < currentDimensionValues.size(); i++)
                    if (currentDimensionValues.get(i) == null)
                        throw new IllegalArgumentException("Missing a value at index " + i + " in dimension " + 
                                                           remainingDimensions.get(0) + " for tensor of type " + type);
                return new IndexedDimension(currentDimensionValues);
            }
            else {
                List<TensorType.Dimension> nestedRemainingDimensions = new ArrayList<>(remainingDimensions);                
                TensorType.Dimension currentDimension = nestedRemainingDimensions.remove(0);
                ImmutableList.Builder<Object> values = new ImmutableList.Builder<>();
                for (int i = 0; i < currentDimensionValues.size(); i++) {
                    if (currentDimensionValues.get(i) == null)
                        throw new IllegalArgumentException("Missing a value at index " + i + " in dimension " +
                                                           currentDimension + " for tensor of type " + type);
                    values.add(buildRecursively(nestedRemainingDimensions, (List<Object>)currentDimensionValues.get(i)));
                }
                return new IndexedDimension(values.build());
            }
        }

        /**
         * Set a value. The number of indexes must be the same as the dimensions in the type of this.
         * Values can be written in any order but all values needed to make this dense must be provided
         * before building this.
         * 
         * @return this for chaining
         */
        @SuppressWarnings("unchecked")
        public Builder set(double value, int ... indexes) {
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
                }
                else {
                    if (currentValues.get(indexes[dimensionIndex]) == null)
                        currentValues.set(indexes[dimensionIndex], new ArrayList<>());
                    currentValues = (List<Object>)currentValues.get(indexes[dimensionIndex]);
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
