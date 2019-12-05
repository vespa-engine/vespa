// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Map;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * Convert imported 'softmax' operation to the Vespa softmax ranking function.
 *
 * @author lesters
 */
public class Softmax extends IntermediateOperation {

    private final AttributeMap attributeMap;

    public Softmax(String modelName, String nodeName, List<IntermediateOperation> inputs, AttributeMap attributeMap) {
        super(modelName, nodeName, inputs);
        this.attributeMap = attributeMap;
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

        int axis = inputType.rank() == 1 ? 0 : 1;  // assumption: first dimension is batch dimension
        if (attributeMap.get("axis").isPresent()) {
            axis = (int)attributeMap.get("axis").get().asDouble();
        }
        if (axis < 0) {
            axis = inputType.rank() + axis;
        }
        List<String> reduceDimensions = new ArrayList<>();
        for (int i = axis; i < inputType.rank(); ++i) {
            reduceDimensions.add(inputType.dimensions().get(i).name());  // Do softmax over all dimensions except batch dimension
        }

        TensorFunction input = inputs.get(0).function().get();
        TensorFunction exp = new Map(input, ScalarFunctions.exp());
        TensorFunction sum = new Reduce(exp, Reduce.Aggregator.sum, reduceDimensions);
        TensorFunction div = new Join(exp, sum, ScalarFunctions.divide());

        return div;
    }

    @Override
    public Softmax withInputs(List<IntermediateOperation> inputs) {
        return new Softmax(modelName(), name(), inputs, attributeMap);
    }

    @Override
    public String operationName() { return "SoftMax"; }

}
