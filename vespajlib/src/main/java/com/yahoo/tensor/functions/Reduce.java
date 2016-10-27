package com.yahoo.tensor.functions;

import java.util.Optional;
import java.util.function.DoubleBinaryOperator;

/**
 * The reduce tensor function.
 *
 * @author bratseth
 */
public class Reduce extends PrimitiveTensorFunction {

    private final TensorFunction argument;
    private final String dimension;
    private final DoubleBinaryOperator reductor;
    private final Optional<DoubleBinaryOperator> postTransformation;

    public Reduce(TensorFunction argument, String dimension,
                  DoubleBinaryOperator reductor, Optional<DoubleBinaryOperator> postTransformation) {
        this.argument = argument;
        this.dimension = dimension;
        this.reductor = reductor;
        this.postTransformation = postTransformation;
    }

    public TensorFunction argument() { return argument; }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new Reduce(argument.toPrimitive(), dimension, reductor, postTransformation);
    }

}
