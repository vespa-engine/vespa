// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A function which returns a constant tensor.
 *
 * @author bratseth
 */
public class ConstantTensor<NAMETYPE extends Name> extends PrimitiveTensorFunction<NAMETYPE> {

    private final Tensor constant;

    public ConstantTensor(String tensorString) {
        this.constant = Tensor.from(tensorString);
    }

    public ConstantTensor(Tensor tensor) {
        this.constant = tensor;
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return Collections.emptyList(); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("ConstantTensor must have 0 arguments, got " + arguments.size());
        return this;
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() { return this; }

    @Override
    public TensorType type(TypeContext<NAMETYPE> context) { return constant.type(); }

    @Override
    public Tensor evaluate(EvaluationContext<NAMETYPE> context) { return constant; }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) { return constant.toString(); }

    @Override
    public int hashCode() {
        return Objects.hash("constant", constant.hashCode());
    }

}
