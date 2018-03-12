// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import java.util.Collections;
import java.util.List;

/**
 * @author bratseth
 */
public class Argmax extends CompositeTensorFunction {

    private final TensorFunction argument;
    private final String dimension;

    public Argmax(TensorFunction argument, String dimension) {
        this.argument = argument;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction> arguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction withArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("Argmax must have 1 argument, got " + arguments.size());
        return new Argmax(arguments.get(0), dimension);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        TensorFunction primitiveArgument = argument.toPrimitive();
        return new Join(primitiveArgument,
                        new Reduce(primitiveArgument, Reduce.Aggregator.max, dimension),
                        ScalarFunctions.equal());
    }

    @Override
    public String toString(ToStringContext context) {
        return "argmax(" + argument.toString(context) + ", " + dimension + ")";
    }

}
