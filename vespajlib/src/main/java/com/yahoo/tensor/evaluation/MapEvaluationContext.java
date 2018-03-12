// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.evaluation;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.HashMap;

/**
 * @author bratseth
 */
public class MapEvaluationContext implements EvaluationContext<TypeContext.Name> {

    private final java.util.Map<String, Tensor> bindings = new HashMap<>();

    public void put(String name, Tensor tensor) { bindings.put(name, tensor); }

    @Override
    public TensorType getType(String name) {
        return getType(new Name(name));
    }

    @Override
    public TensorType getType(Name name) {
        Tensor tensor = bindings.get(name.toString());
        if (tensor == null) return null;
        return tensor.type();
    }

    @Override
    public Tensor getTensor(String name) { return bindings.get(name); }

}
