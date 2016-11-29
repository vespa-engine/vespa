// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Takes the max of each cell of two tensors, see {@link Tensor#max}
 *
 * @author bratseth
 */
class TensorMax {

    private final Set<String> dimensions;
    private final Map<TensorAddress, Double> cells = new HashMap<>();

    public TensorMax(Tensor a, Tensor b) {
        dimensions = TensorOperations.combineDimensions(a, b);
        cells.putAll(a.cells());
        for (Map.Entry<TensorAddress, Double> bCell : b.cells().entrySet()) {
            Double aValue = a.cells().get(bCell.getKey());
            if (aValue == null)
                cells.put(bCell.getKey(), bCell.getValue());
            else
                cells.put(bCell.getKey(), Math.max(aValue, bCell.getValue()));
        }
    }

    /** Returns the result of taking this sum */
    public Tensor result() {
        return new MapTensor(dimensions, cells);
    }

}
