// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations;

import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.GeneratorLambdaFunctionNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExpandDims extends IntermediateOperation {

    private List<String> expandDimensions;

    public ExpandDims(String name, List<IntermediateOperation> inputs) {
        super(name, inputs);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(2)) {
            return null;
        }

        IntermediateOperation axisOperation = inputs().get(1);
        if (!axisOperation.getConstantValue().isPresent()) {
            throw new IllegalArgumentException("ExpandDims in " + name + ": " +
                    "axis must be a constant.");
        }
        Tensor axis = axisOperation.getConstantValue().get().asTensor();
        if (axis.type().rank() != 0) {
            throw new IllegalArgumentException("ExpandDims in " + name + ": " +
                    "axis argument must be a scalar.");
        }

        OrderedTensorType inputType = inputs.get(0).type().get();
        int dimensionToInsert = (int)axis.asDouble();
        if (dimensionToInsert < 0) {
            dimensionToInsert = inputType.dimensions().size() - dimensionToInsert;
        }

        OrderedTensorType.Builder typeBuilder = new OrderedTensorType.Builder();
        expandDimensions = new ArrayList<>();
        int dimensionIndex = 0;
        for (TensorType.Dimension dimension : inputType.dimensions()) {
            if (dimensionIndex == dimensionToInsert) {
                String name = String.format("%s_%d", vespaName(), dimensionIndex);
                expandDimensions.add(name);
                typeBuilder.add(TensorType.Dimension.indexed(name, 1L));
            }
            typeBuilder.add(dimension);
            dimensionIndex++;
        }

        return typeBuilder.build();
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        if (!allInputFunctionsPresent(2)) {
            return null;
        }

        // multiply with a generated tensor created from the reduced dimensions
        TensorType.Builder typeBuilder = new TensorType.Builder();
        for (String name : expandDimensions) {
            typeBuilder.indexed(name, 1);
        }
        TensorType generatedType = typeBuilder.build();
        ExpressionNode generatedExpression = new ConstantNode(new DoubleValue(1));
        Generate generatedFunction = new Generate(generatedType,
                new GeneratorLambdaFunctionNode(generatedType, generatedExpression).asLongListToDoubleOperator());
        return new com.yahoo.tensor.functions.Join(inputs().get(0).function().get(), generatedFunction, ScalarFunctions.multiply());
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        for (TensorType.Dimension dimension : type.type().dimensions()) {
            renamer.addDimension(dimension.name());
        }
    }

    @Override
    public void renameDimensions(DimensionRenamer renamer) {
        super.renameDimensions(renamer);
        List<String> renamedDimensions = new ArrayList<>(expandDimensions.size());
        for (String name : expandDimensions) {
            Optional<String> newName = renamer.dimensionNameOf(name);
            if (!newName.isPresent()) {
                return;  // presumably, already renamed
            }
            renamedDimensions.add(newName.get());
        }
        expandDimensions = renamedDimensions;
    }

}
