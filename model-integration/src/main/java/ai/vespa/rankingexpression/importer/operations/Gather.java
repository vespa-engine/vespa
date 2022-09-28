// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.rule.OperationNode;
import com.yahoo.searchlib.rankingexpression.rule.Operator;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.EmbracedNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.Slice;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode.wrapScalar;

/*
 * Onnx gather is the same as Numpy take.
 */
public class Gather extends IntermediateOperation {

    private final AttributeMap attributeMap;

    private int axis;

    public Gather(String modelName, String nodeName, List<IntermediateOperation> inputs, AttributeMap attributeMap) {
        super(modelName, nodeName, inputs);
        this.attributeMap = attributeMap;
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! allInputTypesPresent(2)) return null;

        OrderedTensorType dataType = inputs.get(0).type().get();
        OrderedTensorType indicesType = inputs.get(1).type().get();

        axis = (int) attributeMap.get("axis").orElse(DoubleValue.zero).asDouble();
        if (axis < 0)
            axis = dataType.rank() + axis;

        OrderedTensorType.Builder typeBuilder = new OrderedTensorType.Builder(resultValueType());
        for (int i = 0; i < axis; ++i) {
            addDimension(i, dataType.dimensions().get(i).size().orElse(-1L), typeBuilder);
        }
        for (int i = 0; i < indicesType.rank(); ++i) {
            addDimension(i + axis, indicesType.dimensions().get(i).size().orElse(-1L), typeBuilder);
        }
        for (int i = axis + 1; i < dataType.rank(); ++i) {
            addDimension(i + indicesType.rank(), dataType.dimensions().get(i).size().orElse(-1L), typeBuilder);
        }

        inputs.get(0).exportAsRankingFunction = true;
        inputs.get(1).exportAsRankingFunction = true;

        return typeBuilder.build();
    }

    private void addDimension(int dimensionIndex, long size, OrderedTensorType.Builder typeBuilder) {
        String name = String.format("%s_%d", vespaName(), dimensionIndex);
        typeBuilder.add(TensorType.Dimension.indexed(name, size));
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if ( ! allInputFunctionsPresent(2)) return null;

        IntermediateOperation data = inputs.get(0);
        IntermediateOperation indices = inputs.get(1);
        OrderedTensorType dataType = data.type().get();
        OrderedTensorType indicesType = indices.type().get();

        String dataFunctionName = data.rankingExpressionFunctionName();
        String indicesFunctionName = indices.rankingExpressionFunctionName();

        List<Slice.DimensionValue<Reference>> dataSliceDimensions = new ArrayList<>();
        for (int i = 0; i < axis; ++i) {
            addSliceDimension(dataSliceDimensions, dataType.dimensions().get(i).name(), i);
        }

        if (indicesType.rank() == 0 && indices.isConstant()) {
            double constantValue = indices.getConstantValue().get().asDouble();
            ExpressionNode indexExpression = new ConstantNode(new DoubleValue(constantValue));
            if (constantValue < 0) {
                ExpressionNode axisSize = new ConstantNode(new DoubleValue(dataType.dimensions().get(axis).size().get()));
                indexExpression = new EmbracedNode(new OperationNode(indexExpression, Operator.plus, axisSize));
            }
            addSliceDimension(dataSliceDimensions, dataType.dimensions().get(axis).name(), indexExpression);
        } else {
            List<Slice.DimensionValue<Reference>> indicesSliceDimensions = new ArrayList<>();
            for (int i = 0; i < indicesType.rank(); ++i) {
                addSliceDimension(indicesSliceDimensions, indicesType.dimensions().get(i).name(), axis + i);
            }
            ExpressionNode sliceExpression = createSliceExpression(indicesSliceDimensions, indicesFunctionName);
            ExpressionNode indexExpression = createIndexExpression(dataType, sliceExpression);
            addSliceDimension(dataSliceDimensions, dataType.dimensions().get(axis).name(), indexExpression);
        }

        for (int i = axis + 1; i < dataType.rank(); ++i) {
            addSliceDimension(dataSliceDimensions, dataType.dimensions().get(i).name(), i + indicesType.rank() - 1);
        }

        ExpressionNode sliceExpression = createSliceExpression(dataSliceDimensions, dataFunctionName);
        return Generate.bound(type.type(), wrapScalar(sliceExpression));
    }

    private ExpressionNode createSliceExpression(List<Slice.DimensionValue<Reference>> dimensionValues, String referenceName) {
        TensorFunction<Reference> inputIndices = new TensorFunctionNode.ExpressionTensorFunction(new ReferenceNode(referenceName));
        if (dimensionValues.isEmpty()) {
            return new TensorFunctionNode(inputIndices);
        }
        Slice<Reference> sliceIndices = new Slice<>(inputIndices, dimensionValues);
        return new TensorFunctionNode(sliceIndices);
    }

    /** to support negative indexing */
    private ExpressionNode createIndexExpression(OrderedTensorType dataType, ExpressionNode slice) {
        ExpressionNode axisSize = new ConstantNode(new DoubleValue(dataType.dimensions().get(axis).size().get()));
        ExpressionNode plus = new EmbracedNode(new OperationNode(slice, Operator.plus, axisSize));
        ExpressionNode mod = new OperationNode(plus, Operator.modulo, axisSize);
        return mod;
    }

    private void addSliceDimension(List<Slice.DimensionValue<Reference>> dimensionValues, String dimensionName, ExpressionNode expr) {
        dimensionValues.add(new Slice.DimensionValue<>(Optional.of(dimensionName), wrapScalar(new EmbracedNode(expr))));
    }

    private void addSliceDimension(List<Slice.DimensionValue<Reference>> dimensionValues, String dimensionName, int dimensionIndex) {
        String outputDimensionName = type.dimensions().get(dimensionIndex).name();
        addSliceDimension(dimensionValues, dimensionName, new ReferenceNode(outputDimensionName));
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        if ( ! allInputTypesPresent(2)) return;

        for (int i = 0; i < type.dimensions().size(); i++) {
            renamer.addDimension(type.dimensions().get(i).name());
            for (int j = i + 1; j < type.dimensions().size(); j++) {
                renamer.addConstraint(type.dimensions().get(i).name(), type.dimensions().get(j).name(),
                        DimensionRenamer.Constraint.lessThan(), this);
            }
        }

        OrderedTensorType dataType = inputs.get(0).type().get();
        OrderedTensorType indicesType = inputs.get(1).type().get();

        for (int i = 0; i < axis; ++i) {
            renamer.addConstraint(type.dimensions().get(i).name(),
                                  dataType.dimensions().get(i).name(),
                                  DimensionRenamer.Constraint.equal(), this);
        }
        for (int i = 0; i < indicesType.rank(); ++i) {
            renamer.addConstraint(type.dimensions().get(i + axis).name(),
                                  indicesType.dimensions().get(i).name(),
                                  DimensionRenamer.Constraint.equal(), this);
        }
        for (int i = axis + 1; i < dataType.rank(); ++i) {
            renamer.addConstraint(type.dimensions().get(i + indicesType.rank() - 1).name(),
                                  dataType.dimensions().get(i).name(),
                                  DimensionRenamer.Constraint.equal(), this);
        }

    }

    @Override
    public Gather withInputs(List<IntermediateOperation> inputs) {
        return new Gather(modelName(), name(), inputs, attributeMap);
    }

    @Override
    public String operationName() { return "Gather"; }

}
