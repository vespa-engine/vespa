// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.framework.NodeDef;

import java.util.List;

public class Merge extends TensorFlowOperation {

    public Merge(String modelName, NodeDef node, List<TensorFlowOperation> inputs, int port) {
        super(modelName, node, inputs, port);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        for (TensorFlowOperation operation : inputs) {
            if (operation.type().isPresent()) {
                return operation.type().get();
            }
        }
        return null;
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        for (TensorFlowOperation operation : inputs) {
            if (operation.function().isPresent()) {
                return operation.function().get();
            }
        }
        return null;
    }

}
