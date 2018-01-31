// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;

import java.util.Collections;
import java.util.List;

/**
 * @author bratseth
 */
@Beta
public class Argmin extends CompositeTensorFunction {

    private final TensorFunction argument;
    private final String dimension;

    public Argmin(TensorFunction argument, String dimension) {
        this.argument = argument;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction> arguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction withArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("Argmin must have 1 argument, got " + arguments.size());
        return new Argmin(arguments.get(0), dimension);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        TensorFunction primitiveArgument = argument.toPrimitive();
        return new Join(primitiveArgument,
                        new Reduce(primitiveArgument, Reduce.Aggregator.min, dimension),
                        ScalarFunctions.equal());
    }

    @Override
    public String toString(ToStringContext context) {
        return "argmin(" + argument.toString(context) + ", " + dimension + ")";
    }

}
