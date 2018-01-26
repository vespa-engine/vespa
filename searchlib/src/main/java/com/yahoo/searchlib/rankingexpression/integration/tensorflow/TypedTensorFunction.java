// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;

/**
 * A tensor function returning a specific tensor type
 *
 * @author bratseth
 */
final class TypedTensorFunction {

    private final TensorType type;
    private final TensorFunction function;

    public TypedTensorFunction(TensorType type, TensorFunction function) {
        this.type = type;
        this.function = function;
    }

    public TensorType type() {
        return type;
    }

    public TensorFunction function() {
        return function;
    }

}
