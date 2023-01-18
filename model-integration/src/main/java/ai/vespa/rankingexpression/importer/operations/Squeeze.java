// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Squeeze extends IntermediateOperation {

    private final AttributeMap attributeMap;
    private List<String> squeezeDimensions;

    public Squeeze(String modelName, String nodeName, List<IntermediateOperation> inputs, AttributeMap attributeMap) {
        super(modelName, nodeName, inputs);
        this.attributeMap = attributeMap;
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! allInputTypesPresent(1)) return null;

        OrderedTensorType inputType = inputs.get(0).type().get();
        squeezeDimensions = new ArrayList<>();

        Optional<List<Value>> squeezeDimsAttr = attributeMap.getList("squeeze_dims");
        if (squeezeDimsAttr.isEmpty()) {
            squeezeDimsAttr = attributeMap.getList("axes");  // ONNX
        }
        if (squeezeDimsAttr.isEmpty()) {
            squeezeDimensions = inputType.type().dimensions().stream().
                    filter(dim -> OrderedTensorType.dimensionSize(dim) == 1).
                    map(TensorType.Dimension::name).
                    toList();
        } else {
            squeezeDimensions = squeezeDimsAttr.get().stream().map(Value::asDouble).map(Double::intValue).
                    map(i -> i < 0 ? inputType.type().dimensions().size() - i : i).
                    map(i -> inputType.type().dimensions().get(i)).
                    filter(dim -> OrderedTensorType.dimensionSize(dim) == 1).
                    map(TensorType.Dimension::name).
                    toList();
        }

        return squeezeDimensions.isEmpty() ? inputType : reducedType(inputType);
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if ( ! allInputFunctionsPresent(1)) return null;

        TensorFunction<Reference> inputFunction = inputs.get(0).function().get();
        return new Reduce<>(inputFunction, Reduce.Aggregator.sum, squeezeDimensions);
    }

    @Override
    public void renameDimensions(DimensionRenamer renamer) {
        super.renameDimensions(renamer);
        List<String> renamedDimensions = new ArrayList<>(squeezeDimensions.size());
        for (String name : squeezeDimensions) {
            Optional<String> newName = renamer.dimensionNameOf(name);
            if (newName.isEmpty()) {
                return;  // presumably, already renamed
            }
            renamedDimensions.add(newName.get());
        }
        squeezeDimensions = renamedDimensions;
    }

    @Override
    public Squeeze withInputs(List<IntermediateOperation> inputs) {
        return new Squeeze(modelName(), name(), inputs, attributeMap);
    }

    private OrderedTensorType reducedType(OrderedTensorType inputType) {
        OrderedTensorType.Builder builder = new OrderedTensorType.Builder(resultValueType());
        for (TensorType.Dimension dimension: inputType.dimensions()) {
            if ( ! squeezeDimensions.contains(dimension.name())) {
                builder.add(dimension);
            }
        }
        return builder.build();
    }

    @Override
    public String operationName() { return "Squeeze"; }

}
