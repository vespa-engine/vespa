// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.framework.NodeDef;

import java.util.List;
import java.util.Optional;

public class Matmul extends TensorFlowOperation {

    public Matmul(NodeDef node, List<TensorFlowOperation> inputs, int port) {
        super(node, inputs, port);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(2)) {
            return null;
        }
        OrderedTensorType.Builder typeBuilder = new OrderedTensorType.Builder(node);
        typeBuilder.add(inputs.get(0).type().get().dimensions().get(0));
        typeBuilder.add(inputs.get(1).type().get().dimensions().get(1));
        return typeBuilder.build();
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        if (!allInputTypesPresent(2)) {
            return null;
        }
        OrderedTensorType aType = inputs.get(0).type().get();
        OrderedTensorType bType = inputs.get(1).type().get();
        if (aType.type().rank() < 2 || bType.type().rank() < 2)
            throw new IllegalArgumentException("Tensors in matmul must have rank of at least 2");
        if (aType.type().rank() != bType.type().rank())
            throw new IllegalArgumentException("Tensors in matmul must have the same rank");

        Optional<TensorFunction> aFunction = inputs.get(0).function();
        Optional<TensorFunction> bFunction = inputs.get(1).function();
        if (!aFunction.isPresent() || !bFunction.isPresent()) {
            return null;
        }
        return new com.yahoo.tensor.functions.Matmul(aFunction.get(), bFunction.get(), aType.dimensions().get(1).name());
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        if (!allInputTypesPresent(2)) {
            return;
        }
        List<TensorType.Dimension> aDimensions = inputs.get(0).type().get().dimensions();
        List<TensorType.Dimension> bDimensions = inputs.get(1).type().get().dimensions();

        String aDim0 = aDimensions.get(0).name();
        String aDim1 = aDimensions.get(1).name();
        String bDim0 = bDimensions.get(0).name();
        String bDim1 = bDimensions.get(1).name();

        // The second dimension of a should have the same name as the first dimension of b
        renamer.addConstraint(aDim1, bDim0, DimensionRenamer::equals, this);

        // The first dimension of a should have a different name than the second dimension of b
        renamer.addConstraint(aDim0, bDim1, DimensionRenamer::lesserThan, this);

        // For efficiency, the dimensions to join over should be innermost - soft constraint
        renamer.addConstraint(aDim0, aDim1, DimensionRenamer::lesserThan, this);
        renamer.addConstraint(bDim0, bDim1, DimensionRenamer::greaterThan, this);
    }

}
