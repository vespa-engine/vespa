package com.yahoo.tensor.functions;

/**
 * The product tensor function
 * 
 * @author bratseth
 */
public class Product extends CompositeTensorFunction {

    private final TensorFunction argumentA, argumentB;
    
    public Product(TensorFunction argumentA, TensorFunction argumentB) {
        this.argumentA = argumentA;
        this.argumentB = argumentB;
    }
    
    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new Join(argumentA.toPrimitive(), argumentB.toPrimitive(), (a, b) -> a * b);
    }

    @Override
    public String toString() {
        return "product(" + argumentA.toString() + ", " + argumentB.toString() + ")";
    }

}
