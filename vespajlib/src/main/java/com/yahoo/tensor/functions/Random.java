// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.Name;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A tensor generator which returns a tensor of any dimension filled with random numbers between 0 and 1.
 *
 * @author bratseth
 */
public class Random<NAMETYPE extends Name> extends CompositeTensorFunction<NAMETYPE> {

    private final TensorType type;

    public Random(TensorType type) {
        this.type = type;
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return Collections.emptyList(); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 0)
            throw new IllegalArgumentException("Random must have 0 arguments, got " + arguments.size());
        return this;
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        return new Generate<>(type, ScalarFunctions.random());
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "random(" + dimensionNames().collect(Collectors.joining(",")) + ")";
    }

    @Override
    public int hashCode() { return Objects.hash("random", type); }

    private Stream<String> dimensionNames() {
        return type.dimensions().stream().map(TensorType.Dimension::toString);
    }

}
