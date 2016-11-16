package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.Tensor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The <i>reduce</i> tensor operation returns a tensor produced from the argument tensor where some dimensions 
 * are collapsed to a single value using an aggregator function.
 *
 * @author bratseth
 */
public class ReduceFunction extends PrimitiveTensorFunction {

    private final TensorFunction argument;
    private final List<String> dimensions;
    private final Aggregator aggregator;

    /** Creates a reduce function reducing aLL dimensions */
    public ReduceFunction(TensorFunction argument, Aggregator aggregator) {
        this(argument, aggregator, Collections.emptyList());
    }

    /** Creates a reduce function reducing a single dimension */
    public ReduceFunction(TensorFunction argument, Aggregator aggregator, String dimension) {
        this(argument, aggregator, Collections.singletonList(dimension));
    }

    /**
     * Creates a reduce function.
     * 
     * @param argument the tensor to reduce
     * @param aggregator the aggregator function to use
     * @param dimensions the list of dimensions to remove. If an empty list is given, all dimensions are reduced,
     *                   producing a dimensionless tensor (a scalar).
     * @throws IllegalArgumentException if any of the tensor dimensions are not present in the input tensor
     */
    public ReduceFunction(TensorFunction argument, Aggregator aggregator, List<String> dimensions) {
        Objects.requireNonNull(argument, "The argument tensor cannot be null");
        Objects.requireNonNull(aggregator, "The aggregator cannot be null");
        Objects.requireNonNull(dimensions, "The dimensions cannot be null");
        this.argument = argument;
        this.aggregator = aggregator;
        this.dimensions = ImmutableList.copyOf(dimensions);
    }

    public TensorFunction argument() { return argument; }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new ReduceFunction(argument.toPrimitive(), aggregator, dimensions);
    }

    @Override
    public Tensor execute() {
        throw new UnsupportedOperationException("Not implemented"); // TODO
    }

    @Override
    public String toString() {
        return "reduce(" + argument.toString() + ", " + aggregator + commaSeparated(dimensions) + ")";
    }
    
    private String commaSeparated(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (String element  : list)
            b.append(", ").append(element);
        return b.toString();
    }
    
    public enum Aggregator {
        avg, count, prod, sum, max, min;
    }

}
