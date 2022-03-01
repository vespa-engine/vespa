// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.evaluation.Name;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author bratseth
 */
public class Argmax<NAMETYPE extends Name> extends CompositeTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> argument;
    private final List<String> dimensions;

    public Argmax(TensorFunction<NAMETYPE> argument) {
        this(argument, Collections.emptyList());
    }

    public Argmax(TensorFunction<NAMETYPE> argument, String dimension) {
        this(argument, Collections.singletonList(dimension));
    }

    public Argmax(TensorFunction<NAMETYPE> argument, List<String> dimensions) {
        Objects.requireNonNull(dimensions, "The dimensions cannot be null");
        this.argument = argument;
        this.dimensions = ImmutableList.copyOf(dimensions);
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if (arguments.size() != 1)
            throw new IllegalArgumentException("Argmax must have 1 argument, got " + arguments.size());
        return new Argmax<>(arguments.get(0), dimensions);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        TensorFunction<NAMETYPE> primitiveArgument = argument.toPrimitive();
        TensorFunction<NAMETYPE> reduce = new Reduce<>(primitiveArgument, Reduce.Aggregator.max, dimensions);
        return new Join<>(primitiveArgument, reduce, ScalarFunctions.equal());
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "argmax(" + argument.toString(context) + Reduce.commaSeparated(dimensions) + ")";
    }

    @Override
    public int hashCode() { return Objects.hash("argmax", argument, dimensions); }

}
