// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * @author bratseth
 */
@Beta
public class XwPlusB extends CompositeTensorFunction {

    private final TensorFunction x, w, b;
    private final String dimension;

    public XwPlusB(TensorFunction x, TensorFunction w, TensorFunction b, String dimension) {
        this.x = x;
        this.w = w;
        this.b = b;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction> arguments() { return ImmutableList.of(x, w, b); }

    @Override
    public TensorFunction withArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 3)
            throw new IllegalArgumentException("XwPlusB must have 3 arguments, got " + arguments.size());
        return new XwPlusB(arguments.get(0), arguments.get(1), arguments.get(2), dimension);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        TensorFunction primitiveX = x.toPrimitive();
        TensorFunction primitiveW = w.toPrimitive();
        TensorFunction primitiveB = b.toPrimitive();
        return new Join(new Reduce(new Join(primitiveX, primitiveW, ScalarFunctions.multiply()),
                                   Reduce.Aggregator.sum,
                                   dimension),
                        primitiveB,
                        ScalarFunctions.add());
    }

    @Override
    public String toString(ToStringContext context) {
        return "xw_plus_b(" + x.toString(context) + ", " +
               w.toString(context) + ", " +
               b.toString(context) + ", " +
               dimension + ")";
    }

}
