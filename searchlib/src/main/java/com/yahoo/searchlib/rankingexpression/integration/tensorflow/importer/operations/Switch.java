// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.framework.NodeDef;

import java.util.List;
import java.util.Optional;

public class Switch extends TensorFlowOperation {

    public Switch(NodeDef node, List<TensorFlowOperation> inputs, int port) {
        super(node, inputs, port);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(2)) {
            return null;
        }
        Optional<OrderedTensorType> predicate = inputs.get(1).type();
        if (predicate.get().type().rank() != 0) {
            throw new IllegalArgumentException("Switch in " + node.getName() + ": " +
                    "predicate must be a scalar");
        }
        return inputs.get(0).type().orElse(null);
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        TensorFlowOperation predicateOperation = inputs().get(1);
        if (!predicateOperation.getConstantValue().isPresent()) {
            throw new IllegalArgumentException("Switch in " + node.getName() + ": " +
                    "predicate must be a constant");
        }
        if (port < 0 || port > 1) {
            throw new IllegalArgumentException("Switch in " + node.getName() + ": " +
                    "choice should be boolean");
        }

        double predicate = predicateOperation.getConstantValue().get().asDouble();
        return predicate == port ? inputs().get(0).function().get() : null;
    }

}


