package com.yahoo.tensor.functions;

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
    public PrimitiveTensorFunction toPrimitive() {
        TensorFunction primitiveArgument = argument.toPrimitive();
        return new Join(primitiveArgument, 
                        new Map(new Reduce(new Map(primitiveArgument, ScalarFunctions.square()), 
                                           Reduce.Aggregator.sum,
                                           dimension), 
                                ScalarFunctions.square()), 
                        ScalarFunctions.divide());
    }
    
    @Override
    public String toString() {
        return "l2_normalize(" + argument + ")";
    }

}
