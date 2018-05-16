// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.onnx.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.OrderedTensorType;
import com.yahoo.tensor.functions.TensorFunction;
import onnx.Onnx;

import java.util.Collections;
import java.util.List;

public class NoOp extends OnnxOperation {

    public NoOp(Onnx.NodeProto node, List<OnnxOperation> inputs) {
        super(node, Collections.emptyList());  // don't propagate inputs
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        return null;
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        return null;
    }

    @Override
    public boolean isConstant() {
        return true;
    }

}
