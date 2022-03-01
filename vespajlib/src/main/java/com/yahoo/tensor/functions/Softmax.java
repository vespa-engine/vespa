// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.Name;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author bratseth
 */
public class Softmax<NAMETYPE extends Name> extends CompositeTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> argument;
    private final String dimension;

    public Softmax(TensorFunction<NAMETYPE> argument, String dimension) {
        this.argument = argument;
        this.dimension = dimension;
    }

    public static TensorType outputType(TensorType inputType, String dimension) {
        return Reduce.outputType(inputType, ImmutableList.of(dimension));
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("Softmax must have 1 argument, got " + arguments.size());
        return new Softmax<>(arguments.get(0), dimension);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        TensorFunction<NAMETYPE> primitiveArgument = argument.toPrimitive();
        return new Join<>(new Map<>(primitiveArgument, ScalarFunctions.exp()),
                          new Reduce<>(new Map<>(primitiveArgument, ScalarFunctions.exp()),
                                       Reduce.Aggregator.sum,
                                       dimension),
                          ScalarFunctions.divide());
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "softmax(" + argument.toString(context) + ", " + dimension + ")";
    }

    @Override
    public int hashCode() { return Objects.hash("softmax", argument, dimension); }

}
