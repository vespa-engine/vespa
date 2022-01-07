// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
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
import java.util.Set;
import java.util.stream.Collectors;

public class Unsqueeze extends IntermediateOperation {

    private final AttributeMap attributeMap;
    private List<String> expandDimensions;

    public Unsqueeze(String modelName, String nodeName, List<IntermediateOperation> inputs, AttributeMap attributeMap) {
        super(modelName, nodeName, inputs);
        this.attributeMap = attributeMap;
        if (attributeMap.getList("axes").isEmpty()) {
            throw new IllegalArgumentException("Unsqueeze in " + name + ": Required attribute 'axes' is missing.");
        }
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! allInputTypesPresent(1)) return null;

        OrderedTensorType inputType = inputs.get(0).type().get();
        Set<Integer> dimensionsToInsert = attributeMap.getList("axes").get().stream().
                map(d -> (int)d.asDouble()).collect(Collectors.toSet());

        // handle negative dimension indexes
        int rank = inputType.rank() + dimensionsToInsert.size();
        dimensionsToInsert = dimensionsToInsert.stream().map(d -> d < 0 ? rank + d : d).collect(Collectors.toSet());

        expandDimensions = new ArrayList<>();
        OrderedTensorType.Builder typeBuilder = new OrderedTensorType.Builder(resultValueType());
        int inputDimensionIndex = 0;
        for (int expandedDimensionIndex = 0; expandedDimensionIndex < rank; ++expandedDimensionIndex) {
            if (dimensionsToInsert.contains(expandedDimensionIndex)) {
                addDimension(expandedDimensionIndex, typeBuilder);
            } else {
                typeBuilder.add(inputType.dimensions().get(inputDimensionIndex));
                inputDimensionIndex++;
            }
        }
        return typeBuilder.build();
    }

    private void addDimension(int dimensionIndex, OrderedTensorType.Builder typeBuilder) {
        String name = String.format("%s_%d", vespaName(), dimensionIndex);
        expandDimensions.add(name);
        typeBuilder.add(TensorType.Dimension.indexed(name, 1L));
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if ( ! allInputFunctionsPresent(1)) return null;

        // multiply with a generated tensor created from the expanded dimensions
        TensorType.Builder typeBuilder = new TensorType.Builder(resultValueType());
        for (String name : expandDimensions) {
            typeBuilder.indexed(name, 1);
        }
        TensorType generatedType = typeBuilder.build();
        ExpressionNode generatedExpression = new ConstantNode(new DoubleValue(1));
        Generate<Reference> generatedFunction = new Generate<>(generatedType,
                new GeneratorLambdaFunctionNode(generatedType, generatedExpression).asLongListToDoubleOperator());
        return new com.yahoo.tensor.functions.Join<>(inputs().get(0).function().get(), generatedFunction, ScalarFunctions.multiply());
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        addConstraintsFrom(type, renamer);
    }

    @Override
    public void renameDimensions(DimensionRenamer renamer) {
        super.renameDimensions(renamer);
        List<String> renamedDimensions = new ArrayList<>(expandDimensions.size());
        for (String name : expandDimensions) {
            Optional<String> newName = renamer.dimensionNameOf(name);
            if (newName.isEmpty()) {
                return;  // presumably, already renamed
            }
            renamedDimensions.add(newName.get());
        }
        expandDimensions = renamedDimensions;
    }

    @Override
    public Unsqueeze withInputs(List<IntermediateOperation> inputs) {
        return new Unsqueeze(modelName(), name(), inputs, attributeMap);
    }

    @Override
    public String operationName() { return "Unsqueeze"; }

}
