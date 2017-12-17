// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * @author bratseth
 */
@Beta
public class Matmul extends CompositeTensorFunction {

    private final TensorFunction argument1, argument2;
    private final String dimension;
    
    public Matmul(TensorFunction argument1, TensorFunction argument2, String dimension) {
        this.argument1 = argument1;
        this.argument2 = argument2;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction> functionArguments() { return ImmutableList.of(argument1, argument2); }

    @Override
    public TensorFunction replaceArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 2)
            throw new IllegalArgumentException("Matmul must have 2 arguments, got " + arguments.size());
        return new Matmul(arguments.get(0), arguments.get(1), dimension);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        TensorFunction primitiveArgument1 = argument1.toPrimitive();
        TensorFunction primitiveArgument2 = argument2.toPrimitive();
        return new Reduce(new Join(primitiveArgument1, primitiveArgument2, ScalarFunctions.multiply()),
                          Reduce.Aggregator.sum,
                          dimension);
    }
    
    @Override
    public String toString(ToStringContext context) {
        return "matmul(" + argument1.toString(context) + ", " + argument2.toString(context) + ", " + dimension + ")";
    }

}
