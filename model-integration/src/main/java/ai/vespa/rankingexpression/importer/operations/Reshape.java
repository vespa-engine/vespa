// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import ai.vespa.rankingexpression.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.ArithmeticNode;
import com.yahoo.searchlib.rankingexpression.rule.ArithmeticOperator;
import com.yahoo.searchlib.rankingexpression.rule.ComparisonNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.EmbracedNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.GeneratorLambdaFunctionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TruthOperator;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Reshape extends IntermediateOperation {

    private final AttributeMap attributeMap;

    public Reshape(String modelName, String nodeName, List<IntermediateOperation> inputs, AttributeMap attributeMap) {
        super(modelName, nodeName, inputs);
        this.attributeMap = attributeMap;
    }

    @Override
    protected OrderedTensorType lazyGetType() {
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

        // first pass - set 0 values
        for (int i = 0; i < dimSizes.size(); ++i) {
            if (dimSizes.get(i) == 0) {
                if (i >= inputType.dimensions().size()) {
                    throw new IllegalArgumentException("Reshape " + name + ": 0 value for dimension not found in input");
                }
                dimSizes.set(i, inputType.dimensions().get(i).size().get().intValue());
            }
        }

        // second pass - set any -1 values
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
    protected TensorFunction lazyGetFunction() {
        if ( ! inputs.stream().map(IntermediateOperation::type).allMatch(Optional::isPresent) ) return null;
        if ( ! inputs.stream().map(IntermediateOperation::function).allMatch(Optional::isPresent) ) return null;

        OrderedTensorType inputType = inputs.get(0).type().get();
        TensorFunction inputFunction = inputs.get(0).function().get();
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

    public static TensorFunction reshape(TensorFunction inputFunction, OrderedTensorType inputType, OrderedTensorType outputType) {
        if ( ! OrderedTensorType.tensorSize(inputType.type()).equals(OrderedTensorType.tensorSize(outputType.type())))
            throw new IllegalArgumentException("New and old shape of tensor must have the same size when reshaping");

        // Conceptually, reshaping consists on unrolling a tensor to an array using the dimension order,
        // then use the dimension order of the new shape to roll back into a tensor.
        // Here we create a transformation tensor that is multiplied with the from tensor to map into
        // the new shape. We have to introduce temporary dimension names and rename back if dimension names
        // in the new and old tensor type overlap.

        // Todo: change this to use tensor generate when available

        List<String> from = new ArrayList<>();
        List<String> to = new ArrayList<>();
        boolean dimensionNamesOverlap = dimensionNamesOverlap(inputType, outputType);
        if (dimensionNamesOverlap) {
            OrderedTensorType.Builder builder = new OrderedTensorType.Builder(outputType.type().valueType());
            for (int i = 0; i < outputType.rank(); ++i) {
                TensorType.Dimension dim = outputType.dimensions().get(i);
                from.add(dim.name());
                to.add("temp_" + dim.name());
                builder.add(dim.withName("temp_" + dim.name()));
            }
            outputType = builder.build();
        }

        ExpressionNode unrollFrom = unrollTensorExpression(inputType);
        ExpressionNode unrollTo = unrollTensorExpression(outputType);
        ExpressionNode transformExpression = new ComparisonNode(new EmbracedNode(unrollFrom), TruthOperator.EQUAL, new EmbracedNode(unrollTo));

        TensorType transformationType = new TensorType.Builder(inputType.type(), outputType.type()).build();
        Generate transformTensor = new Generate(transformationType,
                                                new GeneratorLambdaFunctionNode(transformationType, transformExpression).asLongListToDoubleOperator());

        TensorFunction result = new Reduce(new com.yahoo.tensor.functions.Join(inputFunction, transformTensor, ScalarFunctions.multiply()),
                          Reduce.Aggregator.sum,
                          inputType.dimensions().stream().map(TensorType.Dimension::name).collect(Collectors.toList()));

        if (dimensionNamesOverlap) {
            result = new Rename(result, to, from);
        }
        return result;
    }

    private static boolean dimensionNamesOverlap(OrderedTensorType a, OrderedTensorType b) {
        return a.dimensionNames().stream().anyMatch(d -> b.type().indexOfDimension(d).isPresent());
    }

    private static ExpressionNode unrollTensorExpression(OrderedTensorType type) {
        if (type.rank() == 0)
            return new ConstantNode(DoubleValue.zero);

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

    @Override
    public String operationName() { return "Reshape"; }

}
