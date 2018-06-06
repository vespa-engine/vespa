// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations;

import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.framework.NodeDef;

import java.util.List;

public class Shape extends TensorFlowOperation {

    public Shape(String modelName, NodeDef node, List<TensorFlowOperation> inputs, int port) {
        super(modelName, node, inputs, port);
        createConstantValue();
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(1)) {
            return null;
        }
        OrderedTensorType inputType = inputs.get(0).type().get();
        return new OrderedTensorType.Builder(node)
                .add(TensorType.Dimension.indexed(vespaName(), inputType.dimensions().size()))
                .build();
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        return null; // will be added by function() since this is constant.
    }

    @Override
    public boolean isConstant() {
        return true;
    }

    private void createConstantValue() {
        if (!allInputTypesPresent(1)) {
            return;
        }
        OrderedTensorType inputType = inputs.get(0).type().get();
        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder) Tensor.Builder.of(type().get().type());
        List<TensorType.Dimension> inputDimensions = inputType.dimensions();
        for (int i = 0; i < inputDimensions.size(); i++) {
            builder.cellByDirectIndex(i, inputDimensions.get(i).size().orElse(-1L));
        }
        this.setConstantValue(new TensorValue(builder.build()));
    }

}
