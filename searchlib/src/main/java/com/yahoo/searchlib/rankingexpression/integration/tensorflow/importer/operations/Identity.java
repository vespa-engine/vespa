// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.framework.NodeDef;

import java.util.List;

public class Identity extends TensorFlowOperation {

    public Identity(NodeDef node, List<TensorFlowOperation> inputs, int port) {
        super(node, inputs, port);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(1))
            return null;
        return inputs.get(0).type().orElse(null);
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        if (!allInputFunctionsPresent(1))
            return null;
        return inputs.get(0).function().orElse(null);
    }

}
