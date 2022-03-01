// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.Name;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A tensor generator which returns a tensor of any dimension filled with 1 in the diagonal and 0 elsewhere.
 *
 * @author bratseth
 */
public class Diag<NAMETYPE extends Name> extends CompositeTensorFunction<NAMETYPE> {

    private final TensorType type;
    private final Function<List<Long>, Double> diagFunction;

    public Diag(TensorType type) {
        this.type = type;
        this.diagFunction = ScalarFunctions.equal(dimensionNames().collect(Collectors.toList()));
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return Collections.emptyList(); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 0)
            throw new IllegalArgumentException("Diag must have 0 arguments, got " + arguments.size());
        return this;
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        return new Generate<>(type, diagFunction);
    }

    private Stream<String> dimensionNames() {
        return type.dimensions().stream().map(TensorType.Dimension::name);
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "diag(" + dimensionNames().collect(Collectors.joining(",")) + ")" + diagFunction;
    }

    @Override
    public int hashCode() { return Objects.hash("diag", type, diagFunction); }

}
