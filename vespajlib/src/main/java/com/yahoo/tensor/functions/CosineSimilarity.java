// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.TensorType.Dimension;

import java.util.List;
import java.util.Objects;

/**
 * Convenience for cosine similarity between vectors.
 * cosine_similarity(a, b, mydim) == sum(a*b, mydim) / sqrt(sum(a*a, mydim) * sum(b*b, mydim))
 * @author arnej
 */
public class CosineSimilarity<NAMETYPE extends Name> extends TensorFunction<NAMETYPE> {

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
    public TensorType type(TypeContext<NAMETYPE> context) {
        TensorType t1 = arg1.toPrimitive().type(context);
        TensorType t2 = arg2.toPrimitive().type(context);
        var d1 = t1.dimension(dimension);
        var d2 = t2.dimension(dimension);
        if (d1.isEmpty() || d2.isEmpty()
            || d1.get().type() != Dimension.Type.indexedBound
            || d2.get().type() != Dimension.Type.indexedBound
            || ! d1.get().size().equals(d2.get().size()))
        {
            throw new IllegalArgumentException("cosine_similarity expects both arguments to have the '"
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
