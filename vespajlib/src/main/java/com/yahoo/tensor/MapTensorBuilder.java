// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;

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
    private final ImmutableMap.Builder<TensorAddress, Double> cells = new ImmutableMap.Builder<>();

    public MapTensorBuilder(TensorType type) {
        this.type = type;
    }

    public CellBuilder cell() {
        return new CellBuilder(type);
    }

    public Tensor build() {
        return new MapTensor(type, cells.build());
    }

    public class CellBuilder {

        private final TensorAddress.Builder addressBuilder;

        private CellBuilder(TensorType type) {
            addressBuilder = new TensorAddress.Builder(type);
        }
        
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
