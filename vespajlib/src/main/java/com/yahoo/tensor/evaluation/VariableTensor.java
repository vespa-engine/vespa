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
import java.util.Optional;

/**
 * A tensor variable name which resolves to a tensor in the context at evaluation time
 *
 * @author bratseth
 */
@Beta
public class VariableTensor extends PrimitiveTensorFunction {

    private final String name;
    private final Optional<TensorType> requiredType;

    public VariableTensor(String name) {
        this.name = name;
        this.requiredType = Optional.empty();
    }

    /** A variable tensor which must be compatible with the given type */
    public VariableTensor(String name, TensorType requiredType) {
        this.name = name;
        this.requiredType = Optional.of(requiredType);
    }

    @Override
    public List<TensorFunction> arguments() { return Collections.emptyList(); }

    @Override
    public TensorFunction withArguments(List<TensorFunction> arguments) { return this; }

    @Override
    public PrimitiveTensorFunction toPrimitive() { return this; }

    @Override
    public TensorType type(TypeContext context) {
        TensorType givenType = context.getType(name);
        if (givenType == null) return null;
        verifyType(givenType);
        return givenType;
    }

    @Override
    public Tensor evaluate(EvaluationContext context) {
        Tensor tensor = context.getTensor(name);
        if (tensor == null) return null;
        verifyType(tensor.type());
        return tensor;
    }

    @Override
    public String toString(ToStringContext context) {
        return name;
    }

    private void verifyType(TensorType givenType) {
        if (requiredType.isPresent() && ! givenType.isAssignableTo(requiredType.get()))
            throw new IllegalArgumentException("Variable '" + name + "' must be compatible with " +
                                               requiredType.get() + " but was " + givenType);
    }
}
