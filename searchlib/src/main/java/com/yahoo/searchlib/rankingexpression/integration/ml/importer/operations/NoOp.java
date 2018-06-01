// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.Collections;
import java.util.List;

public class NoOp extends IntermediateOperation {

    public NoOp(String name, List<IntermediateOperation> inputs) {
        super(name, Collections.emptyList());  // don't propagate inputs
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        return null;
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        return null;
    }

}
