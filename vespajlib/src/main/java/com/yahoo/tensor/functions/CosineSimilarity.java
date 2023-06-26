// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.evaluation.Name;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Convenience for cosine similarity between vectors.
 * cosine_similarity(a, b, mydim) == sum(a*b, mydim) / sqrt(sum(a*a, mydim) * sum(b*b, mydim))
 * @author arnej
 */
public class CosineSimilarity<NAMETYPE extends Name> extends CompositeTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> arg1;
    private final TensorFunction<NAMETYPE> arg2;
    private final String dimension;

    public CosineSimilarity(TensorFunction<NAMETYPE> argument1,
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
            throw new IllegalArgumentException("CosineSimilarity must have 2 arguments, got " + arguments.size());
        return new CosineSimilarity<>(arguments.get(0), arguments.get(1), dimension);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        TensorFunction<NAMETYPE> a = arg1.toPrimitive();
        TensorFunction<NAMETYPE> b = arg2.toPrimitive();
        var aa = new Join<>(a, a, ScalarFunctions.multiply());
        var ab = new Join<>(a, b, ScalarFunctions.multiply());
        var bb = new Join<>(b, b, ScalarFunctions.multiply());
        var dot_aa = new Reduce<>(aa, Reduce.Aggregator.sum, dimension);
        var dot_ab = new Reduce<>(ab, Reduce.Aggregator.sum, dimension);
        var dot_bb = new Reduce<>(bb, Reduce.Aggregator.sum, dimension);
        var aabb = new Join<>(dot_aa, dot_bb, ScalarFunctions.multiply());
        var sqrt_aabb = new Map<>(aabb, ScalarFunctions.sqrt());
        return new Join<>(dot_ab, sqrt_aabb, ScalarFunctions.divide());
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "cosine_similarity(" + arg1.toString(context) + ", " + arg2.toString(context) + ", " + dimension + ")";
    }

    @Override
    public int hashCode() { return Objects.hash("cosine_similarity", arg1, arg2, dimension); }

}
