// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Set;

/**
 * Computes a <i>match product</i>, see {@link Tensor#match}
 *
 * @author bratseth
 */
class MatchProduct {

    private final Set<String> dimensions;
    private final ImmutableMap.Builder<TensorAddress, Double> cells = new ImmutableMap.Builder<>();

    public MatchProduct(Tensor a, Tensor b) {
        this.dimensions = TensorOperations.combineDimensions(a, b);
        for (Map.Entry<TensorAddress, Double> aCell : a.cells().entrySet()) {
            Double sameValueInB = b.cells().get(aCell.getKey());
            if (sameValueInB != null)
                cells.put(aCell.getKey(), aCell.getValue() * sameValueInB);
        }
    }

    /** Returns the result of taking this product */
    public MapTensor result() {
        return new MapTensor(dimensions, cells.build());
    }

}
