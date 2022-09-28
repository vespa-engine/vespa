// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.OperationNode;
import com.yahoo.searchlib.rankingexpression.rule.Operator;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.EmbracedNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode.wrapScalar;

public class Split extends IntermediateOperation {

    private final AttributeMap attributes;
    private final int output;

    private final int axis;
    private int start;
    private int end;

    public Split(String modelName, String nodeName, List<IntermediateOperation> inputs, AttributeMap attributes, int output) {
        super(modelName, nodeName, inputs);
        this.attributes = attributes;
        this.output = output;
        axis = (int) attributes.get("axis").orElse(DoubleValue.zero).asDouble();
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(1))
            return null;
        OrderedTensorType inputType = inputs.get(0).type().get();

        // required as we use tensor create
        inputs.get(0).exportAsRankingFunction = true;

        int axisSize = inputType.dimensions().get(axis).size().get().intValue();
        start = 0;
        end = axisSize;

        if (attributes.getList("split").isPresent()) {
            List<Value> splitList = attributes.getList("split").get();
            if (output > splitList.size()) {
                throw new IllegalArgumentException("Split in " + name + ": output out of range of split list");
            }
            for (int i = 0; i < output; ++i) {
                start += (int) splitList.get(i).asDouble();
            }
            if (output < splitList.size()) {
                end = start + (int) splitList.get(output).asDouble();
            }
        } else {
            start = axisSize / 2 * output;
            end = start + axisSize / 2;
        }

        if (start >= axisSize || start < 0) {
            throw new IllegalArgumentException("Split in " + name + ": split start index out of range (" + start + ")");
        }
        if (end > axisSize || end < 0) {
            throw new IllegalArgumentException("Split in " + name + ": split end index out of range (" + end + ")");
        }

        OrderedTensorType.Builder typeBuilder = new OrderedTensorType.Builder(resultValueType());
        for (int i = 0; i < inputType.rank(); ++i) {
            TensorType.Dimension inputDimension = inputType.dimensions().get(i);
            long dimSize = i == axis ? end - start : inputDimension.size().get();
            typeBuilder.add(TensorType.Dimension.indexed(inputDimension.name(), dimSize));
        }
        return typeBuilder.build();
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if (!allInputFunctionsPresent(1)) return null;

        IntermediateOperation input = inputs.get(0);
        OrderedTensorType inputType = input.type().get();
        String inputFunctionName = input.rankingExpressionFunctionName();

        List<com.yahoo.tensor.functions.Slice.DimensionValue<Reference>> dimensionValues = new ArrayList<>();

        for (int i = 0; i < inputType.rank(); ++i) {
            String inputDimensionName = inputType.dimensions().get(i).name();
            ExpressionNode reference = new ReferenceNode(inputDimensionName);
            ExpressionNode offset = new OperationNode(reference, Operator.plus, new ConstantNode(new DoubleValue(i == axis ? start : 0)));
            dimensionValues.add(new com.yahoo.tensor.functions.Slice.DimensionValue<>(Optional.of(inputDimensionName), wrapScalar(new EmbracedNode(offset))));
        }

        TensorFunction<Reference> inputIndices = new TensorFunctionNode.ExpressionTensorFunction(new ReferenceNode(inputFunctionName));
        com.yahoo.tensor.functions.Slice<Reference> sliceIndices = new com.yahoo.tensor.functions.Slice<>(inputIndices, dimensionValues);
        ExpressionNode sliceExpression = new TensorFunctionNode(sliceIndices);

        TensorFunction<Reference> generate = Generate.bound(type.type(), wrapScalar(sliceExpression));
        return generate;
    }

    @Override
    public Split withInputs(List<IntermediateOperation> inputs) {
        return new Split(modelName(), name(), inputs, attributes, output);
    }

    @Override
    public String operationName() { return "Split"; }

}
