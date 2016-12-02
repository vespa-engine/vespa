// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.annotations.Beta;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Builder class for a MapTensor.
 *
 * The set of dimensions of the resulting tensor is the union of
 * the dimensions specified explicitly and the ones specified in the
 * tensor cell addresses.
 *
 * @author geirst
 */
// TODO: Move below MapTensor
@Beta
public class MapTensorBuilder {

    private final TensorType type;
    private final Map<TensorAddress, Double> cells = new HashMap<>();

    // TODO: Remove this, and the TODO below
    public MapTensorBuilder() {
        this.type = null;
    }
    
    public MapTensorBuilder(TensorType type) {
        this.type = type;
    }

    public CellBuilder cell() {
        return new CellBuilder();
    }

    public Tensor build() {
        return new MapTensor(type != null ? type : typeFromCells(), cells);
    }

    // TODO: Remove this, and the TODO above
    private TensorType typeFromCells() { 
        if (cells.size() == 0) return TensorType.empty;
        
        TensorType.Builder b = new TensorType.Builder();
        for (String dimension : cells.keySet().iterator().next().dimensions())
            b.mapped(dimension);
        return b.build();
    }

    public class CellBuilder {

        private final TensorAddress.Builder addressBuilder = new TensorAddress.Builder();

        public CellBuilder label(String dimension, String label) {
            addressBuilder.add(dimension, label);
            return this;
        }

        public MapTensorBuilder value(double cellValue) {
            cells.put(addressBuilder.build(), cellValue);
            return MapTensorBuilder.this;
        }

    }

}
