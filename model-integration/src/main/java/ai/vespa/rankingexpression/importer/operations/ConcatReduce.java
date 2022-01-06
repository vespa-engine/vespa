// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;

public class ConcatReduce extends IntermediateOperation {

    private final static String tmpDimensionName = "__concat_reduce_tmp_dimension_name__";
    private final Reduce.Aggregator aggregator;

    public ConcatReduce(String modelName, String nodeName, List<IntermediateOperation> inputs, Reduce.Aggregator aggregator) {
        super(modelName, nodeName, inputs);
        this.aggregator = aggregator;
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! allInputTypesPresent(inputs.size())) return null;
        return inputs.get(0).type().get();
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if ( ! allInputFunctionsPresent(inputs.size())) return null;

        TensorFunction<Reference> result = inputs.get(0).function().get();
        for (int i = 1; i < inputs.size(); ++i) {
            TensorFunction<Reference> b = inputs.get(i).function().get();
            result = new com.yahoo.tensor.functions.Concat<>(result, b, tmpDimensionName);
        }
        return new com.yahoo.tensor.functions.Reduce<>(result, aggregator, tmpDimensionName);
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        if ( ! allInputTypesPresent(inputs.size())) return;

        OrderedTensorType a = inputs.get(0).type().get();
        for (int i = 1; i < inputs.size(); ++i) {
            OrderedTensorType b = inputs.get(i).type().get();

            OrderedTensorType largest = largestInput(a, b);
            OrderedTensorType smallest = smallestInput(a, b);

            int sizeDifference = largest.rank() - smallest.rank();
            for (int j = 0; j < smallest.rank(); ++j) {
                String bDim = smallest.dimensions().get(j).name();
                String aDim = largest.dimensions().get(j + sizeDifference).name();
                renamer.addConstraint(aDim, bDim, DimensionRenamer.Constraint.equal(false), this);
            }
            a = b;
        }
    }

    private OrderedTensorType largestInput(OrderedTensorType a, OrderedTensorType b) {
        return a.rank() >= b.rank() ? a : b;
    }

    private OrderedTensorType smallestInput(OrderedTensorType a, OrderedTensorType b) {
        return a.rank() < b.rank() ? a : b;
    }

    @Override
    public ConcatReduce withInputs(List<IntermediateOperation> inputs) {
        return new ConcatReduce(modelName(), name(), inputs, aggregator);
    }

    @Override
    public String operationName() { return "ConcatReduce"; }

}
