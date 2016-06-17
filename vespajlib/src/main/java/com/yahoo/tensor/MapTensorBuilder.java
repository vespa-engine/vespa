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
@Beta
public class MapTensorBuilder {

    private final Set<String> dimensions = new HashSet<>();
    private final Map<TensorAddress, Double> cells = new HashMap<>();

    public class CellBuilder {

        private final TensorAddress.Builder addressBuilder = new TensorAddress.Builder();

        public CellBuilder label(String dimension, String label) {
            dimensions.add(dimension);
            addressBuilder.add(dimension, label);
            return this;
        }
        public MapTensorBuilder value(double cellValue) {
            cells.put(addressBuilder.build(), cellValue);
            return MapTensorBuilder.this;
        }
    }

    public MapTensorBuilder() {
    }

    public MapTensorBuilder dimension(String dimension) {
        dimensions.add(dimension);
        return this;
    }

    public CellBuilder cell() {
        return new CellBuilder();
    }

    public Tensor build() {
        return new MapTensor(dimensions, cells);
    }
}
