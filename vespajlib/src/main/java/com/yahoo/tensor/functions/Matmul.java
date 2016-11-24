package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * @author bratseth
 */
public class Matmul extends CompositeTensorFunction {

    private final TensorFunction argument1, argument2;
    private final String dimension;
    
    public Matmul(TensorFunction argument1, TensorFunction argument2, String dimension) {
        this.argument1 = argument1;
        this.argument2 = argument2;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction> functionArguments() { return ImmutableList.of(argument1, argument2); }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        TensorFunction primitiveArgument1 = argument1.toPrimitive();
        TensorFunction primitiveArgument2 = argument2.toPrimitive();
        return new Reduce(new Join(primitiveArgument1, primitiveArgument2, ScalarFunctions.multiply()),
                          Reduce.Aggregator.sum,
                          dimension);
    }
    
    @Override
    public String toString(ToStringContext context) {
        return "matmul(" + argument1.toString(context) + ", " + argument2.toString(context) + ", " + dimension + ")";
    }

}
