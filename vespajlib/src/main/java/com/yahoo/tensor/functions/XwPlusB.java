package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * @author bratseth
 */
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
    public List<TensorFunction> functionArguments() { return ImmutableList.of(x, w, b); }

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
