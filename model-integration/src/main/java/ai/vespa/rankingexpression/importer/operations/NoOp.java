// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.Collections;
import java.util.List;

public class NoOp extends IntermediateOperation {

    public NoOp(String modelName, String nodeName, List<IntermediateOperation> inputs) {
        super(modelName, nodeName, Collections.emptyList());  // don't propagate inputs
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        return null;
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        return null;
    }

    @Override
    public NoOp withInputs(List<IntermediateOperation> inputs) {
        return new NoOp(modelName(), name(), inputs);
    }

    @Override
    public String operationName() { return "NoOp"; }

}
