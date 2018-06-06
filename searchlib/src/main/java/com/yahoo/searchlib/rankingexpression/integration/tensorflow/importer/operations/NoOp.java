// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.framework.NodeDef;

import java.util.Collections;
import java.util.List;

public class NoOp extends TensorFlowOperation {

    public NoOp(String modelName, NodeDef node, List<TensorFlowOperation> inputs, int port) {
        super(modelName, node, Collections.emptyList(), port);  // don't propagate inputs
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        return null;
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        return null;
    }

    @Override
    public boolean isConstant() {
        return true;
    }

}
