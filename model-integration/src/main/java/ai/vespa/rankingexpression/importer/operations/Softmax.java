// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Map;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.Collections;
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
        insert(new SoftmaxPartialOperation(modelName, nodeName, null), 0); // inputs are fixed in insert
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! allInputTypesPresent(1)) return null;
        return inputs.get(0).type().get();
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if ( ! allInputFunctionsPresent(1)) return null;
        List<String> reduceDimensions = reduceDimensions();
        TensorFunction<Reference> input = inputs.get(0).function().get();
        TensorFunction<Reference> sum = new Reduce<>(input, Reduce.Aggregator.sum, reduceDimensions);
        TensorFunction<Reference> div = new Join<>(input, sum, ScalarFunctions.divide());
        return div;
    }

    @Override
    public Softmax withInputs(List<IntermediateOperation> inputs) {
        return new Softmax(modelName(), name(), inputs, attributeMap);
    }

    @Override
    public String operationName() { return "SoftMax"; }

    private List<String> reduceDimensions() {
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
        return reduceDimensions;
    }

    /*
     * Operation to insert between input and this softmax to avoid double calculation
     * Note that this partial operation should be removed when we have a specific
     * softmax optimization in the backend, as this way of splitting the calculation
     * makes the full softmax expression impossible to recognize.
     */
    private class SoftmaxPartialOperation extends IntermediateOperation {

        private SoftmaxPartialOperation(String modelName, String nodeName, List<IntermediateOperation> inputs) {
            super(modelName, nodeName + "_partial" , inputs != null ? inputs : Collections.emptyList());
        }

        @Override
        protected OrderedTensorType lazyGetType() {
            if ( ! allInputTypesPresent(1)) return null;

            // input is referenced twice due to overflow avoidance, so make sure it is exported as a ranking function
            inputs.get(0).exportAsRankingFunction = true;

            // this should also be it's own function since we use it twice
            exportAsRankingFunction = true;

            return inputs.get(0).type().get();
        }

        @Override
        protected TensorFunction<Reference> lazyGetFunction() {
            if ( ! allInputFunctionsPresent(1)) return null;
            List<String> reduceDimensions = reduceDimensions();
            TensorFunction<Reference> input = inputs.get(0).function().get();
            TensorFunction<Reference> max = new Reduce<>(input, Reduce.Aggregator.max, reduceDimensions);
            TensorFunction<Reference> cap = new Join<>(input, max, ScalarFunctions.subtract());  // to avoid overflow
            TensorFunction<Reference> exp = new Map<>(cap, ScalarFunctions.exp());
            return exp;
        }

        @Override
        public SoftmaxPartialOperation withInputs(List<IntermediateOperation> inputs) {
            return new SoftmaxPartialOperation(modelName(), name(), inputs);
        }

        @Override
        public String operationName() { return "SoftMaxPartial"; }

    }

}
