// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.evaluation.Name;

import java.util.List;
import java.util.Objects;

/**
 * @author bratseth
 */
public class XwPlusB<NAMETYPE extends Name> extends CompositeTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> x, w, b;
    private final String dimension;

    public XwPlusB(TensorFunction<NAMETYPE> x, TensorFunction<NAMETYPE> w, TensorFunction<NAMETYPE> b, String dimension) {
        this.x = x;
        this.w = w;
        this.b = b;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return ImmutableList.of(x, w, b); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 3)
            throw new IllegalArgumentException("XwPlusB must have 3 arguments, got " + arguments.size());
        return new XwPlusB<>(arguments.get(0), arguments.get(1), arguments.get(2), dimension);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        TensorFunction<NAMETYPE> primitiveX = x.toPrimitive();
        TensorFunction<NAMETYPE> primitiveW = w.toPrimitive();
        TensorFunction<NAMETYPE> primitiveB = b.toPrimitive();
        return new Join<>(new Reduce<>(new Join<>(primitiveX, primitiveW, ScalarFunctions.multiply()),
                                       Reduce.Aggregator.sum,
                                       dimension),
                          primitiveB,
                          ScalarFunctions.add());
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "xw_plus_b(" + x.toString(context) + ", " +
               w.toString(context) + ", " +
               b.toString(context) + ", " +
               dimension + ")";
    }

    @Override
    public int hashCode() { return Objects.hash("xwplusb", x, w, b, dimension); }

}
