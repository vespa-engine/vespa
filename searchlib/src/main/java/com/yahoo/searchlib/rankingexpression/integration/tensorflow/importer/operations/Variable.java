// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.framework.NodeDef;

import java.util.List;

public class Variable extends TensorFlowOperation {

    public Variable(NodeDef node, List<TensorFlowOperation> inputs, int port) {
        super(node, inputs, port);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        return OrderedTensorType.fromTensorFlowType(node, vespaName() + "_");
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        return null;  // will be added by function() since this is constant.
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        for (TensorType.Dimension dimension : type.type().dimensions()) {
            renamer.addDimension(dimension.name());
        }
    }

    @Override
    public boolean isConstant() {
        return true;
    }

}
