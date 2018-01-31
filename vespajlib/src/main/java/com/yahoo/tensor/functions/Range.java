// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.TensorType;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A tensor generator which returns a tensor of any dimension filled with the sum of the tensor
 * indexes of each position.
 *
 * @author bratseth
 */
public class Range extends CompositeTensorFunction {

    private final TensorType type;
    private final Function<List<Long>, Double> rangeFunction;

    public Range(TensorType type) {
        this.type = type;
        this.rangeFunction = ScalarFunctions.sum(dimensionNames().collect(Collectors.toList()));
    }

    @Override
    public List<TensorFunction> arguments() { return Collections.emptyList(); }

    @Override
    public TensorFunction withArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 0)
            throw new IllegalArgumentException("Range must have 0 arguments, got " + arguments.size());
        return this;
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new Generate(type, rangeFunction);
    }

    @Override
    public String toString(ToStringContext context) {
        return "range(" + dimensionNames().collect(Collectors.joining(",")) + ")" + rangeFunction;
    }

    private Stream<String> dimensionNames() {
        return type.dimensions().stream().map(TensorType.Dimension::toString);
    }

}
