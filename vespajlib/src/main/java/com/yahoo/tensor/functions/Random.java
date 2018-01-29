// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.CompositeTensorFunction;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.PrimitiveTensorFunction;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.TensorFunction;
import com.yahoo.tensor.functions.ToStringContext;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A tensor generator which returns a tensor of any dimension filled with random numbers between 0 and 1.
 *
 * @author bratseth
 */
public class Random extends CompositeTensorFunction {

    private final TensorType type;

    public Random(TensorType type) {
        this.type = type;
    }

    @Override
    public List<TensorFunction> arguments() { return Collections.emptyList(); }

    @Override
    public TensorFunction withArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 0)
            throw new IllegalArgumentException("Random must have 0 arguments, got " + arguments.size());
        return this;
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new Generate(type, ScalarFunctions.random());
    }

    @Override
    public String toString(ToStringContext context) {
        return "random(" + dimensionNames().collect(Collectors.joining(",")) + ")";
    }

    private Stream<String> dimensionNames() {
        return type.dimensions().stream().map(TensorType.Dimension::toString);
    }

}
