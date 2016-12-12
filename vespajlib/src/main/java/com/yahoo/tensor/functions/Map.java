package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableMap;
import com.yahoo.tensor.MappedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.evaluation.EvaluationContext;

import java.util.Collections;
import java.util.List;
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
    public List<TensorFunction> functionArguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction replaceArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("Map must have 1 argument, got " + arguments.size());
        return new Map(arguments.get(0), mapper);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new Map(argument.toPrimitive(), mapper);
    }

    @Override
    public Tensor evaluate(EvaluationContext context) {
        Tensor argument = argument().evaluate(context);
        ImmutableMap.Builder<TensorAddress, Double> mappedCells = new ImmutableMap.Builder<>();
        for (java.util.Map.Entry<TensorAddress, Double> cell : argument.cells().entrySet())
            mappedCells.put(cell.getKey(), mapper.applyAsDouble(cell.getValue()));
        return new MappedTensor(argument.type(), mappedCells.build());
    }

    @Override
    public String toString(ToStringContext context) {
        return "map(" + argument.toString(context) + ", " + mapper + ")";
    }

}
