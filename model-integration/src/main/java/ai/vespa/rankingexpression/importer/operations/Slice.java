// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode.wrapScalar;

/**
 * Onnx slice operation.
 *
 * Opset 1 to 9 accepts starts, ends, and axes tensors as attributes
 *
 * Opset 10 and up accepts starts, ends, axes, and steps tensors as inputs. Here we assume these are
 * constants, otherwise we can't import this model because that would mean we
 * would not know the resulting tensor type until run-time, and that is currently
 * not supported in Vespa.
 */
public class Slice extends IntermediateOperation {

    private final AttributeMap attributes;

    private int[] starts;
    private int[] ends;
    private int[] steps;

    public Slice(String modelName, String nodeName, List<IntermediateOperation> inputs, AttributeMap attributes) {
        super(modelName, nodeName, inputs);
        this.attributes = attributes;
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (inputs.size() < 1 || inputs.get(0).type().isEmpty()) {
            return null;
        }
        OrderedTensorType dataType = inputs.get(0).type().get();

        // required as we use tensor create
        inputs.get(0).exportAsRankingFunction = true;

        // Todo: only supports opsets 1-9, for >= get these from inputs
        int[] startsInput = attributeListAsArray("starts", 0);
        int[] endsInput = attributeListAsArray("ends", 0);
        int[] stepsInput = new int[dataType.rank()]; Arrays.fill(stepsInput, 1);  // Todo: get from input when opset >= 10

        int[] axes;
        if (attributes.getList("axes").isPresent()) {
            axes = attributeListAsArray("axes", 0);
        } else {
            // infer axes: default is [0, 1, ..., len('starts')-1]
            axes = new int[startsInput.length];
            for (int i = 0; i < startsInput.length; ++i) {
                axes[i] = i;
            }
        }

        if (startsInput.length != endsInput.length) {
            throw new IllegalArgumentException("Slice in " + name + ": 'starts' and 'ends' indexes are not of the same size.");
        }
        if (startsInput.length != axes.length) {
            throw new IllegalArgumentException("Slice in " + name + ": 'axes' and 'starts' are not of same size.");
        }

        int[] dimensionSizes = new int [dataType.rank()];
        for (int i = 0; i < dataType.rank(); ++i) {
            dimensionSizes[i] = dataType.dimensions().get(i).size().get().intValue();
        }

        starts = new int[dataType.rank()]; Arrays.fill(starts, 0);
        ends = new int[dataType.rank()];
        steps = new int[dataType.rank()]; Arrays.fill(steps, 1);

        for (int i = 0; i < axes.length; ++i) {
            int axis = axes[i];
            int start = startsInput[i];
            int end = endsInput[i];
            int step = stepsInput[i];

            axis = Math.min(axis, dataType.rank() - 1);
            axis = axis < 0 ? axis + dataType.rank() : axis;

            start = Math.min(start, dimensionSizes[axis]);
            start = start < 0 ? start + dimensionSizes[axis] : start;

            end = Math.min(end, dimensionSizes[axis]);
            end = end < 0 ? end + dimensionSizes[axis] : end;

            // Todo: check negative values for step size

            starts[axis] = start;
            steps[axis] = step;

            if (step == 0) {
                throw new IllegalArgumentException("Slice in " + name + ": illegal step size of 0.");
            }
            if ((end - start) < 1) {
                throw new IllegalArgumentException("Slice in " + name + ": illegal start (" + start + ") and end (" + end + ") index.");
            }
            dimensionSizes[axis] = (end - start) / step;
        }

        OrderedTensorType.Builder typeBuilder = new OrderedTensorType.Builder(resultValueType());
        for (int i = 0; i < dataType.rank(); ++i) {
            addDimension(i, dimensionSizes[i], typeBuilder);
        }
        return typeBuilder.build();
    }

    private int[] attributeListAsArray(String name, int defaultValue) {
        if (attributes.getList(name).isEmpty()) {
            throw new IllegalArgumentException("Slice in " + name + ": Required attribute '" + name + "' is missing.");
        }
        List<Value> list = attributes.getList(name).get();
        int[] result = new int[list.size()]; Arrays.fill(result, defaultValue);
        for (int i = 0; i < list.size(); ++i) {
            result[i] = (int)list.get(i).asDouble();
        }
        return result;
    }

    private void addDimension(int dimensionIndex, long size, OrderedTensorType.Builder typeBuilder) {
        String name = String.format("%s_%d", vespaName(), dimensionIndex);
        typeBuilder.add(TensorType.Dimension.indexed(name, size));
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if (inputs.size() < 1 || inputs.get(0).function().isEmpty()) {
            return null;
        }

        IntermediateOperation data = inputs.get(0);
        OrderedTensorType dataType = data.type().get();
        String dataFunctionName = data.rankingExpressionFunctionName();

        List<com.yahoo.tensor.functions.Slice.DimensionValue<Reference>> dimensionValues = new ArrayList<>();

        for (int axis = 0; axis < dataType.rank(); ++axis) {
            int start = starts[axis];
            int step = steps[axis];

            String inputDimensionName = dataType.dimensions().get(axis).name();
            String outputDimensionName = type.dimensions().get(axis).name();

            ExpressionNode stepSize = new ConstantNode(new DoubleValue(step));
            ExpressionNode startIndex = new ConstantNode(new DoubleValue(start));

            // step * (d0 + start)
            ExpressionNode reference = new ReferenceNode(outputDimensionName);
            ExpressionNode plus = new EmbracedNode(new OperationNode(reference, Operator.plus, startIndex));
            ExpressionNode mul = new OperationNode(stepSize, Operator.multiply, plus);

            dimensionValues.add(new com.yahoo.tensor.functions.Slice.DimensionValue<>(Optional.of(inputDimensionName), wrapScalar(new EmbracedNode(mul))));
        }

        TensorFunction<Reference> inputIndices = new TensorFunctionNode.ExpressionTensorFunction(new ReferenceNode(dataFunctionName));
        com.yahoo.tensor.functions.Slice<Reference> sliceIndices = new com.yahoo.tensor.functions.Slice<>(inputIndices, dimensionValues);
        ExpressionNode sliceExpression = new TensorFunctionNode(sliceIndices);

        return Generate.bound(type.type(), wrapScalar(sliceExpression));
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        for (int i = 0; i < type.dimensions().size(); i++) {
            renamer.addDimension(type.dimensions().get(i).name());
            for (int j = i + 1; j < type.dimensions().size(); j++) {
                renamer.addConstraint(type.dimensions().get(i).name(), type.dimensions().get(j).name(),
                        DimensionRenamer.Constraint.lessThan(), this);
            }
        }
    }

    @Override
    public Slice withInputs(List<IntermediateOperation> inputs) {
        return new Slice(modelName(), name(), inputs, attributes);
    }

    @Override
    public String operationName() { return "Slice"; }

}
