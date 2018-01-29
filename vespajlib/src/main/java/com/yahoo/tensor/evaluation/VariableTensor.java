// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.evaluation;

import com.google.common.annotations.Beta;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.PrimitiveTensorFunction;
import com.yahoo.tensor.functions.TensorFunction;
import com.yahoo.tensor.functions.ToStringContext;

import java.util.Collections;
import java.util.List;

/**
 * A tensor variable name which resolves to a tensor in the context at evaluation time
 *
 * @author bratseth
 */
@Beta
public class VariableTensor extends PrimitiveTensorFunction {

    private final String name;

    public VariableTensor(String name) {
        this.name = name;
    }

    @Override
    public List<TensorFunction> arguments() { return Collections.emptyList(); }

    @Override
    public TensorFunction withArguments(List<TensorFunction> arguments) { return this; }

    @Override
    public PrimitiveTensorFunction toPrimitive() { return this; }

    @Override
    public TensorType type(EvaluationContext context) { return context.getTensorType(name); }

    @Override
    public Tensor evaluate(EvaluationContext context) {
        return context.getTensor(name);
    }

    @Override
    public String toString(ToStringContext context) {
        return name;
    }

}
