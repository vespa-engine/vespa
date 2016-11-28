// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Takes the sum of two tensors, see {@link Tensor#add}
 *
 * @author bratseth
 */
class TensorSum {

    private final Set<String> dimensions;
    private final Map<TensorAddress, Double> cells = new HashMap<>();

    public TensorSum(Tensor a, Tensor b) {
        dimensions = TensorOperations.combineDimensions(a, b);
        cells.putAll(a.cells());
        for (Map.Entry<TensorAddress, Double> bCell : b.cells().entrySet()) {
            cells.put(bCell.getKey(), a.cells().getOrDefault(bCell.getKey(), 0d) + bCell.getValue());
        }
    }

    /** Returns the result of taking this sum */
    public Tensor result() { return new MapTensor(dimensions, cells); }

}
