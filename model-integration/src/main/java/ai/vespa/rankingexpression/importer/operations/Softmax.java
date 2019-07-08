// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;

/**
 * Convert imported 'softmax' operation to the Vespa softmax ranking function.
 *
 * @author lesters
 */
public class Softmax extends IntermediateOperation {

    public Softmax(String modelName, String nodeName, List<IntermediateOperation> inputs) {
        super(modelName, nodeName, inputs);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! allInputTypesPresent(1)) return null;
        return inputs.get(0).type().get();
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        if ( ! allInputFunctionsPresent(1)) return null;

        OrderedTensorType inputType = inputs.get(0).type().get();
        String dimension = inputType.dimensions().get(0).name();
        if (inputType.rank() == 2) {
            dimension = inputType.dimensions().get(1).name(); // assumption: first dimension is batch dimension
        }

        TensorFunction inputFunction = inputs.get(0).function().get();
        return new com.yahoo.tensor.functions.Softmax(inputFunction, dimension);
    }

    @Override
    public Softmax withInputs(List<IntermediateOperation> inputs) {
        return new Softmax(modelName(), name(), inputs);
    }

    @Override
    public String operationName() { return "SoftMax"; }

}
