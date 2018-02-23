// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.framework.NodeDef;

import java.util.List;
import java.util.Optional;
import java.util.function.DoubleBinaryOperator;

public class Join extends TensorFlowOperation {

    private final DoubleBinaryOperator operator;

    public Join(NodeDef node, List<TensorFlowOperation> inputs, int port, DoubleBinaryOperator operator) {
        super(node, inputs, port);
        this.operator = operator;
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(2)) {
            return null;
        }
        OrderedTensorType a = inputs.get(0).type().get();
        OrderedTensorType b = inputs.get(1).type().get();
        OrderedTensorType out = a.type().rank() >= b.type().rank() ? a : b;
        return out;
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        if (!allInputTypesPresent(2)) {
            return null;
        }
        Optional<TensorFunction> aFunction = inputs.get(0).function();
        Optional<TensorFunction> bFunction = inputs.get(1).function();
        if (!aFunction.isPresent() || !bFunction.isPresent()) {
            return null;
        }

        // The dimension renaming below takes care of broadcasting.

        return new com.yahoo.tensor.functions.Join(aFunction.get(), bFunction.get(), operator);
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        if (!allInputTypesPresent(2)) {
            return;
        }

        // Well now we have potentially entered the wonderful world of "broadcasting"
        // https://docs.scipy.org/doc/numpy/user/basics.broadcasting.html
        // I'm not able to extract from that any unambiguous specification of which dimensions
        // should be "stretched" when the tensor do not have the same number of dimensions.
        // From trying this with TensorFlow it appears that the second tensor is matched to the
        // "end" (highest numbered) dimensions of the first, but I'm not sure whether this is generally true.
        // Anyway, we move the dimensions of b to the last dimensions of a (instead of by default, the first).

        TensorType a = inputs.get(0).type().get().type();
        TensorType b = inputs.get(1).type().get().type();
        if (a.rank() < b.rank()) {
            TensorType temp = a;
            a = b;
            b = temp;
        }
        int sizeDifference = a.rank() - b.rank();
        for (int i = 0; i < b.rank(); ++i) {
            String bDim = b.dimensions().get(i).name();
            String aDim = a.dimensions().get(i + sizeDifference).name();
            renamer.addConstraint(aDim, bDim, DimensionRenamer::equals, this);
        }
    }

}
