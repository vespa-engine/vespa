package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;

import java.util.Objects;
import java.util.function.DoubleBinaryOperator;

/**
 * The <i>join</i> tensor operation produces a tensor from the argument tensors containing the set of cells
 * given by the cross product of the cells of the given tensors, having as values the value produced by
 * applying the given combinator function on the values from the two source cells.
 * 
 * @author bratseth
 */
public class JoinFunction extends PrimitiveTensorFunction {
    
    private final TensorFunction argumentA, argumentB;
    private final DoubleBinaryOperator combinator;

    public JoinFunction(TensorFunction argumentA, TensorFunction argumentB, DoubleBinaryOperator combinator) {
        Objects.requireNonNull(argumentA, "The first argument tensor cannot be null");
        Objects.requireNonNull(argumentB, "The second argument tensor cannot be null");
        Objects.requireNonNull(combinator, "The combinator function cannot be null");
        this.argumentA = argumentA;
        this.argumentB = argumentB;
        this.combinator = combinator;
    }

    public TensorFunction argumentA() { return argumentA; }
    public TensorFunction argumentB() { return argumentB; }
    public DoubleBinaryOperator combinator() { return combinator; }
    
    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new JoinFunction(argumentA.toPrimitive(), argumentB.toPrimitive(), combinator);
    }

    @Override
    public Tensor execute() {
        throw new UnsupportedOperationException("Not implemented"); // TODO
    }

    @Override
    public String toString() {
        return "join(" + argumentA.toString() + ", " + argumentB.toString() + ", f(a, b) (" + combinator + "))";
    }

}
