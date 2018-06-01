// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations;

import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.rule.ArithmeticNode;
import com.yahoo.searchlib.rankingexpression.rule.ArithmeticOperator;
import com.yahoo.searchlib.rankingexpression.rule.ComparisonNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.GeneratorLambdaFunctionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TruthOperator;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType.tensorSize;

public class Reshape extends IntermediateOperation {

    public Reshape(String modelName, String nodeName, List<IntermediateOperation> inputs) {
        super(modelName, nodeName, inputs);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(2)) {
            return null;
        }
        IntermediateOperation newShape = inputs.get(1);
        if (!newShape.getConstantValue().isPresent()) {
            throw new IllegalArgumentException("Reshape in " + name + ": " +
                    "shape input must be a constant.");
        }
        Tensor shape = newShape.getConstantValue().get().asTensor();

        OrderedTensorType inputType = inputs.get(0).type().get();
        OrderedTensorType.Builder outputTypeBuilder = new OrderedTensorType.Builder();
        int dimensionIndex = 0;
        for (Iterator<Tensor.Cell> cellIterator = shape.cellIterator(); cellIterator.hasNext();) {
            Tensor.Cell cell = cellIterator.next();
            int size = cell.getValue().intValue();
            if (size < 0) {
                size = -1 * (int)shape.reduce(Reduce.Aggregator.prod).asDouble() /
                                tensorSize(inputType.type()).intValue();
            }
            outputTypeBuilder.add(TensorType.Dimension.indexed(
                    String.format("%s_%d", vespaName(), dimensionIndex), size));
            dimensionIndex++;
        }
        return outputTypeBuilder.build();
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        if (!allInputTypesPresent(2)) {
            return null;
        }
        if (!allInputFunctionsPresent(2)) {
            return null;
        }
        OrderedTensorType inputType = inputs.get(0).type().get();
        TensorFunction inputFunction = inputs.get(0).function().get();
        return reshape(inputFunction, inputType.type(), type.type());
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        for (TensorType.Dimension dimension : type.type().dimensions()) {
            renamer.addDimension(dimension.name());
        }
    }

    public static TensorFunction reshape(TensorFunction inputFunction, TensorType inputType, TensorType outputType) {
        if (!tensorSize(inputType).equals(tensorSize(outputType))) {
            throw new IllegalArgumentException("New and old shape of tensor must have the same size when reshaping");
        }

        // Conceptually, reshaping consists on unrolling a tensor to an array using the dimension order,
        // then use the dimension order of the new shape to roll back into a tensor.
        // Here we create a transformation tensor that is multiplied with the from tensor to map into
        // the new shape. We have to introduce temporary dimension names and rename back if dimension names
        // in the new and old tensor type overlap.

        ExpressionNode unrollFrom = unrollTensorExpression(inputType);
        ExpressionNode unrollTo = unrollTensorExpression(outputType);
        ExpressionNode transformExpression = new ComparisonNode(unrollFrom, TruthOperator.EQUAL, unrollTo);

        TensorType transformationType = new TensorType.Builder(inputType, outputType).build();
        Generate transformTensor = new Generate(transformationType,
                new GeneratorLambdaFunctionNode(transformationType, transformExpression).asLongListToDoubleOperator());

        TensorFunction outputFunction = new Reduce(
                new com.yahoo.tensor.functions.Join(inputFunction, transformTensor, ScalarFunctions.multiply()),
                Reduce.Aggregator.sum,
                inputType.dimensions().stream().map(TensorType.Dimension::name).collect(Collectors.toList()));

        return outputFunction;
    }

    private static ExpressionNode unrollTensorExpression(TensorType type) {
        if (type.rank() == 0) {
            return new ConstantNode(DoubleValue.zero);
        }
        List<ExpressionNode> children = new ArrayList<>();
        List<ArithmeticOperator> operators = new ArrayList<>();
        int size = 1;
        for (int i = type.dimensions().size() - 1; i >= 0; --i) {
            TensorType.Dimension dimension = type.dimensions().get(i);
            children.add(0, new ReferenceNode(dimension.name()));
            if (size > 1) {
                operators.add(0, ArithmeticOperator.MULTIPLY);
                children.add(0, new ConstantNode(new DoubleValue(size)));
            }
            size *= OrderedTensorType.dimensionSize(dimension);
            if (i > 0) {
                operators.add(0, ArithmeticOperator.PLUS);
            }
        }
        return new ArithmeticNode(children, operators);
    }

}
