package com.yahoo.tensor.functions;

import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

/**
 * The <i>map</i> tensor function produces a tensor where the given function is applied on each cell value.
 *
 * @author bratseth
 */
public class Map extends PrimitiveTensorFunction {

    private final TensorFunction argument;
    private final DoubleUnaryOperator mapper;

    public Map(TensorFunction argument, DoubleUnaryOperator mapper) {
        Objects.requireNonNull(argument, "The argument tensor cannot be null");
        Objects.requireNonNull(mapper, "The argument function cannot be null");
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
        return "map(" + argument.toString() + ", lambda(a) (" + mapper + "))";
    }

}
