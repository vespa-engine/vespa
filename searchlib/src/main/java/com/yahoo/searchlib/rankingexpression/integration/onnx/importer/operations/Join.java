// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.onnx.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.OrderedTensorType;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.TensorFunction;
import onnx.Onnx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

public class Join extends OnnxOperation {

    private final DoubleBinaryOperator operator;

    public Join(Onnx.NodeProto node, List<OnnxOperation> inputs, DoubleBinaryOperator operator) {
        super(node, inputs);
        this.operator = operator;
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(2)) {
            return null;
        }
        OrderedTensorType a = largestInput().type().get();
        OrderedTensorType b = smallestInput().type().get();

        OrderedTensorType.Builder builder = new OrderedTensorType.Builder();
        int sizeDifference = a.rank() - b.rank();
        for (int i = 0; i < a.rank(); ++i) {
            TensorType.Dimension aDim = a.dimensions().get(i);
            long size = aDim.size().orElse(-1L);

            if (i - sizeDifference >= 0) {
                TensorType.Dimension bDim = b.dimensions().get(i - sizeDifference);
                size = Math.max(size, bDim.size().orElse(-1L));
            }

            if (aDim.type() == TensorType.Dimension.Type.indexedBound) {
                builder.add(TensorType.Dimension.indexed(aDim.name(), size));
            } else if (aDim.type() == TensorType.Dimension.Type.indexedUnbound) {
                builder.add(TensorType.Dimension.indexed(aDim.name()));
            } else if (aDim.type() == TensorType.Dimension.Type.mapped) {
                builder.add(TensorType.Dimension.mapped(aDim.name()));
            }
        }
        return builder.build();
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        if (!allInputTypesPresent(2)) {
            return null;
        }
        if (!allInputFunctionsPresent(2)) {
            return null;
        }

        OnnxOperation a = largestInput();
        OnnxOperation b = smallestInput();

        List<String> aDimensionsToReduce = new ArrayList<>();
        List<String> bDimensionsToReduce = new ArrayList<>();
        int sizeDifference = a.type().get().rank() - b.type().get().rank();
        for (int i = 0; i < b.type().get().rank(); ++i) {
            TensorType.Dimension bDim = b.type().get().dimensions().get(i);
            TensorType.Dimension aDim = a.type().get().dimensions().get(i + sizeDifference);
            long bSize = bDim.size().orElse(-1L);
            long aSize = aDim.size().orElse(-1L);
            if (bSize == 1L && aSize != 1L) {
                bDimensionsToReduce.add(bDim.name());
            }
            if (aSize == 1L && bSize != 1L) {
                aDimensionsToReduce.add(bDim.name());
            }
        }

        TensorFunction aReducedFunction = a.function().get();
        if (aDimensionsToReduce.size() > 0) {
            aReducedFunction = new Reduce(a.function().get(), Reduce.Aggregator.sum, aDimensionsToReduce);
        }
        TensorFunction bReducedFunction = b.function().get();
        if (bDimensionsToReduce.size() > 0) {
            bReducedFunction = new Reduce(b.function().get(), Reduce.Aggregator.sum, bDimensionsToReduce);
        }

        return new com.yahoo.tensor.functions.Join(aReducedFunction, bReducedFunction, operator);
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        if (!allInputTypesPresent(2)) {
            return;
        }
        OrderedTensorType a = largestInput().type().get();
        OrderedTensorType b = smallestInput().type().get();
        int sizeDifference = a.rank() - b.rank();
        for (int i = 0; i < b.rank(); ++i) {
            String bDim = b.dimensions().get(i).name();
            String aDim = a.dimensions().get(i + sizeDifference).name();
            renamer.addConstraint(aDim, bDim, DimensionRenamer::equals, this);
        }
    }

    private OnnxOperation largestInput() {
        OrderedTensorType a = inputs.get(0).type().get();
        OrderedTensorType b = inputs.get(1).type().get();
        return a.rank() >= b.rank() ? inputs.get(0) : inputs.get(1);
    }

    private OnnxOperation smallestInput() {
        OrderedTensorType a = inputs.get(0).type().get();
        OrderedTensorType b = inputs.get(1).type().get();
        return a.rank() < b.rank() ? inputs.get(0) : inputs.get(1);
    }

}
