package com.yahoo.tensor.functions;

import java.util.Collections;
import java.util.List;

/**
 * @author bratseth
 */
public class Softmax extends CompositeTensorFunction {

    private final TensorFunction argument;
    private final String dimension;
    
    public Softmax(TensorFunction argument, String dimension) {
        this.argument = argument;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction> functionArguments() { return Collections.singletonList(argument); }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        TensorFunction primitiveArgument = argument.toPrimitive();
        // join(map(t, f(x)(exp(x))), reduce(map(t, f(x)(exp(x))), "sum", "dimension"), f(x,y)(x / y))
        return new Join(new Map(primitiveArgument, ScalarFunctions.exp()),
                        new Reduce(new Map(primitiveArgument, ScalarFunctions.exp()),
                                   Reduce.Aggregator.sum,
                                   dimension),
                        ScalarFunctions.divide());
    }
    
    @Override
    public String toString(ToStringContext context) {
        return "softmax(" + argument.toString(context) + ", " + dimension + ")";
    }

}
