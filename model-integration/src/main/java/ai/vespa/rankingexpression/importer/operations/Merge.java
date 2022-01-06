// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;

public class Merge extends IntermediateOperation {

    public Merge(String modelName, String nodeName, List<IntermediateOperation> inputs) {
        super(modelName, nodeName, inputs);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        for (IntermediateOperation operation : inputs) {
            if (operation.type().isPresent()) {
                return operation.type().get();
            }
        }
        return null;
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        for (IntermediateOperation operation : inputs) {
            if (operation.function().isPresent()) {
                return operation.function().get();
            }
        }
        return null;
    }

    @Override
    public Merge withInputs(List<IntermediateOperation> inputs) {
        return new Merge(modelName(), name(), inputs);
    }

    @Override
    public String operationName() { return "Merge"; }

}
