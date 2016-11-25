package com.yahoo.tensor.functions;

import java.util.Collections;
import java.util.List;

/**
 * @author bratseth
 */
public class L2Normalize extends CompositeTensorFunction {

    private final TensorFunction argument;
    private final String dimension;
    
    public L2Normalize(TensorFunction argument, String dimension) {
        this.argument = argument;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction> functionArguments() { return Collections.singletonList(argument); }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        TensorFunction primitiveArgument = argument.toPrimitive();
        return new Join(primitiveArgument,
                        new Map(new Reduce(new Map(primitiveArgument, ScalarFunctions.sqrt()),
                                           Reduce.Aggregator.sum,
                                           dimension),
                                ScalarFunctions.square()),
                        ScalarFunctions.divide());
    }
    
    @Override
    public String toString(ToStringContext context) {
        return "l2_normalize(" + argument.toString(context) + ", " + dimension + ")";
    }

}
