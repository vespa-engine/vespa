// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.framework.NodeDef;

import java.util.List;
import java.util.Optional;

public class ConcatV2 extends TensorFlowOperation {

    private String concatDimensionName;

    public ConcatV2(String modelName, NodeDef node, List<TensorFlowOperation> inputs, int port) {
        super(modelName, node, inputs, port);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!inputs.stream().map(TensorFlowOperation::type).allMatch(Optional::isPresent)) {
            return null;
        }

        TensorFlowOperation concatDimOp = inputs.get(inputs.size() - 1);  // ConcatV2: concat dimension is the last input
        if (!concatDimOp.getConstantValue().isPresent()) {
            throw new IllegalArgumentException("ConcatV2 in " + node.getName() + ": " +
                                               "concat dimension must be a constant.");
        }
        Tensor concatDimTensor = concatDimOp.getConstantValue().get().asTensor();
        if (concatDimTensor.type().rank() != 0) {
            throw new IllegalArgumentException("ConcatV2 in " + node.getName() + ": " +
                                               "concat dimension must be a scalar.");
        }

        OrderedTensorType aType = inputs.get(0).type().get();

        int concatDim = (int)concatDimTensor.asDouble();
        long concatDimSize = aType.dimensions().get(concatDim).size().orElse(-1L);

        for (int i = 1; i < inputs.size() - 1; ++i) {
            OrderedTensorType bType = inputs.get(i).type().get();
            if (bType.rank() != aType.rank()) {
                throw new IllegalArgumentException("ConcatV2 in " + node.getName() + ": " +
                                                   "inputs must have save rank.");
            }
            for (int j = 0; j < aType.rank(); ++j) {
                long dimSizeA = aType.dimensions().get(j).size().orElse(-1L);
                long dimSizeB = bType.dimensions().get(j).size().orElse(-1L);
                if (j == concatDim) {
                    concatDimSize += dimSizeB;
                } else if (dimSizeA != dimSizeB) {
                    throw new IllegalArgumentException("ConcatV2 in " + node.getName() + ": " +
                                                       "input dimension " + j + " differs in input tensors.");
                }
            }
        }

        OrderedTensorType.Builder typeBuilder = new OrderedTensorType.Builder(node);
        int dimensionIndex = 0;
        for (TensorType.Dimension dimension : aType.dimensions()) {
            if (dimensionIndex == concatDim) {
                concatDimensionName = dimension.name();
                typeBuilder.add(TensorType.Dimension.indexed(concatDimensionName, concatDimSize));
            } else {
                typeBuilder.add(dimension);
            }
            dimensionIndex++;
        }
        return typeBuilder.build();
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        if (!inputs.stream().map(TensorFlowOperation::function).allMatch(Optional::isPresent)) {
            return null;
        }
        TensorFunction result = inputs.get(0).function().get();
        for (int i = 1; i < inputs.size() - 1; ++i) {
            TensorFunction b = inputs.get(i).function().get();
            result = new com.yahoo.tensor.functions.Concat(result, b, concatDimensionName);
        }
        return result;
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        if (!inputs.stream().map(TensorFlowOperation::type).allMatch(Optional::isPresent)) {
            return;
        }
        OrderedTensorType a = inputs.get(0).type().get();
        for (int i = 1; i < inputs.size() - 1; ++i) {
            OrderedTensorType b = inputs.get(i).type().get();
            String bDim = b.dimensions().get(i).name();
            String aDim = a.dimensions().get(i).name();
            renamer.addConstraint(aDim, bDim, DimensionRenamer::equals, this);
        }
    }

    @Override
    public void renameDimensions(DimensionRenamer renamer) {
        super.renameDimensions(renamer);
        concatDimensionName = renamer.dimensionNameOf(concatDimensionName).orElse(concatDimensionName);
   }

}
