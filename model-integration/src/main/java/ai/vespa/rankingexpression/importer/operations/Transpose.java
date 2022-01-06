// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;

public class Transpose extends IntermediateOperation {

    private final AttributeMap attributes;

    public Transpose(String modelName, String nodeName, List<IntermediateOperation> inputs, AttributeMap attributes) {
        super(modelName, nodeName, inputs);
        this.attributes = attributes;
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(1)) return null;

        OrderedTensorType inputType = inputs.get(0).type().get();

        OrderedTensorType.Builder typeBuilder = new OrderedTensorType.Builder(resultValueType());
        for (int i = 0; i < inputType.rank(); ++i) {
            int inputIndex = inputType.rank() - 1 - i;
            if (attributes.getList("perm").isPresent()) {
                inputIndex = (int) attributes.getList("perm").get().get(i).asDouble();
            }
            TensorType.Dimension inputDimension = inputType.dimensions().get(inputIndex);
            typeBuilder.add(TensorType.Dimension.indexed(inputDimension.name(), inputDimension.size().get()));
        }
        OrderedTensorType result = typeBuilder.build();
        return typeBuilder.build();
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if (!allInputFunctionsPresent(1))
            return null;
        return inputs.get(0).function().orElse(null);
    }

    @Override
    public Transpose withInputs(List<IntermediateOperation> inputs) {
        return new Transpose(modelName(), name(), inputs, attributes);
    }

    @Override
    public String operationName() { return "Transpose"; }

}
