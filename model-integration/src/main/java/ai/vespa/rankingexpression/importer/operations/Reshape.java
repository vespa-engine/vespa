// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import ai.vespa.rankingexpression.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.OperationNode;
import com.yahoo.searchlib.rankingexpression.rule.Operator;
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
import java.util.List;
import java.util.Optional;

import static com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode.wrapScalar;

public class Reshape extends IntermediateOperation {

    private final AttributeMap attributeMap;

    public Reshape(String modelName, String nodeName, List<IntermediateOperation> inputs, AttributeMap attributeMap) {
        super(modelName, nodeName, inputs);
        this.attributeMap = attributeMap;
    }

    @Override
    protected OrderedTensorType lazyGetType() {

        // required as we use tensor create
        inputs.get(0).exportAsRankingFunction = true;

        if (inputs.size() == 2) {
            return typeWithShapeAsInput();
        } else if (inputs.size() == 1) {
            return typeWithShapeAsAttribute();
        }
        throw new IllegalArgumentException("Expected 2 or 3 inputs for '" + name + "', got " + inputs.size());
    }

    private OrderedTensorType typeWithShapeAsInput() {
        IntermediateOperation newShape = inputs.get(1);
        if (newShape.getConstantValue().isEmpty())
            throw new IllegalArgumentException("Reshape " + name + ": Shape input must be a constant.");

        OrderedTensorType inputType = inputs.get(0).type().get();
        Tensor shape = newShape.getConstantValue().get().asTensor();
        List<Integer> dimSizes = new ArrayList<>(shape.type().rank());
        shape.valueIterator().forEachRemaining(v -> dimSizes.add(v.intValue()));

        // first pass - set 0 values, meaning that size is retained from input
        for (int i = 0; i < dimSizes.size(); ++i) {
            if (dimSizes.get(i) == 0) {
                if (i >= inputType.dimensions().size()) {
                    throw new IllegalArgumentException("Reshape " + name + ": 0 value for dimension not found in input");
                }
                dimSizes.set(i, inputType.dimensions().get(i).size().get().intValue());
            }
        }

        // second pass - set any -1 value, meaning that the dimension size should be expanded to fill the tensor
        for (int i = 0; i < dimSizes.size(); ++i) {
            if (dimSizes.get(i) < 0) {
                int shapeSize = dimSizes.stream().reduce(1, (a, b) -> a * b);
                int tensorSize = OrderedTensorType.tensorSize(inputType.type()).intValue();
                dimSizes.set(i, -1 * tensorSize / (shapeSize == 0 ? -1 : shapeSize));
            }
        }

        return buildOutputType(dimSizes);
    }

    private OrderedTensorType typeWithShapeAsAttribute() {
        if (attributeMap.getList("shape").isEmpty() || attributeMap.getList("shape").get().size() == 0)
            throw new IllegalArgumentException("Reshape in " + name + ": Shape attribute is empty.");

        OrderedTensorType inputType = inputs.get(0).type().get();
        List<Value> shape = attributeMap.getList("shape").get();
        List<Integer> dimSizes = new ArrayList<>(shape.size());

        for (Value v : shape) {
            int size = (int) v.asDouble();
            if (size < 0) {
                int shapeSize = (int) shape.stream().mapToDouble(Value::asDouble).reduce(1, (a, b) -> a * b);
                int tensorSize = OrderedTensorType.tensorSize(inputType.type()).intValue();
                size = -1 * shapeSize / tensorSize;
            }
            dimSizes.add(size);
        }
        return buildOutputType(dimSizes);
    }

    private OrderedTensorType buildOutputType(List<Integer> dimSizes) {
        OrderedTensorType.Builder outputTypeBuilder = new OrderedTensorType.Builder(resultValueType());
        for (int i = 0; i < dimSizes.size(); ++i) {
            outputTypeBuilder.add(TensorType.Dimension.indexed(String.format("%s_%d", vespaName(), i), dimSizes.get(i)));
        }
        return outputTypeBuilder.build();
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if ( ! inputs.stream().map(IntermediateOperation::type).allMatch(Optional::isPresent) ) return null;
        if ( ! inputs.stream().map(IntermediateOperation::function).allMatch(Optional::isPresent) ) return null;

        OrderedTensorType inputType = inputs.get(0).type().get();
        TensorFunction<Reference> inputFunction = inputs.get(0).function().get();
        return reshape(inputFunction, inputType, type);
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        addConstraintsFrom(type, renamer);
    }

    @Override
    public Reshape withInputs(List<IntermediateOperation> inputs) {
        return new Reshape(modelName(), name(), inputs, attributeMap);
    }

    public TensorFunction<Reference> reshape(TensorFunction<Reference> inputFunction, OrderedTensorType inputType, OrderedTensorType outputType) {
        if ( ! OrderedTensorType.tensorSize(inputType.type()).equals(OrderedTensorType.tensorSize(outputType.type())))
            throw new IllegalArgumentException("New and old shape of tensor must have the same size when reshaping");

        IntermediateOperation input = inputs.get(0);
        String inputFunctionName = input.rankingExpressionFunctionName();

        List<com.yahoo.tensor.functions.Slice.DimensionValue<Reference>> dimensionValues = new ArrayList<>();

        // Conceptually, reshaping consists on unrolling a tensor to an array using the dimension order,
        // then use the dimension order of the new shape to roll back into a tensor.

        ExpressionNode unrolled = new EmbracedNode(unrollTensorExpression(outputType));

        long innerSize = 1;
        for (int dim = 0; dim < inputType.rank(); ++dim) {
            innerSize *= inputType.dimensions().get(dim).size().get();
        }

        for (int dim = 0; dim < inputType.rank(); ++dim) {
            String inputDimensionName = inputType.dimensions().get(dim).name();
            long inputDimensionSize = inputType.dimensions().get(dim).size().get();
            long previousInnerSize = innerSize;
            innerSize /= inputDimensionSize;

            ExpressionNode inputDimensionExpression;
            if (inputDimensionSize == 1) {
                inputDimensionExpression = new EmbracedNode(new ConstantNode(DoubleValue.zero));
            } else if (dim == (inputType.rank() - 1)) {
                ExpressionNode size = new ConstantNode(new DoubleValue(inputDimensionSize));
                ExpressionNode div = new OperationNode(unrolled, Operator.modulo, size);
                inputDimensionExpression = new EmbracedNode(div);
            } else {
                ExpressionNode size = new ConstantNode(new DoubleValue(innerSize));
                ExpressionNode previousSize = new ConstantNode(new DoubleValue(previousInnerSize));
                ExpressionNode mod = new OperationNode(unrolled, Operator.modulo, previousSize);
                ExpressionNode div = new OperationNode(new EmbracedNode(mod), Operator.divide, size);
                inputDimensionExpression = new EmbracedNode(div);
            }
            dimensionValues.add(new com.yahoo.tensor.functions.Slice.DimensionValue<>(Optional.of(inputDimensionName), wrapScalar(inputDimensionExpression)));
        }

        TensorFunction<Reference> inputIndices = new TensorFunctionNode.ExpressionTensorFunction(new ReferenceNode(inputFunctionName));
        com.yahoo.tensor.functions.Slice<Reference> sliceIndices = new com.yahoo.tensor.functions.Slice<>(inputIndices, dimensionValues);
        ExpressionNode sliceExpression = new TensorFunctionNode(sliceIndices);

        return Generate.bound(outputType.type(), wrapScalar(sliceExpression));
    }

    private static ExpressionNode unrollTensorExpression(OrderedTensorType type) {
        if (type.rank() == 0)
            return new ConstantNode(DoubleValue.zero);

        List<ExpressionNode> children = new ArrayList<>();
        List<Operator> operators = new ArrayList<>();
        int size = 1;
        for (int i = type.dimensions().size() - 1; i >= 0; --i) {
            TensorType.Dimension dimension = type.dimensions().get(i);
            children.add(0, new ReferenceNode(dimension.name()));
            if (size > 1) {
                operators.add(0, Operator.multiply);
                children.add(0, new ConstantNode(new DoubleValue(size)));
            }
            size *= OrderedTensorType.dimensionSize(dimension);
            if (i > 0) {
                operators.add(0, Operator.plus);
            }
        }
        return new OperationNode(children, operators);
    }

    @Override
    public String operationName() { return "Reshape"; }

}
