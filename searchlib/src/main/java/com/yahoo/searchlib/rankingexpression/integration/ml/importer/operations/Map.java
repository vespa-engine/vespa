// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;

public class Map extends IntermediateOperation {

    private final DoubleUnaryOperator operator;

    public Map(String name, List<IntermediateOperation> inputs, DoubleUnaryOperator operator) {
        super(name, inputs);
        this.operator = operator;
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(1)) {
            return null;
        }
        return inputs.get(0).type().get();
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        if (!allInputFunctionsPresent(1)) {
            return null;
        }
        Optional<TensorFunction> input = inputs.get(0).function();
        return new com.yahoo.tensor.functions.Map(input.get(), operator);
    }

}
