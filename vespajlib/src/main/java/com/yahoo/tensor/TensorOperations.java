// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * Functions on tensors
 *
 * @author bratseth
 */
class TensorOperations {

    /**
     * A utility method which returns an ummutable set of the union of the dimensions
     * of the two argument tensors.
     *
     * @return the combined dimensions as an unmodifiable set
     */
    static Set<String> combineDimensions(Tensor a, Tensor b) {
        ImmutableSet.Builder<String> setBuilder = new ImmutableSet.Builder<>();
        setBuilder.addAll(a.dimensions());
        setBuilder.addAll(b.dimensions());
        return setBuilder.build();
    }

}
