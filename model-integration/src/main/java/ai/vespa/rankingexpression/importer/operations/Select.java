// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;
import java.util.function.DoubleBinaryOperator;

import static ai.vespa.rankingexpression.importer.OrderedTensorType.dimensionSize;
import static ai.vespa.rankingexpression.importer.OrderedTensorType.tensorSize;

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
    protected TensorFunction<Reference> lazyGetFunction() {
        if (!allInputFunctionsPresent(3)) {
            return null;
        }
        IntermediateOperation conditionOperation = inputs().get(0);
        TensorFunction<Reference> a = inputs().get(1).function().get();
        TensorFunction<Reference> b = inputs().get(2).function().get();

        // Shortcut: if we know during import which tensor to select, do that directly here.
        if (conditionOperation.getConstantValue().isPresent()) {
            Tensor condition = conditionOperation.getConstantValue().get().asTensor();
            if (condition.type().rank() == 0) {
                return ((int) condition.asDouble() == 0) ? b : a;
            }
            if (condition.type().rank() == 1 && dimensionSize(condition.type().dimensions().get(0)) == 1) {
                return condition.cellIterator().next().getValue().intValue() == 0 ? b : a;
            }
            if (condition.type().rank() == 2 && dimensionSize(condition.type().dimensions().get(0)) == 1 && dimensionSize(condition.type().dimensions().get(1)) == 1) {
                return condition.cellIterator().next().getValue().intValue() == 0 ? b : a;
            }
        }

        // The task is to select cells from 'x' or 'y' based on 'condition'.
        // If 'condition' is 0 (false), select from 'y', if 1 (true) select
        // from 'x'. We do this by individually joining 'x' and 'y' with
        // 'condition', and then joining the resulting two tensors.

        TensorFunction<Reference> conditionFunction = conditionOperation.function().get();
        TensorFunction<Reference> aCond = new com.yahoo.tensor.functions.Join<>(a, conditionFunction, ScalarFunctions.multiply());
        TensorFunction<Reference> bCond = new com.yahoo.tensor.functions.Join<>(b, conditionFunction, new DoubleBinaryOperator() {
             @Override public double applyAsDouble(double a, double b) { return a * (1.0 - b); }
             @Override public String toString() { return "f(a,b)(a * (1-b))"; }
         });
        return new com.yahoo.tensor.functions.Join<>(aCond, bCond, ScalarFunctions.add());
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        if (!allInputTypesPresent(3)) {
            return;
        }
        List<TensorType.Dimension> aDimensions = inputs.get(1).type().get().dimensions();
        List<TensorType.Dimension> bDimensions = inputs.get(2).type().get().dimensions();

        // These tensors should have the same dimension names
        for (int i = 0; i < aDimensions.size(); ++i) {
            String aDim = aDimensions.get(i).name();
            String bDim = bDimensions.get(i).name();
            renamer.addConstraint(aDim, bDim, DimensionRenamer.Constraint.equal(false), this);
        }
    }

    @Override
    public Select withInputs(List<IntermediateOperation> inputs) {
        return new Select(modelName(), name(), inputs);
    }

    @Override
    public String operationName() { return "Select"; }

}
