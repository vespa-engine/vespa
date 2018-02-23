// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations;

import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.GeneratorLambdaFunctionNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.NodeDef;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class Mean extends TensorFlowOperation {

    private List<String> reduceDimensions;

    public Mean(NodeDef node, List<TensorFlowOperation> inputs, int port) {
        super(node, inputs, port);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(2)) {
            return null;
        }
        TensorFlowOperation reductionIndices = inputs.get(1);
        if (!reductionIndices.getConstantValue().isPresent()) {
            throw new IllegalArgumentException("Mean in " + node.getName() + ": " +
                    "reduction indices must be a constant.");
        }
        Tensor indices = reductionIndices.getConstantValue().get().asTensor();
        reduceDimensions = new ArrayList<>();

        OrderedTensorType inputType = inputs.get(0).type().get();
        for (Iterator<Tensor.Cell> cellIterator = indices.cellIterator(); cellIterator.hasNext();) {
            Tensor.Cell cell = cellIterator.next();
            int dimensionIndex = cell.getValue().intValue();
            if (dimensionIndex < 0) {
                dimensionIndex = inputType.dimensions().size() - dimensionIndex;
            }
            reduceDimensions.add(inputType.dimensions().get(dimensionIndex).name());
        }
        return reducedType(inputType, shouldKeepDimensions());
    }

    // todo: optimization: if keepDims and one reduce dimension that has size 1: same as identity.

    @Override
    protected TensorFunction lazyGetFunction() {
        if (!allInputTypesPresent(2)) {
            return null;
        }
        TensorFunction inputFunction = inputs.get(0).function().get();
        TensorFunction output = new Reduce(inputFunction, Reduce.Aggregator.avg, reduceDimensions);
        if (shouldKeepDimensions()) {
            // multiply with a generated tensor created from the reduced dimensions
            TensorType.Builder typeBuilder = new TensorType.Builder();
            for (String name : reduceDimensions) {
                typeBuilder.indexed(name, 1);
            }
            TensorType generatedType = typeBuilder.build();
            ExpressionNode generatedExpression = new ConstantNode(new DoubleValue(1));
            Generate generatedFunction = new Generate(generatedType,
                    new GeneratorLambdaFunctionNode(generatedType, generatedExpression).asLongListToDoubleOperator());
            output = new com.yahoo.tensor.functions.Join(output, generatedFunction, ScalarFunctions.multiply());
        }
        return output;
    }

    @Override
    public void renameDimensions(DimensionRenamer renamer) {
        super.renameDimensions(renamer);
        List<String> renamedDimensions = new ArrayList<>(reduceDimensions.size());
        for (String name : reduceDimensions) {
            Optional<String> newName = renamer.dimensionNameOf(name);
            if (!newName.isPresent()) {
                return;  // presumably, already renamed
            }
            renamedDimensions.add(newName.get());
        }
        reduceDimensions = renamedDimensions;
    }

    private boolean shouldKeepDimensions() {
        AttrValue keepDimsAttr = node.getAttrMap().get("keep_dims");
        return keepDimsAttr != null && keepDimsAttr.getB();
    }

    private OrderedTensorType reducedType(OrderedTensorType inputType, boolean keepDimensions) {
        OrderedTensorType.Builder builder = new OrderedTensorType.Builder(node);
        for (TensorType.Dimension dimension: inputType.type().dimensions()) {
            if (!reduceDimensions.contains(dimension.name())) {
                builder.add(dimension);
            } else if (keepDimensions) {
                builder.add(TensorType.Dimension.indexed(dimension.name(), 1L));
            }
        }
        return builder.build();
    }

}
