package com.yahoo.tensor.functions;

import java.util.Collections;
import java.util.List;

/**
 * @author bratseth
 */
public class L1Normalize extends CompositeTensorFunction {

    private final TensorFunction argument;
    private final String dimension;
    
    public L1Normalize(TensorFunction argument, String dimension) {
        this.argument = argument;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction> functionArguments() { return Collections.singletonList(argument); }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        TensorFunction primitiveArgument = argument.toPrimitive();
        // join(x, reduce(x, "avg", "dimension"), f(x,y) (x / y))
        return new Join(primitiveArgument,
                        new Reduce(primitiveArgument, Reduce.Aggregator.sum, dimension),
                        ScalarFunctions.divide());
    }
    
    @Override
    public String toString(ToStringContext context) {
        return "l1_normalize(" + argument.toString(context) + ", " + dimension + ")";
    }

}
