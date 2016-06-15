// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Computes the tensor with some function to all the cells of the input tensor
 *
 * @author bratseth
 */
class TensorFunction {

    private final Set<String> dimensions;
    private final ImmutableMap.Builder<TensorAddress, Double> cells = new ImmutableMap.Builder<>();

    public TensorFunction(Tensor t, UnaryOperator<Double> f) {
        dimensions = t.dimensions();
        for (Map.Entry<TensorAddress, Double> cell : t.cells().entrySet()) {
            cells.put(cell.getKey(), f.apply(cell.getValue()));
        }
    }

    /** Returns the result of taking this sum */
    public MapTensor result() {
        return new MapTensor(dimensions, cells.build());
    }

}
