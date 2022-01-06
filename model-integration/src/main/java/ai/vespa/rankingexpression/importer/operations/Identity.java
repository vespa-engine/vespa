// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;

public class Identity extends IntermediateOperation {

    public Identity(String modelName, String nodeName, List<IntermediateOperation> inputs) {
        super(modelName, nodeName, inputs);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(1))
            return null;
        return inputs.get(0).type().orElse(null);
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if (!allInputFunctionsPresent(1))
            return null;
        return inputs.get(0).function().orElse(null);
    }

    @Override
    public Identity withInputs(List<IntermediateOperation> inputs) {
        return new Identity(modelName(), name(), inputs);
    }

    @Override
    public String operationName() { return "Identity"; }

}
