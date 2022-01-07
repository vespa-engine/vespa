// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;

public class Map extends IntermediateOperation {

    private final DoubleUnaryOperator operator;

    public Map(String modelName, String nodeName, List<IntermediateOperation> inputs, DoubleUnaryOperator operator) {
        super(modelName, nodeName, inputs);
        this.operator = operator;
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(1)) {
            return null;
        }
        return inputs.get(0).type().get();
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if (!allInputFunctionsPresent(1)) {
            return null;
        }
        Optional<TensorFunction<Reference>> input = inputs.get(0).function();
        return new com.yahoo.tensor.functions.Map<>(input.get(), operator);
    }

    @Override
    public Map withInputs(List<IntermediateOperation> inputs) {
        return new Map(modelName(), name(), inputs, operator);
    }

    @Override
    public String operationName() { return "Map"; }

}
