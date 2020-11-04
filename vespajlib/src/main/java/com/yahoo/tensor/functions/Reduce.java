// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.ArrayList;
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
public class Reduce<NAMETYPE extends Name> extends PrimitiveTensorFunction<NAMETYPE> {

    public enum Aggregator { avg, count, max, median, min, prod, sum ; }

    private final TensorFunction<NAMETYPE> argument;
    private final List<String> dimensions;
    private final Aggregator aggregator;

    /** Creates a reduce function reducing all dimensions */
    public Reduce(TensorFunction<NAMETYPE> argument, Aggregator aggregator) {
        this(argument, aggregator, Collections.emptyList());
    }

    /** Creates a reduce function reducing a single dimension */
    public Reduce(TensorFunction<NAMETYPE> argument, Aggregator aggregator, String dimension) {
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
    public Reduce(TensorFunction<NAMETYPE> argument, Aggregator aggregator, List<String> dimensions) {
        this.argument = Objects.requireNonNull(argument, "The argument tensor cannot be null");
        this.aggregator  = Objects.requireNonNull(aggregator, "The aggregator cannot be null");
        this.dimensions = ImmutableList.copyOf(dimensions);
    }

    public static TensorType outputType(TensorType inputType, List<String> reduceDimensions) {
        TensorType.Builder b = new TensorType.Builder(inputType.valueType());
        if (reduceDimensions.isEmpty()) return b.build(); // means reduce all
        for (TensorType.Dimension dimension : inputType.dimensions()) {
            if ( ! reduceDimensions.contains(dimension.name()))
                b.dimension(dimension);
        }
        return b.build();
    }

    public TensorFunction<NAMETYPE> argument() { return argument; }

    Aggregator aggregator() { return aggregator; }

    List<String> dimensions() { return dimensions; }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("Reduce must have 1 argument, got " + arguments.size());
        return new Reduce<>(arguments.get(0), aggregator, dimensions);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        return new Reduce<>(argument.toPrimitive(), aggregator, dimensions);
    }

    @Override
    public String toString(ToStringContext context) {
        return "reduce(" + argument.toString(context) + ", " + aggregator + commaSeparated(dimensions) + ")";
    }

    static String commaSeparated(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (String element  : list)
            b.append(", ").append(element);
        return b.toString();
    }

    @Override
    public TensorType type(TypeContext<NAMETYPE> context) {
        return type(argument.type(context), dimensions);
    }

    private static TensorType type(TensorType argumentType, List<String> dimensions) {
        TensorType.Builder builder = new TensorType.Builder(argumentType.valueType());
        if (dimensions.isEmpty()) return builder.build(); // means reduce all
        for (TensorType.Dimension dimension : argumentType.dimensions())
            if ( ! dimensions.contains(dimension.name())) // keep
                builder.dimension(dimension);
        return builder.build();
    }

    @Override
    public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        return evaluate(this.argument.evaluate(context), dimensions, aggregator);
    }

    static Tensor evaluate(Tensor argument, List<String> dimensions, Aggregator aggregator) {
        if ( ! dimensions.isEmpty() && ! argument.type().dimensionNames().containsAll(dimensions))
            throw new IllegalArgumentException("Cannot reduce " + argument + " over dimensions " +
                                               dimensions + ": Not all those dimensions are present in this tensor");

        // Special case: Reduce all
        if (dimensions.isEmpty() || dimensions.size() == argument.type().dimensions().size())
            if (argument.type().dimensions().size() == 1 && argument instanceof IndexedTensor)
                return reduceIndexedVector((IndexedTensor)argument, aggregator);
            else
                return reduceAllGeneral(argument, aggregator);

        TensorType reducedType = type(argument.type(), dimensions);

        // Reduce cells
        Map<TensorAddress, ValueAggregator> aggregatingCells = new HashMap<>();
        for (Iterator<Tensor.Cell> i = argument.cellIterator(); i.hasNext(); ) {
            Map.Entry<TensorAddress, Double> cell = i.next();
            TensorAddress reducedAddress = reduceDimensions(cell.getKey(), argument.type(), reducedType, dimensions);
            aggregatingCells.putIfAbsent(reducedAddress, ValueAggregator.ofType(aggregator));
            aggregatingCells.get(reducedAddress).aggregate(cell.getValue());
        }
        Tensor.Builder reducedBuilder = Tensor.Builder.of(reducedType);
        for (Map.Entry<TensorAddress, ValueAggregator> aggregatingCell : aggregatingCells.entrySet())
            reducedBuilder.cell(aggregatingCell.getKey(), aggregatingCell.getValue().aggregatedValue());

        return reducedBuilder.build();

    }

    private static TensorAddress reduceDimensions(TensorAddress address, TensorType argumentType, TensorType reducedType, List<String> dimensions) {
        Set<Integer> indexesToRemove = new HashSet<>();
        for (String dimensionToRemove : dimensions)
            indexesToRemove.add(argumentType.indexOfDimension(dimensionToRemove).get());

        String[] reducedLabels = new String[reducedType.dimensions().size()];
        int reducedLabelIndex = 0;
        for (int i = 0; i < address.size(); i++)
            if ( ! indexesToRemove.contains(i))
                reducedLabels[reducedLabelIndex++] = address.label(i);
        return TensorAddress.of(reducedLabels);
    }

    private static Tensor reduceAllGeneral(Tensor argument, Aggregator aggregator) {
        ValueAggregator valueAggregator = ValueAggregator.ofType(aggregator);
        for (Iterator<Double> i = argument.valueIterator(); i.hasNext(); )
            valueAggregator.aggregate(i.next());
        return Tensor.Builder.of(TensorType.empty).cell((valueAggregator.aggregatedValue())).build();
    }

    private static Tensor reduceIndexedVector(IndexedTensor argument, Aggregator aggregator) {
        ValueAggregator valueAggregator = ValueAggregator.ofType(aggregator);
        for (int i = 0; i < argument.dimensionSizes().size(0); i++)
            valueAggregator.aggregate(argument.get(i));
        return Tensor.Builder.of(TensorType.empty).cell((valueAggregator.aggregatedValue())).build();
    }

    static abstract class ValueAggregator {

        static ValueAggregator ofType(Aggregator aggregator) {
            switch (aggregator) {
                case avg : return new AvgAggregator();
                case count : return new CountAggregator();
                case max : return new MaxAggregator();
                case median : return new MedianAggregator();
                case min : return new MinAggregator();
                case prod : return new ProdAggregator();
                case sum : return new SumAggregator();
                default: throw new UnsupportedOperationException("Aggregator " + aggregator + " is not implemented");
            }

        }

        /** Add a new value to those aggregated by this */
        public abstract void aggregate(double value);

        /** Returns the value aggregated by this */
        public abstract double aggregatedValue();

        /** Resets the aggregator */
        public abstract void reset();

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

        @Override
        public void reset() {
            valueCount = 0;
            valueSum = 0.0;
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

        @Override
        public void reset() {
            valueCount = 0;
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

        @Override
        public void reset() {
            maxValue = Double.MIN_VALUE;
        }
    }

    private static class MedianAggregator extends ValueAggregator {

        /** If any NaN is added, the result should be NaN */
        private boolean isNaN = false;

        private List<Double> values = new ArrayList<>();

        @Override
        public void aggregate(double value) {
            if ( Double.isNaN(value))
                isNaN = true;
            if ( ! isNaN)
                values.add(value);
        }

        @Override
        public double aggregatedValue() {
            if (isNaN || values.isEmpty()) return Double.NaN;
            Collections.sort(values);
            if (values.size() % 2 == 0) // even: average the two middle values
                return ( values.get(values.size() / 2 - 1) + values.get(values.size() / 2) ) / 2;
            else
                return values.get((values.size() - 1)/ 2);
        }

        @Override
        public void reset() {
            isNaN = false;
            values = new ArrayList<>();
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

        @Override
        public void reset() {
            minValue = Double.MAX_VALUE;
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

        @Override
        public void reset() {
            valueProd = 1.0;
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

        @Override
        public void reset() {
            valueSum = 0.0;
        }
    }

}
