// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.framework.NodeDef;

import java.util.List;

public class Identity extends TensorFlowOperation {

    private final String modelName;

    public Identity(String modelName, NodeDef node, List<TensorFlowOperation> inputs, int port) {
        super(node, inputs, port);
        this.modelName = modelName;
    }

    /** Constant names are prefixed by "modelName_" to avoid name conflicts between models */
    @Override
    public String vespaName() {
        return modelName + "_" + super.vespaName();
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
