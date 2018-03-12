// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Collections;
import java.util.List;

/**
 * A function which returns a constant tensor.
 *
 * @author bratseth
 */
public class ConstantTensor extends PrimitiveTensorFunction {

    private final Tensor constant;

    public ConstantTensor(String tensorString) {
        this.constant = Tensor.from(tensorString);
    }

    public ConstantTensor(Tensor tensor) {
        this.constant = tensor;
    }

    @Override
    public List<TensorFunction> arguments() { return Collections.emptyList(); }

    @Override
    public TensorFunction withArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("ConstantTensor must have 0 arguments, got " + arguments.size());
        return this;
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() { return this; }

    @Override
    public <NAMETYPE extends TypeContext.Name> TensorType type(TypeContext<NAMETYPE> context) { return constant.type(); }

    @Override
    public <NAMETYPE extends TypeContext.Name> Tensor evaluate(EvaluationContext<NAMETYPE> context) { return constant; }

    @Override
    public String toString(ToStringContext context) { return constant.toString(); }

}
