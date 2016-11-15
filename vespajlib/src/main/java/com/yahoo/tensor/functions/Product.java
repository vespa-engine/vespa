package com.yahoo.tensor.functions;

import java.util.Objects;

/**
 * The product tensor function: A join using product to join cells.
 * 
 * @author bratseth
 */
public class Product extends CompositeTensorFunction {

    private final TensorFunction argumentA, argumentB;
    
    public Product(TensorFunction argumentA, TensorFunction argumentB) {
        Objects.requireNonNull(argumentA, "The first argument tensor cannot be null");
        Objects.requireNonNull(argumentB, "The second argument tensor cannot be null");
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
