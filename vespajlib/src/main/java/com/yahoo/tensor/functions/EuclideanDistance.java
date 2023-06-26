// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.TensorType.Dimension;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Convenience for euclidean distance between vectors.
 * euclidean_distance(a, b, mydim) == sqrt(sum(pow(a-b, 2), mydim))
 * @author arnej
 */
public class EuclideanDistance<NAMETYPE extends Name> extends TensorFunction<NAMETYPE> {

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
    public TensorType type(TypeContext<NAMETYPE> context) {
        TensorType t1 = arg1.toPrimitive().type(context);
        TensorType t2 = arg2.toPrimitive().type(context);
        var d1 = t1.dimension(dimension);
        var d2 = t2.dimension(dimension);
        if (d1.isEmpty() || d2.isEmpty()
            || d1.get().type() != Dimension.Type.indexedBound
            || d2.get().type() != Dimension.Type.indexedBound
            || d1.get().size().get() != d2.get().size().get())
        {
            throw new IllegalArgumentException("euclidean_distance expects both arguments to have the '"
                                               + dimension + "' dimension with same size, but input types were "
                                               + t1 + " and " + t2);
        }
        // Finds the type this produces by first converting it to a primitive function
        return toPrimitive().type(context);
    }

    /** Evaluates this by first converting it to a primitive function */
    @Override
    public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        return toPrimitive().evaluate(context);
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
