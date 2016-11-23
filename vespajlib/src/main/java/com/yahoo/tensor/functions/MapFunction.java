package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableMap;
import com.yahoo.tensor.MapTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;

import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

/**
 * The <i>map</i> tensor function produces a tensor where the given function is applied on each cell value.
 *
 * @author bratseth
 */
public class MapFunction extends PrimitiveTensorFunction {

    private final TensorFunction argument;
    private final DoubleUnaryOperator mapper;

    public MapFunction(TensorFunction argument, DoubleUnaryOperator mapper) {
        Objects.requireNonNull(argument, "The argument tensor cannot be null");
        Objects.requireNonNull(mapper, "The argument function cannot be null");
        this.argument = argument;
        this.mapper = mapper;
    }

    public TensorFunction argument() { return argument; }
    public DoubleUnaryOperator mapper() { return mapper; }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new MapFunction(argument.toPrimitive(), mapper);
    }

    @Override
    public Tensor execute() {
        Tensor argument = argument().execute();
        ImmutableMap.Builder<TensorAddress, Double> mappedCells = new ImmutableMap.Builder<>();
        for (Map.Entry<TensorAddress, Double> cell : argument.cells().entrySet())
            mappedCells.put(cell.getKey(), mapper.applyAsDouble(cell.getValue()));
        return new MapTensor(argument.dimensions(), mappedCells.build());
    }

    @Override
    public String toString() {
        return "map(" + argument.toString() + ", f(a) (" + mapper + "))";
    }

}
