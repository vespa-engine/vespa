package com.yahoo.tensor.functions;

/**
 * @author bratseth
 */
public class L1Normalize extends CompositeTensorFunction {

    private final TensorFunction argument;
    
    public L1Normalize(TensorFunction argument) {
        this.argument = argument;
    }
    
    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new Join(argument.toPrimitive(), new Reduce(argument.toPrimitive(), Reduce.Aggregator.avg, "dimension"), ScalarFunctions.multiply());
    }
    
    @Override
    public String toString() {
        return "l1_normalize(" + argument + ")";
    }

}
