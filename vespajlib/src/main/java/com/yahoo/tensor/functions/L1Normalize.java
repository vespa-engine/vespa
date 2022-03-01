// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.evaluation.Name;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author bratseth
 */
public class L1Normalize<NAMETYPE extends Name> extends CompositeTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> argument;
    private final String dimension;

    public L1Normalize(TensorFunction<NAMETYPE> argument, String dimension) {
        this.argument = argument;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("L1Normalize must have 1 argument, got " + arguments.size());
        return new L1Normalize<>(arguments.get(0), dimension);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        TensorFunction<NAMETYPE> primitiveArgument = argument.toPrimitive();
        // join(x, reduce(x, "avg", "dimension"), f(x,y) (x / y))
        return new Join<>(primitiveArgument,
                          new Reduce<>(primitiveArgument, Reduce.Aggregator.sum, dimension),
                          ScalarFunctions.divide());
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "l1_normalize(" + argument.toString(context) + ", " + dimension + ")";
    }

    @Override
    public int hashCode() { return Objects.hash("l1_normalize", argument, dimension); }

}
