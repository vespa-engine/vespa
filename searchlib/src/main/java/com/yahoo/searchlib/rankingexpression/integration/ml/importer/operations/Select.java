// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.ml.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;
import java.util.function.DoubleBinaryOperator;

import static com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType.dimensionSize;
import static com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType.tensorSize;

public class Select extends IntermediateOperation {

    public Select(String modelName, String nodeName, List<IntermediateOperation> inputs) {
        super(modelName, nodeName, inputs);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(3)) {
            return null;
        }
        OrderedTensorType a = inputs.get(1).type().get();
        OrderedTensorType b = inputs.get(2).type().get();
        if ((a.type().rank() != b.type().rank()) || !(tensorSize(a.type()).equals(tensorSize(b.type())))) {
            throw new IllegalArgumentException("'Select': input tensors must have the same shape");
        }
        return a;
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        if (!allInputFunctionsPresent(3)) {
            return null;
        }
        IntermediateOperation conditionOperation = inputs().get(0);
        TensorFunction a = inputs().get(1).function().get();
        TensorFunction b = inputs().get(2).function().get();

        // Shortcut: if we know during import which tensor to select, do that directly here.
        if (conditionOperation.getConstantValue().isPresent()) {
            Tensor condition = conditionOperation.getConstantValue().get().asTensor();
            if (condition.type().rank() == 0) {
                return ((int) condition.asDouble() == 0) ? b : a;
            }
            if (condition.type().rank() == 1 && dimensionSize(condition.type().dimensions().get(0)) == 1) {
                return condition.cellIterator().next().getValue().intValue() == 0 ? b : a;
            }
        }

        // The task is to select cells from 'x' or 'y' based on 'condition'.
        // If 'condition' is 0 (false), select from 'y', if 1 (true) select
        // from 'x'. We do this by individually joining 'x' and 'y' with
        // 'condition', and then joining the resulting two tensors.

        TensorFunction conditionFunction = conditionOperation.function().get();
        TensorFunction aCond = new com.yahoo.tensor.functions.Join(a, conditionFunction, ScalarFunctions.multiply());
        TensorFunction bCond = new com.yahoo.tensor.functions.Join(b, conditionFunction, new DoubleBinaryOperator() {
             @Override public double applyAsDouble(double a, double b) { return a * (1.0 - b); }
             @Override public String toString() { return "f(a,b)(a * (1-b))"; }
         });
        return new com.yahoo.tensor.functions.Join(aCond, bCond, ScalarFunctions.add());
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        if (!allInputTypesPresent(3)) {
            return;
        }
        List<TensorType.Dimension> aDimensions = inputs.get(1).type().get().dimensions();
        List<TensorType.Dimension> bDimensions = inputs.get(2).type().get().dimensions();

        String aDim0 = aDimensions.get(0).name();
        String aDim1 = aDimensions.get(1).name();
        String bDim0 = bDimensions.get(0).name();
        String bDim1 = bDimensions.get(1).name();

        // These tensors should have the same dimension names
        renamer.addConstraint(aDim0, bDim0, DimensionRenamer::equals, this);
        renamer.addConstraint(aDim1, bDim1, DimensionRenamer::equals, this);
    }

}
