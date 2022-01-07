// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.EmbracedNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode.wrapScalar;

public class Expand extends IntermediateOperation {

    public Expand(String modelName, String nodeName, List<IntermediateOperation> inputs) {
        super(modelName, nodeName, inputs);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(2)) return null;

        // required as we use tensor create
        inputs.get(0).exportAsRankingFunction = true;

        Optional<Value> shapeValue = inputs.get(1).getConstantValue();
        if (shapeValue.isEmpty())
            throw new IllegalArgumentException("Expand " + name + ": shape must be a constant.");

        Tensor shape = shapeValue.get().asTensor();
        if (shape.type().rank() != 1)
            throw new IllegalArgumentException("Expand " + name + ": shape must be a 1-d tensor.");

        OrderedTensorType inputType = inputs.get(0).type().get();

        int inputRank = inputType.rank();
        int shapeSize = shape.type().dimensions().get(0).size().get().intValue();
        int sizeDiff = shapeSize - inputRank;

        OrderedTensorType.Builder typeBuilder = new OrderedTensorType.Builder(inputType.type().valueType());
        Iterator<Double> iter = shape.valueIterator();

        // Add any extra dimensions
        for (int i = 0; i < sizeDiff; ++i) {
            typeBuilder.add(TensorType.Dimension.indexed(vespaName() + "_" + i, iter.next().intValue()));
        }

        // Dimensions are matched innermost
        for (int i = sizeDiff; i < shapeSize; i++) {
            int shapeDimSize = iter.next().intValue();
            int inputDimSize = inputType.dimensions().get(i - sizeDiff).size().get().intValue();
            if (shapeDimSize != inputDimSize && shapeDimSize != 1 && inputDimSize != 1) {
                throw new IllegalArgumentException("Expand " + name + ": dimension sizes of input and shape " +
                        "are not compatible. Either they must be equal or one must be of size 1.");
            }
            int dimSize = Math.max(shapeDimSize, inputDimSize);
            typeBuilder.add(TensorType.Dimension.indexed(vespaName() + "_" + i, dimSize));
        }

        return typeBuilder.build();
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if (!allInputFunctionsPresent(2)) return null;

        IntermediateOperation input = inputs.get(0);
        OrderedTensorType inputType = input.type().get();
        OrderedTensorType type = type().get();
        String inputFunctionName = input.rankingExpressionFunctionName();

        List<com.yahoo.tensor.functions.Slice.DimensionValue<Reference>> dimensionValues = new ArrayList<>();

        int sizeDiff = type().get().rank() - inputType.rank();
        for (int i = sizeDiff; i < type().get().rank(); ++i) {
            String inputDimensionName = inputType.dimensions().get(i - sizeDiff).name();
            String typeDimensionName = type.dimensionNames().get(i);
            long inputDimensionSize = inputType.dimensions().get(i - sizeDiff).size().get();

            ExpressionNode index;
            if (inputDimensionSize == 1) {
                index = new ConstantNode(new DoubleValue(0.0));
            } else {
                index = new EmbracedNode(new ReferenceNode(typeDimensionName));
            }
            dimensionValues.add(new com.yahoo.tensor.functions.Slice.DimensionValue<>(Optional.of(inputDimensionName), wrapScalar(index)));
        }

        TensorFunction<Reference> externalRef = new TensorFunctionNode.ExpressionTensorFunction(new ReferenceNode(inputFunctionName));
        com.yahoo.tensor.functions.Slice<Reference> sliceIndices = new com.yahoo.tensor.functions.Slice<>(externalRef, dimensionValues);
        ExpressionNode sliceExpression = new TensorFunctionNode(sliceIndices);
        return Generate.bound(type.type(), wrapScalar(sliceExpression));
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        addConstraintsFrom(type, renamer);
    }

    @Override
    public Expand withInputs(List<IntermediateOperation> inputs) {
        return new Expand(modelName(), name(), inputs);
    }

    @Override
    public String operationName() { return "Expand"; }

}

