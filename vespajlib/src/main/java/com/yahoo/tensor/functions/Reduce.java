// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.DimensionSizes;
import com.yahoo.tensor.DirectIndexedAddress;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.TypeResolver;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.tensor.impl.Convert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        this(argument, aggregator, List.of());
    }

    /** Creates a reduce function reducing a single dimension */
    public Reduce(TensorFunction<NAMETYPE> argument, Aggregator aggregator, String dimension) {
        this(argument, aggregator, List.of(dimension));
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
        this.dimensions = List.copyOf(dimensions);
    }

    public static TensorType outputType(TensorType inputType, List<String> reduceDimensions) {
        return TypeResolver.reduce(inputType, reduceDimensions);
    }

    public TensorFunction<NAMETYPE> argument() { return argument; }

    Aggregator aggregator() { return aggregator; }

    List<String> dimensions() { return dimensions; }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return List.of(argument); }

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
    public String toString(ToStringContext<NAMETYPE> context) {
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
        return outputType(argument.type(context), dimensions);
    }

    @Override
    public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        return evaluate(this.argument.evaluate(context), dimensions, aggregator);
    }

    @Override
    public int hashCode() {
        return Objects.hash("reduce", argument, dimensions, aggregator);
    }

    static Tensor evaluate(Tensor argument, List<String> dimensions, Aggregator aggregator) {
        if (!dimensions.isEmpty() && !argument.type().dimensionNames().containsAll(dimensions))
            throw new IllegalArgumentException("Cannot reduce " + argument + " over dimensions " +
                    dimensions + ": Not all those dimensions are present in this tensor");

        // Special case: Reduce all
        if (dimensions.isEmpty() || dimensions.size() == argument.type().dimensions().size()) {
            if (argument.isEmpty())
                return Tensor.from(0.0);
            else if (argument.type().dimensions().size() == 1 && argument instanceof IndexedTensor)
                return reduceIndexedVector((IndexedTensor) argument, aggregator);
            else
                return reduceAllGeneral(argument, aggregator);
        }

        TensorType reducedType = outputType(argument.type(), dimensions);
        int[] indexesToReduce = createIndexesToReduce(argument.type(), dimensions);
        int[] indexesToKeep = createIndexesToKeep(argument.type(), indexesToReduce);
        if (argument instanceof IndexedTensor indexedTensor && reducedType.hasOnlyIndexedBoundDimensions()) {
            return reduceIndexedTensor(indexedTensor, reducedType, indexesToKeep, indexesToReduce, aggregator);
        } else {
            return reduceGeneral(argument, reducedType, indexesToKeep, aggregator);
        }
    }

    private static void reduce(IndexedTensor argument, ValueAggregator aggregator, DirectIndexedAddress address, int[] reduce, int reduceIndex) {
        int currentIndex = reduce[reduceIndex];
        int dimSize = Convert.safe2Int(argument.dimensionSizes().size(currentIndex));
        if (reduceIndex + 1  < reduce.length) {
            int nextDimension = reduceIndex + 1;
            for (int i = 0; i < dimSize; i++) {
                address.setIndex(currentIndex, i);
                reduce(argument, aggregator, address, reduce, nextDimension);
            }
        } else {
            address.setIndex(currentIndex, 0);
            long increment = address.getStride(currentIndex);
            long directIndex = address.getDirectIndex();
            for (int i = 0; i < dimSize; i++) {
                aggregator.aggregate(argument.get(directIndex + i * increment));
            }
        }
    }

    private static void reduce(IndexedTensor.Builder builder, DirectIndexedAddress destAddress, IndexedTensor argument, Aggregator aggregator, DirectIndexedAddress address, int[] toKeep, int keepIndex, int[] toReduce) {
        if (keepIndex < toKeep.length) {
            int currentIndex = toKeep[keepIndex];
            int dimSize = Convert.safe2Int(argument.dimensionSizes().size(currentIndex));

            int nextKeep = keepIndex + 1;
            for (int i = 0; i < dimSize; i++) {
                address.setIndex(currentIndex, i);
                destAddress.setIndex(keepIndex, i);
                reduce(builder, destAddress, argument, aggregator, address, toKeep, nextKeep, toReduce);
            }
        } else {
            ValueAggregator valueAggregator = ValueAggregator.ofType(aggregator);
            reduce(argument, valueAggregator, address, toReduce, 0);
            builder.cell(valueAggregator.aggregatedValue(), destAddress.getIndexes());
        }

    }

    private static Tensor reduceIndexedTensor(IndexedTensor argument, TensorType reducedType, int[] indexesToKeep, int[] indexesToReduce, Aggregator aggregator) {

        var reducedBuilder = IndexedTensor.Builder.of(reducedType);
        DirectIndexedAddress reducedAddress = DirectIndexedAddress.of(DimensionSizes.of(reducedType));
        reduce(reducedBuilder, reducedAddress, argument, aggregator, argument.directAddress(), indexesToKeep, 0, indexesToReduce);
        return reducedBuilder.build();
    }

    private static Tensor reduceGeneral(Tensor argument, TensorType reducedType, int[] indexesToKeep, Aggregator aggregator) {
        // TODO cells.size() is most likely an overestimate, and might need a better heuristic
        // But the upside is larger than the downside.
        Map<TensorAddress, ValueAggregator> aggregatingCells = new HashMap<>(argument.sizeAsInt());
        for (Iterator<Tensor.Cell> i = argument.cellIterator(); i.hasNext(); ) {
            Map.Entry<TensorAddress, Double> cell = i.next();
            TensorAddress reducedAddress = cell.getKey().partialCopy(indexesToKeep);
            ValueAggregator aggr = aggregatingCells.computeIfAbsent(reducedAddress, (key) ->ValueAggregator.ofType(aggregator));
            aggr.aggregate(cell.getValue());
        }
        Tensor.Builder reducedBuilder = Tensor.Builder.of(reducedType);
        for (Map.Entry<TensorAddress, ValueAggregator> aggregatingCell : aggregatingCells.entrySet())
            reducedBuilder.cell(aggregatingCell.getKey(), aggregatingCell.getValue().aggregatedValue());

        return reducedBuilder.build();
    }

    private static int[] createIndexesToReduce(TensorType tensorType, List<String> dimensions) {
        int[] indexesToReduce = new int[dimensions.size()];
        for (int i = 0; i < dimensions.size(); i++) {
            indexesToReduce[i] = tensorType.indexOfDimension(dimensions.get(i)).get();
        }
        return indexesToReduce;
    }
    private static int[] createIndexesToKeep(TensorType argumentType, int[] indexesToReduce) {
        int[] indexesToKeep = new int[argumentType.rank() - indexesToReduce.length];
        int toKeepIndex = 0;
        for (int i = 0; i < argumentType.rank(); i++) {
            if ( ! contains(indexesToReduce, i))
                indexesToKeep[toKeepIndex++] = i;
        }
        return indexesToKeep;
    }
    private static boolean contains(int[] list, int key) {
        for (int candidate : list) {
            if (candidate == key) return true;
        }
        return false;
    }

    private static Tensor reduceAllGeneral(Tensor argument, Aggregator aggregator) {
        ValueAggregator valueAggregator = ValueAggregator.ofType(aggregator);
        for (Iterator<Double> i = argument.valueIterator(); i.hasNext(); )
            valueAggregator.aggregate(i.next());
        return Tensor.Builder.of(TensorType.empty).cell(valueAggregator.aggregatedValue()).build();
    }

    private static Tensor reduceIndexedVector(IndexedTensor argument, Aggregator aggregator) {
        ValueAggregator valueAggregator = ValueAggregator.ofType(aggregator);
        int dimensionSize = Convert.safe2Int(argument.dimensionSizes().size(0));
        for (int i = 0; i < dimensionSize ; i++)
            valueAggregator.aggregate(argument.get(i));
        return Tensor.Builder.of(TensorType.empty).cell(valueAggregator.aggregatedValue()).build();
    }

    static abstract class ValueAggregator {

        static ValueAggregator ofType(Aggregator aggregator) {
            return switch (aggregator) {
                case avg -> new AvgAggregator();
                case count -> new CountAggregator();
                case max -> new MaxAggregator();
                case median -> new MedianAggregator();
                case min -> new MinAggregator();
                case prod -> new ProdAggregator();
                case sum -> new SumAggregator();
                default -> throw new UnsupportedOperationException("Aggregator " + aggregator + " is not implemented");
            };

        }

        /** Add a new value to those aggregated by this */
        public abstract void aggregate(double value);

        /** Returns the value aggregated by this */
        public abstract double aggregatedValue();

        /** Resets the aggregator */
        public abstract void reset();

        /** Returns a hash of this aggregator which only depends on its identity */
        @Override
        public abstract int hashCode();

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

        @Override
        public int hashCode() { return "avgAggregator".hashCode(); }

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

        @Override
        public int hashCode() { return "countAggregator".hashCode(); }

    }

    private static class MaxAggregator extends ValueAggregator {

        private double maxValue = Double.NEGATIVE_INFINITY;

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
            maxValue = Double.NEGATIVE_INFINITY;
        }

        @Override
        public int hashCode() { return "maxAggregator".hashCode(); }

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

        @Override
        public int hashCode() { return "medianAggregator".hashCode(); }

    }

    private static class MinAggregator extends ValueAggregator {

        private double minValue = Double.POSITIVE_INFINITY;

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
            minValue = Double.POSITIVE_INFINITY;
        }

        @Override
        public int hashCode() { return "minAggregator".hashCode(); }

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

        @Override
        public int hashCode() { return "prodAggregator".hashCode(); }

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

        @Override
        public int hashCode() { return "sumAggregator".hashCode(); }

    }

}
