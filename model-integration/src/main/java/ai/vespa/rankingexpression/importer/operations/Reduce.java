// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.GeneratorLambdaFunctionNode;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;

/**
 * ONNX Reduce[Sum/Mean/etc] operation
 */
public class Reduce extends IntermediateOperation {

    private final AttributeMap attributeMap;
    private final com.yahoo.tensor.functions.Reduce.Aggregator aggregator;
    private final DoubleUnaryOperator preOperator;
    private final DoubleUnaryOperator postOperator;

    private List<String> reduceDimensions;

    public Reduce(String modelName, String nodeName,
                  List<IntermediateOperation> inputs,
                  AttributeMap attributeMap,
                  com.yahoo.tensor.functions.Reduce.Aggregator aggregator) {
        this(modelName, nodeName, inputs, attributeMap, aggregator, null, null);
    }

    public Reduce(String modelName, String nodeName,
                  List<IntermediateOperation> inputs,
                  AttributeMap attributeMap,
                  com.yahoo.tensor.functions.Reduce.Aggregator aggregator,
                  DoubleUnaryOperator preOperator,
                  DoubleUnaryOperator postOperator) {
        super(modelName, nodeName, inputs);
        this.attributeMap = attributeMap;
        this.aggregator = aggregator;
        this.preOperator = preOperator;
        this.postOperator = postOperator;
    }


    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! allInputTypesPresent(1)) return null;

        OrderedTensorType inputType = inputs.get(0).type().get();

        reduceDimensions = inputType.dimensionNames();  // default is to reduce all dimensions
        if (attributeMap.getList("axes").isPresent()) {
            reduceDimensions = new ArrayList<>();
            for (Value i : attributeMap.getList("axes").get()) {
                int dimensionIndex = (int) i.asDouble();
                if (dimensionIndex < 0) {
                    dimensionIndex = inputType.dimensions().size() + dimensionIndex;
                }
                reduceDimensions.add(inputType.dimensions().get(dimensionIndex).name());
            }
        }
        return reducedType(inputType, shouldKeepDimensions());
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if ( ! allInputTypesPresent(1)) return null;

        TensorFunction<Reference> inputFunction = inputs.get(0).function().get();
        if (preOperator != null) {
            inputFunction = new com.yahoo.tensor.functions.Map<>(inputFunction, preOperator);
        }
        TensorFunction<Reference> output = new com.yahoo.tensor.functions.Reduce<>(inputFunction, aggregator, reduceDimensions);
        if (shouldKeepDimensions()) {
            // multiply with a generated tensor created from the reduced dimensions
            TensorType.Builder typeBuilder = new TensorType.Builder(resultValueType());
            for (String name : reduceDimensions) {
                typeBuilder.indexed(name, 1);
            }
            TensorType generatedType = typeBuilder.build();
            ExpressionNode generatedExpression = new ConstantNode(new DoubleValue(1));
            Generate<Reference> generatedFunction = new Generate<>(generatedType,
                    new GeneratorLambdaFunctionNode(generatedType, generatedExpression).asLongListToDoubleOperator());
            output = new com.yahoo.tensor.functions.Join<>(output, generatedFunction, ScalarFunctions.multiply());
        }
        if (postOperator != null) {
            output = new com.yahoo.tensor.functions.Map<>(output, postOperator);
        }
        return output;
    }

    @Override
    public void renameDimensions(DimensionRenamer renamer) {
        super.renameDimensions(renamer);
        List<String> renamedDimensions = new ArrayList<>(reduceDimensions.size());
        for (String name : reduceDimensions) {
            Optional<String> newName = renamer.dimensionNameOf(name);
            if (newName.isEmpty()) {
                return;  // presumably, already renamed
            }
            renamedDimensions.add(newName.get());
        }
        reduceDimensions = renamedDimensions;
    }

    @Override
    public Reduce withInputs(List<IntermediateOperation> inputs) {
        return new Reduce(modelName(), name(), inputs, attributeMap, aggregator, preOperator, postOperator);
    }

    @Override
    public String operationName() { return "Reduce"; }

    private boolean shouldKeepDimensions() {
        Optional<Value> keepDims = attributeMap.get("keepdims");
        return keepDims.isEmpty() || keepDims.get().asBoolean();  // default is 1
    }

    private OrderedTensorType reducedType(OrderedTensorType inputType, boolean keepDimensions) {
        OrderedTensorType.Builder builder = new OrderedTensorType.Builder(resultValueType());
        for (TensorType.Dimension dimension: inputType.type().dimensions()) {
            if ( ! reduceDimensions.contains(dimension.name())) {
                builder.add(dimension);
            } else if (keepDimensions) {
                builder.add(TensorType.Dimension.indexed(dimension.name(), 1L));
            }
        }
        return builder.build();
    }



}
