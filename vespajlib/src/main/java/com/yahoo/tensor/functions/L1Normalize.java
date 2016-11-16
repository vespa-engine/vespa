package com.yahoo.tensor.functions;

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
    public PrimitiveTensorFunction toPrimitive() {
        TensorFunction primitiveArgument = argument.toPrimitive();
        return new Join(primitiveArgument, 
                        new Reduce(primitiveArgument, Reduce.Aggregator.avg, dimension), 
                        ScalarFunctions.multiply());
    }
    
    @Override
    public String toString() {
        return "l1_normalize(" + argument + ")";
    }

}
