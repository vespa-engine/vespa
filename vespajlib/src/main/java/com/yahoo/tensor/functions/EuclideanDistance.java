// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.evaluation.Name;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author arnej
 */
public class EuclideanDistance<NAMETYPE extends Name> extends CompositeTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> arg1;
    private final TensorFunction<NAMETYPE> arg2;
    private final String dimension;

    public EuclideanDistance(TensorFunction<NAMETYPE> argument1,
                             TensorFunction<NAMETYPE> argument2,
                             String dimension)
    {
        this.arg1 = argument1;
        this.arg2 = argument2;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return List.of(arg1, arg2); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 2)
            throw new IllegalArgumentException("EuclideanDistance must have 2 arguments, got " + arguments.size());
        return new EuclideanDistance<>(arguments.get(0), arguments.get(1), dimension);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        TensorFunction<NAMETYPE> primitive1 = arg1.toPrimitive();
        TensorFunction<NAMETYPE> primitive2 = arg2.toPrimitive();
        // this should match the C++ optimized "l2_distance"
        var diffs = new Join<>(primitive1, primitive2, ScalarFunctions.subtract());
        var squaredDiffs = new Map<>(diffs, ScalarFunctions.square());
        var sumOfSquares = new Reduce<>(squaredDiffs, Reduce.Aggregator.sum, dimension);
        return new Map<>(sumOfSquares, ScalarFunctions.sqrt());
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "euclidean_distance(" + arg1.toString(context) + ", " + arg2.toString(context) + ", " + dimension + ")";
    }

    @Override
    public int hashCode() { return Objects.hash("euclidean_distance", arg1, arg2, dimension); }

}
