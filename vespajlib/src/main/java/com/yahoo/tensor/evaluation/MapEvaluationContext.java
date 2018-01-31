// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.evaluation;

import com.google.common.annotations.Beta;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.HashMap;

/**
 * @author bratseth
 */
@Beta
public class MapEvaluationContext implements EvaluationContext {

    private final java.util.Map<String, Tensor> bindings = new HashMap<>();

    static MapEvaluationContext empty() { return new MapEvaluationContext(); }

    public void put(String name, Tensor tensor) { bindings.put(name, tensor); }

    @Override
    public TensorType getType(String name) {
        Tensor tensor = bindings.get(name);
        if (tensor == null) return null;
        return tensor.type();
    }

    @Override
    public Tensor getTensor(String name) { return bindings.get(name); }

}
