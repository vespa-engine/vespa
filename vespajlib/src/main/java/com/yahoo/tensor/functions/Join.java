package com.yahoo.tensor.functions;

import java.util.function.DoubleBinaryOperator;

/**
 * The join tensor function. 
 * 
 * @author bratseth
 */
public class Join extends PrimitiveTensorFunction {
    
    private final TensorFunction argumentA, argumentB;
    private final DoubleBinaryOperator combinator;

    public Join(TensorFunction argumentA, TensorFunction argumentB, DoubleBinaryOperator combinator) {
        this.argumentA = argumentA;
        this.argumentB = argumentB;
        this.combinator = combinator;
    }

    public TensorFunction argumentA() { return argumentA; }
    public TensorFunction argumentB() { return argumentB; }
    public DoubleBinaryOperator combinator() { return combinator; }
    
    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new Join(argumentA.toPrimitive(), argumentB.toPrimitive(), combinator);
    }

}
