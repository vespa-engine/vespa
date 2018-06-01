// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;
import java.util.Optional;

public class PlaceholderWithDefault extends IntermediateOperation {

    public PlaceholderWithDefault(String modelName, String nodeName, List<IntermediateOperation> inputs) {
        super(modelName, nodeName, inputs);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if (!allInputTypesPresent(1)) {
            return null;
        }
        return inputs().get(0).type().orElse(null);
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        if (!allInputFunctionsPresent(1)) {
            return null;
        }
        // This should be a call to the macro we add below, but for now
        // we treat this as as identity function and just pass the constant.
        return inputs.get(0).function().orElse(null);
    }

    @Override
    public Optional<TensorFunction> macro() {
        // For now, it is much more efficient to assume we always will return
        // the default value, as we can prune away large parts of the expression
        // tree by having it calculated as a constant. If a case arises where
        // it is important to support this, implement this.
        return Optional.empty();
    }

    @Override
    public boolean isConstant() {
        return true;  // not true if we add to macro
    }

}
