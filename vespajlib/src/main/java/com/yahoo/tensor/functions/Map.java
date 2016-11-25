package com.yahoo.tensor.functions;

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * The join tensor function.
 *
 * @author bratseth
 */
public class Map extends PrimitiveTensorFunction {

    private final TensorFunction argument;
    private final DoubleUnaryOperator mapper;

    public Map(TensorFunction argument, DoubleUnaryOperator mapper) {
        this.argument = argument;
        this.mapper = mapper;
    }

    public TensorFunction argument() { return argument; }
    public DoubleUnaryOperator mapper() { return mapper; }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new Map(argument.toPrimitive(), mapper);
    }

    @Override
    public String toString() {
        return "map(" + argument.toString() + ", lambda(a) (...))";
    }

}
