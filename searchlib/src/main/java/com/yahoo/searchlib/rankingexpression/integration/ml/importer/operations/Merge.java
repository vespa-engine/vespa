// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;

public class Merge extends IntermediateOperation {

    public Merge(String name, List<IntermediateOperation> inputs) {
        super(name, inputs);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        for (IntermediateOperation operation : inputs) {
            if (operation.type().isPresent()) {
                return operation.type().get();
            }
        }
        return null;
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        for (IntermediateOperation operation : inputs) {
            if (operation.function().isPresent()) {
                return operation.function().get();
            }
        }
        return null;
    }

}
