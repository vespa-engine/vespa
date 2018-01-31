// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.TensorType;

import java.util.Collections;
import java.util.List;

/**
 * @author bratseth
 */
@Beta
public class Softmax extends CompositeTensorFunction {

    private final TensorFunction argument;
    private final String dimension;

    public Softmax(TensorFunction argument, String dimension) {
        this.argument = argument;
        this.dimension = dimension;
    }

    public static TensorType outputType(TensorType inputType, String dimension) {
        return Reduce.outputType(inputType, ImmutableList.of(dimension));
    }

    @Override
    public List<TensorFunction> arguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction withArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("Softmax must have 1 argument, got " + arguments.size());
        return new Softmax(arguments.get(0), dimension);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        TensorFunction primitiveArgument = argument.toPrimitive();
        return new Join(new Map(primitiveArgument, ScalarFunctions.exp()),
                        new Reduce(new Map(primitiveArgument, ScalarFunctions.exp()),
                                   Reduce.Aggregator.sum,
                                   dimension),
                        ScalarFunctions.divide());
    }

    @Override
    public String toString(ToStringContext context) {
        return "softmax(" + argument.toString(context) + ", " + dimension + ")";
    }

}
