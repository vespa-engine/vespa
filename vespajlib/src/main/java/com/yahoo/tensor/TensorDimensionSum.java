// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Returns a tensor with the given dimension removed and the cell values in that dimension summed
 *
 * @author bratseth
 */
class TensorDimensionSum {

    private final Set<String> dimensions;
    private final Map<TensorAddress, Double> cells = new HashMap<>();

    public TensorDimensionSum(String dimension, Tensor t) {
        dimensions = new HashSet<>(t.dimensions());
        dimensions.remove(dimension);

        for (Map.Entry<TensorAddress, Double> cell : t.cells().entrySet()) {
            TensorAddress reducedAddress = removeDimension(dimension, cell.getKey());
            Double newValue = cell.getValue();
            Double existingValue = cells.get(reducedAddress);
            if (existingValue != null)
                newValue += existingValue;
            cells.put(reducedAddress, newValue);
        }
    }

    private TensorAddress removeDimension(String dimension, TensorAddress address) {
        List<TensorAddress.Element> reducedAddress = new ArrayList<>();
        for (TensorAddress.Element element : address.elements())
            if ( ! element.dimension().equals(dimension))
                reducedAddress.add(element);
        return TensorAddress.fromSorted(reducedAddress);
    }

    /** Returns the result of taking this sum */
    public MapTensor result() { return new MapTensor(dimensions, cells); }

}
