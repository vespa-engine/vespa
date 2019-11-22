// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;
import com.yahoo.text.ExpressionFormatter;

import java.util.List;
import java.util.Optional;

public class MatMul extends IntermediateOperation {

    public MatMul(String modelName, String nodeName, List<IntermediateOperation> inputs) {
        super(modelName, nodeName, inputs);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! allInputTypesPresent(2)) return null;

        OrderedTensorType.Builder typeBuilder = new OrderedTensorType.Builder(resultValueType());
        typeBuilder.add(inputs.get(0).type().get().dimensions().get(0));
        typeBuilder.add(inputs.get(1).type().get().dimensions().get(1));
        return typeBuilder.build();
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        if ( ! allInputTypesPresent(2)) return null;

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
        if ( ! allInputTypesPresent(2)) return;

        List<TensorType.Dimension> aDimensions = inputs.get(0).type().get().dimensions();
        List<TensorType.Dimension> bDimensions = inputs.get(1).type().get().dimensions();

        assertTwoDimensions(aDimensions, inputs.get(0), "first argument");
        assertTwoDimensions(bDimensions, inputs.get(1), "second argument");

        String aDim0 = aDimensions.get(0).name();
        String aDim1 = aDimensions.get(1).name();
        String bDim0 = bDimensions.get(0).name();
        String bDim1 = bDimensions.get(1).name();

        // The second dimension of a should have the same name as the first dimension of b
        renamer.addConstraint(aDim1, bDim0, DimensionRenamer.Constraint.equal(false), this);

        // The first dimension of a should have a different name than the second dimension of b
        renamer.addConstraint(aDim0, bDim1, DimensionRenamer.Constraint.lessThan(false), this);

        // For efficiency, the dimensions to join over should be innermost - soft constraint
        renamer.addConstraint(aDim0, aDim1, DimensionRenamer.Constraint.lessThan(true), this);
        renamer.addConstraint(bDim0, bDim1, DimensionRenamer.Constraint.greaterThan(true), this);
    }

    private void assertTwoDimensions(List<TensorType.Dimension> dimensions, IntermediateOperation supplier, String inputDescription) {
        if (dimensions.size() >= 2) return;
        throw new IllegalArgumentException("Expected 2 dimensions in the " + inputDescription + " to " + this +
                                           " but got just " + dimensions + " from\n" +
                                           ExpressionFormatter.inTwoColumnMode(70, 50).format(supplier.toFullString()));
    }

    @Override
    public MatMul withInputs(List<IntermediateOperation> inputs) {
        return new MatMul(modelName(), name(), inputs);
    }

    @Override
    public String operationName() { return "MatMul"; }

}
