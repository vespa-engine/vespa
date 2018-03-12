// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The <i>reduce</i> tensor operation returns a tensor produced from the argument tensor where some dimensions
 * are collapsed to a single value using an aggregator function.
 *
 * @author bratseth
 */
public class Reduce extends PrimitiveTensorFunction {

    public enum Aggregator { avg, count, prod, sum, max, min; }

    private final TensorFunction argument;
    private final List<String> dimensions;
    private final Aggregator aggregator;

    /** Creates a reduce function reducing aLL dimensions */
    public Reduce(TensorFunction argument, Aggregator aggregator) {
        this(argument, aggregator, Collections.emptyList());
    }

    /** Creates a reduce function reducing a single dimension */
    public Reduce(TensorFunction argument, Aggregator aggregator, String dimension) {
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
    public Reduce(TensorFunction argument, Aggregator aggregator, List<String> dimensions) {
        Objects.requireNonNull(argument, "The argument tensor cannot be null");
        Objects.requireNonNull(aggregator, "The aggregator cannot be null");
        Objects.requireNonNull(dimensions, "The dimensions cannot be null");
        this.argument = argument;
        this.aggregator = aggregator;
        this.dimensions = ImmutableList.copyOf(dimensions);
    }

    public static TensorType outputType(TensorType inputType, List<String> reduceDimensions) {
        TensorType.Builder b = new TensorType.Builder();
        for (TensorType.Dimension dimension : inputType.dimensions()) {
            if ( ! reduceDimensions.contains(dimension.name()))
                b.dimension(dimension);
        }
        return b.build();
    }

    public TensorFunction argument() { return argument; }

    @Override
    public List<TensorFunction> arguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction withArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("Reduce must have 1 argument, got " + arguments.size());
        return new Reduce(arguments.get(0), aggregator, dimensions);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new Reduce(argument.toPrimitive(), aggregator, dimensions);
    }

    @Override
    public String toString(ToStringContext context) {
        return "reduce(" + argument.toString(context) + ", " + aggregator + commaSeparated(dimensions) + ")";
    }

    private String commaSeparated(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (String element  : list)
            b.append(", ").append(element);
        return b.toString();
    }

    @Override
    public <NAMETYPE extends TypeContext.Name> TensorType type(TypeContext<NAMETYPE> context) {
        return type(argument.type(context));
    }

    private TensorType type(TensorType argumentType) {
        if (dimensions.isEmpty()) return TensorType.empty; // means reduce all
        TensorType.Builder builder = new TensorType.Builder();
        for (TensorType.Dimension dimension : argumentType.dimensions())
            if ( ! dimensions.contains(dimension.name())) // keep
                builder.dimension(dimension);
        return builder.build();
    }

    @Override
    public <NAMETYPE extends TypeContext.Name> Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor argument = this.argument.evaluate(context);
        if ( ! dimensions.isEmpty() && ! argument.type().dimensionNames().containsAll(dimensions))
            throw new IllegalArgumentException("Cannot reduce " + argument + " over dimensions " +
                                               dimensions + ": Not all those dimensions are present in this tensor");

        // Special case: Reduce all
        if (dimensions.isEmpty() || dimensions.size() == argument.type().dimensions().size())
            if (argument.type().dimensions().size() == 1 && argument instanceof IndexedTensor)
                return reduceIndexedVector((IndexedTensor)argument);
            else
                return reduceAllGeneral(argument);

        TensorType reducedType = type(argument.type());

        // Reduce cells
        Map<TensorAddress, ValueAggregator> aggregatingCells = new HashMap<>();
        for (Iterator<Tensor.Cell> i = argument.cellIterator(); i.hasNext(); ) {
            Map.Entry<TensorAddress, Double> cell = i.next();
            TensorAddress reducedAddress = reduceDimensions(cell.getKey(), argument.type(), reducedType);
            aggregatingCells.putIfAbsent(reducedAddress, ValueAggregator.ofType(aggregator));
            aggregatingCells.get(reducedAddress).aggregate(cell.getValue());
        }
        Tensor.Builder reducedBuilder = Tensor.Builder.of(reducedType);
        for (Map.Entry<TensorAddress, ValueAggregator> aggregatingCell : aggregatingCells.entrySet())
            reducedBuilder.cell(aggregatingCell.getKey(), aggregatingCell.getValue().aggregatedValue());

        return reducedBuilder.build();
    }

    private TensorAddress reduceDimensions(TensorAddress address, TensorType argumentType, TensorType reducedType) {
        Set<Integer> indexesToRemove = new HashSet<>();
        for (String dimensionToRemove : this.dimensions)
            indexesToRemove.add(argumentType.indexOfDimension(dimensionToRemove).get());

        String[] reducedLabels = new String[reducedType.dimensions().size()];
        int reducedLabelIndex = 0;
        for (int i = 0; i < address.size(); i++)
            if ( ! indexesToRemove.contains(i))
                reducedLabels[reducedLabelIndex++] = address.label(i);
        return TensorAddress.of(reducedLabels);
    }

    private Tensor reduceAllGeneral(Tensor argument) {
        ValueAggregator valueAggregator = ValueAggregator.ofType(aggregator);
        for (Iterator<Double> i = argument.valueIterator(); i.hasNext(); )
            valueAggregator.aggregate(i.next());
        return Tensor.Builder.of(TensorType.empty).cell((valueAggregator.aggregatedValue())).build();
    }

    private Tensor reduceIndexedVector(IndexedTensor argument) {
        ValueAggregator valueAggregator = ValueAggregator.ofType(aggregator);
        for (int i = 0; i < argument.dimensionSizes().size(0); i++)
            valueAggregator.aggregate(argument.get(i));
        return Tensor.Builder.of(TensorType.empty).cell((valueAggregator.aggregatedValue())).build();
    }

    private static abstract class ValueAggregator {

        private static ValueAggregator ofType(Aggregator aggregator) {
            switch (aggregator) {
                case avg : return new AvgAggregator();
                case count : return new CountAggregator();
                case prod : return new ProdAggregator();
                case sum : return new SumAggregator();
                case max : return new MaxAggregator();
                case min : return new MinAggregator();
                default: throw new UnsupportedOperationException("Aggregator " + aggregator + " is not implemented");
            }

        }

        /** Add a new value to those aggregated by this */
        public abstract void aggregate(double value);

        /** Returns the value aggregated by this */
        public abstract double aggregatedValue();

    }

    private static class AvgAggregator extends ValueAggregator {

        private int valueCount = 0;
        private double valueSum = 0.0;

        @Override
        public void aggregate(double value) {
            valueCount++;
            valueSum+= value;
        }

        @Override
        public double aggregatedValue() {
            return valueSum / valueCount;
        }

    }

    private static class CountAggregator extends ValueAggregator {

        private int valueCount = 0;

        @Override
        public void aggregate(double value) {
            valueCount++;
        }

        @Override
        public double aggregatedValue() {
            return valueCount;
        }

    }

    private static class ProdAggregator extends ValueAggregator {

        private double valueProd = 1.0;

        @Override
        public void aggregate(double value) {
            valueProd *= value;
        }

        @Override
        public double aggregatedValue() {
            return valueProd;
        }

    }

    private static class SumAggregator extends ValueAggregator {

        private double valueSum = 0.0;

        @Override
        public void aggregate(double value) {
            valueSum += value;
        }

        @Override
        public double aggregatedValue() {
            return valueSum;
        }

    }

    private static class MaxAggregator extends ValueAggregator {

        private double maxValue = Double.MIN_VALUE;

        @Override
        public void aggregate(double value) {
            if (value > maxValue)
                maxValue = value;
        }

        @Override
        public double aggregatedValue() {
            return maxValue;
        }

    }

    private static class MinAggregator extends ValueAggregator {

        private double minValue = Double.MAX_VALUE;

        @Override
        public void aggregate(double value) {
            if (value < minValue)
                minValue = value;
        }

        @Override
        public double aggregatedValue() {
            return minValue;
        }

    }

}
