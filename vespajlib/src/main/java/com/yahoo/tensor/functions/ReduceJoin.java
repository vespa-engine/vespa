// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.DimensionSizes;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleBinaryOperator;
import java.util.stream.Collectors;

/**
 * An optimization for tensor expressions where a join immediately follows a
 * reduce. Evaluating this as one operation is significantly more efficient
 * than evaluating each separately.
 *
 * This implementation optimizes the case where the reduce is done on the same
 * dimensions as the join. A particularly efficient evaluation is done if there
 * is one common dimension that is joined and reduced on, which is a common
 * case as it covers vector and matrix like multiplications.
 *
 * @author lesters
 */
public class ReduceJoin<NAMETYPE extends Name> extends CompositeTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> argumentA, argumentB;
    private final DoubleBinaryOperator combinator;
    private final Reduce.Aggregator aggregator;
    private final List<String> dimensions;

    public ReduceJoin(Reduce<NAMETYPE> reduce, Join<NAMETYPE> join) {
        this(join.arguments().get(0), join.arguments().get(1), join.combinator(), reduce.aggregator(), reduce.dimensions());
    }

    public ReduceJoin(TensorFunction<NAMETYPE> argumentA,
                      TensorFunction<NAMETYPE> argumentB,
                      DoubleBinaryOperator combinator,
                      Reduce.Aggregator aggregator,
                      List<String> dimensions) {
        this.argumentA = argumentA;
        this.argumentB = argumentB;
        this.combinator = combinator;
        this.aggregator = aggregator;
        this.dimensions = ImmutableList.copyOf(dimensions);
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() {
        return ImmutableList.of(argumentA, argumentB);
    }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 2)
            throw new IllegalArgumentException("ReduceJoin must have 2 arguments, got " + arguments.size());
        return new ReduceJoin<>(arguments.get(0), arguments.get(1), combinator, aggregator, dimensions);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        Join<NAMETYPE> join = new Join<>(argumentA.toPrimitive(), argumentB.toPrimitive(), combinator);
        return new Reduce<>(join, aggregator, dimensions);
    }

    @Override
    public final Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor a = argumentA.evaluate(context);
        Tensor b = argumentB.evaluate(context);
        TensorType joinedType = new TensorType.Builder(a.type(), b.type()).build();

        if (canOptimize(a, b)) {
            return evaluate((IndexedTensor)a, (IndexedTensor)b, joinedType);
        }
        return Reduce.evaluate(Join.evaluate(a, b, joinedType, combinator), dimensions, aggregator);
    }

    /**
     * Tests whether or not the reduce is over the join dimensions. The
     * remaining logic in this class assumes this to be true.
     *
     * If no dimensions are given, the join must be on all tensor dimensions.
     *
     * @return {@code true} if the implementation can optimize evaluation
     *         given the two tensors.
     */
    public boolean canOptimize(Tensor a, Tensor b) {
        if (a.type().dimensions().isEmpty() || b.type().dimensions().isEmpty())  // TODO: support scalars
            return false;
        if ( ! (a instanceof IndexedTensor))
            return false;
        if ( ! (a.type().dimensions().stream().allMatch(d -> d.type() == TensorType.Dimension.Type.indexedBound)))
            return false;
        if ( ! (b instanceof IndexedTensor))
            return false;
        if ( ! (b.type().dimensions().stream().allMatch(d -> d.type() == TensorType.Dimension.Type.indexedBound)))
            return false;

        TensorType commonDimensions = dimensionsInCommon((IndexedTensor)a, (IndexedTensor)b);
        if (dimensions.isEmpty()) {
            if (a.type().dimensions().size() != commonDimensions.dimensions().size())
                return false;
            if (b.type().dimensions().size() != commonDimensions.dimensions().size())
                return false;
        } else {
            for (TensorType.Dimension dimension : commonDimensions.dimensions()) {
                if (!dimensions.contains(dimension.name()))
                    return false;
            }
        }
        return true;
    }

    /**
     * Evaluates the reduce-join. Special handling for common cases where the
     * reduce dimension is the innermost dimension in both tensors.
     */
    private Tensor evaluate(IndexedTensor a, IndexedTensor b, TensorType joinedType) {
        TensorType reducedType = Reduce.outputType(joinedType, dimensions);

        if (reduceDimensionIsInnermost(a, b)) {
            if (a.type().dimensions().size() == 1 && b.type().dimensions().size() == 1) {
                return vectorVectorProduct(a, b, reducedType);
            }
            if (a.type().dimensions().size() == 1 && b.type().dimensions().size() == 2) {
                return vectorMatrixProduct(a, b, reducedType, false);
            }
            if (a.type().dimensions().size() == 2 && b.type().dimensions().size() == 1) {
                return vectorMatrixProduct(b, a, reducedType, true);
            }
            if (a.type().dimensions().size() == 2 && b.type().dimensions().size() == 2) {
                return matrixMatrixProduct(a, b, reducedType);
            }
        }
        return evaluateGeneral(a, b, reducedType);
    }

    private Tensor vectorVectorProduct(IndexedTensor a, IndexedTensor b, TensorType reducedType) {
        if ( a.type().dimensions().size() != 1 || b.type().dimensions().size() != 1) {
            throw new IllegalArgumentException("Wrong dimension sizes for tensors for vector-vector product");
        }
        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder)IndexedTensor.Builder.of(reducedType);
        long commonSize = Math.min(a.dimensionSizes().size(0), b.dimensionSizes().size(0));

        Reduce.ValueAggregator agg = Reduce.ValueAggregator.ofType(aggregator);
        for (int ic = 0; ic < commonSize; ++ic) {
            double va = a.get(ic);
            double vb = b.get(ic);
            agg.aggregate(combinator.applyAsDouble(va, vb));
        }
        builder.cellByDirectIndex(0, agg.aggregatedValue());
        return builder.build();
    }

    private Tensor vectorMatrixProduct(IndexedTensor a, IndexedTensor b, TensorType reducedType, boolean swapped) {
        if ( a.type().dimensions().size() != 1 || b.type().dimensions().size() != 2) {
            throw new IllegalArgumentException("Wrong dimension sizes for tensors for vector-matrix product");
        }
        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder)IndexedTensor.Builder.of(reducedType);
        DimensionSizes sizesA = a.dimensionSizes();
        DimensionSizes sizesB = b.dimensionSizes();

        Reduce.ValueAggregator agg = Reduce.ValueAggregator.ofType(aggregator);
        for (int ib = 0; ib < sizesB.size(0); ++ib) {
            agg.reset();
            for (int ic = 0; ic < Math.min(sizesA.size(0), sizesB.size(1)); ++ic) {
                double va = a.get(ic);
                double vb = b.get(ib * sizesB.size(1) + ic);
                double result = swapped ? combinator.applyAsDouble(vb, va) : combinator.applyAsDouble(va, vb);
                agg.aggregate(result);
            }
            builder.cellByDirectIndex(ib, agg.aggregatedValue());
        }
        return builder.build();
    }

    private Tensor matrixMatrixProduct(IndexedTensor a, IndexedTensor b, TensorType reducedType) {
        if ( a.type().dimensions().size() != 2 || b.type().dimensions().size() != 2) {
            throw new IllegalArgumentException("Wrong dimension sizes for tensors for matrix-matrix product");
        }
        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder)IndexedTensor.Builder.of(reducedType);
        DimensionSizes sizesA = a.dimensionSizes();
        DimensionSizes sizesB = b.dimensionSizes();
        int iaToReduced = reducedType.indexOfDimension(a.type().dimensions().get(0).name()).get();
        int ibToReduced = reducedType.indexOfDimension(b.type().dimensions().get(0).name()).get();
        long strideA = iaToReduced < ibToReduced ? sizesB.size(0) : 1;
        long strideB = ibToReduced < iaToReduced ? sizesA.size(0) : 1;

        Reduce.ValueAggregator agg = Reduce.ValueAggregator.ofType(aggregator);
        for (int ia = 0; ia < sizesA.size(0); ++ia) {
            for (int ib = 0; ib < sizesB.size(0); ++ib) {
                agg.reset();
                for (int ic = 0; ic < Math.min(sizesA.size(1), sizesB.size(1)); ++ic) {
                    double va = a.get(ia * sizesA.size(1) + ic);
                    double vb = b.get(ib * sizesB.size(1) + ic);
                    agg.aggregate(combinator.applyAsDouble(va, vb));
                }
                builder.cellByDirectIndex(ia * strideA + ib * strideB, agg.aggregatedValue());
            }
        }
        return builder.build();
    }

    private Tensor evaluateGeneral(IndexedTensor a, IndexedTensor b, TensorType reducedType) {
        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder)IndexedTensor.Builder.of(reducedType);
        TensorType onlyInA = Reduce.outputType(a.type(), dimensions);
        TensorType onlyInB = Reduce.outputType(b.type(), dimensions);
        TensorType common = dimensionsInCommon(a, b);

        // pre-calculate strides for each index position
        long[] stridesA = strides(a.type());
        long[] stridesB = strides(b.type());
        long[] stridesResult = strides(reducedType);

        // mapping of dimension indexes
        int[] mapOnlyAToA = Join.mapIndexes(onlyInA, a.type());
        int[] mapCommonToA = Join.mapIndexes(common, a.type());
        int[] mapOnlyBToB = Join.mapIndexes(onlyInB, b.type());
        int[] mapCommonToB = Join.mapIndexes(common, b.type());
        int[] mapOnlyAToResult = Join.mapIndexes(onlyInA, reducedType);
        int[] mapOnlyBToResult = Join.mapIndexes(onlyInB, reducedType);

        // TODO: refactor with code in IndexedTensor and Join

        MultiDimensionIterator ic = new MultiDimensionIterator(common);
        Reduce.ValueAggregator agg = Reduce.ValueAggregator.ofType(aggregator);
        for (MultiDimensionIterator ia = new MultiDimensionIterator(onlyInA); ia.hasNext(); ia.next()) {
            for (MultiDimensionIterator ib = new MultiDimensionIterator(onlyInB); ib.hasNext(); ib.next()) {
                agg.reset();
                for (ic.reset(); ic.hasNext(); ic.next()) {
                    double va = a.get(toDirectIndex(ia, ic, stridesA, mapOnlyAToA, mapCommonToA));
                    double vb = b.get(toDirectIndex(ib, ic, stridesB, mapOnlyBToB, mapCommonToB));
                    agg.aggregate(combinator.applyAsDouble(va, vb));
                }
                builder.cellByDirectIndex(toDirectIndex(ia, ib, stridesResult, mapOnlyAToResult, mapOnlyBToResult),
                                          agg.aggregatedValue());
            }
        }
        return builder.build();
    }

    private long toDirectIndex(MultiDimensionIterator iter, MultiDimensionIterator common, long[] strides, int[] map, int[] commonmap) {
        long directIndex = 0;
        for (int i = 0; i < iter.length(); ++i) {
            directIndex += strides[map[i]] * iter.iterator[i];
        }
        for (int i = 0; i < common.length(); ++i) {
            directIndex += strides[commonmap[i]] * common.iterator[i];
        }
        return directIndex;
    }

    private long[] strides(TensorType type) {
        long[] strides = new long[type.dimensions().size()];
        if (strides.length > 0) {
            long previous = 1;
            strides[strides.length - 1] = previous;
            for (int i = strides.length - 2; i >= 0; --i) {
                strides[i] = previous * type.dimensions().get(i + 1).size().get();
                previous = strides[i];
            }
        }
        return strides;
    }

    private TensorType dimensionsInCommon(IndexedTensor a, IndexedTensor b) {
        TensorType.Builder builder = new TensorType.Builder(TensorType.combinedValueType(a.type(), b.type()));
        for (TensorType.Dimension aDim : a.type().dimensions()) {
            for (TensorType.Dimension bDim : b.type().dimensions()) {
                if (aDim.name().equals(bDim.name())) {
                    if ( ! aDim.size().isPresent()) {
                        builder.set(aDim);
                    } else if ( ! bDim.size().isPresent()) {
                        builder.set(bDim);
                    } else {
                        builder.set(aDim.size().get() < bDim.size().get() ? aDim : bDim);  // minimum size of dimension
                    }
                }
            }
        }
        return builder.build();
    }

    /**
     * Tests if there is exactly one reduce dimension and it is the innermost
     * dimension in both tensors.
     */
    private boolean reduceDimensionIsInnermost(Tensor a, Tensor b) {
        List<String> reducingDimensions = dimensions;
        if (reducingDimensions.isEmpty()) {
            reducingDimensions = dimensionsInCommon((IndexedTensor)a, (IndexedTensor)b).dimensions().stream()
                    .map(TensorType.Dimension::name)
                    .collect(Collectors.toList());
        }
        if (reducingDimensions.size() != 1) {
            return false;
        }
        String dimension = reducingDimensions.get(0);
        int indexInA = a.type().indexOfDimension(dimension).orElseThrow(() ->
                new IllegalArgumentException("Reduce-Join dimension '" + dimension + "' missing in tensor A."));
        if (indexInA != (a.type().dimensions().size() - 1)) {
            return false;
        }
        int indexInB = b.type().indexOfDimension(dimension).orElseThrow(() ->
                new IllegalArgumentException("Reduce-Join dimension '" + dimension + "' missing in tensor B."));
        if (indexInB < (b.type().dimensions().size() - 1)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "reduce_join(" + argumentA.toString(context) + ", " +
                                argumentB.toString(context) + ", " +
                                combinator + ", " +
                                aggregator +
                                Reduce.commaSeparated(dimensions) + ")";
    }

    @Override
    public int hashCode() {
        return Objects.hash("reduce_join", argumentA, argumentB, combinator, aggregator, dimensions);
    }

    private static class MultiDimensionIterator {

        private final long[] bounds;
        private final long[] iterator;
        private int remaining;

        MultiDimensionIterator(TensorType type) {
            bounds = new long[type.dimensions().size()];
            iterator = new long[type.dimensions().size()];
            for (int i = 0; i < bounds.length; ++i) {
                bounds[i] = type.dimensions().get(i).size().get();
            }
            reset();
        }

        public int length() {
            return iterator.length;
        }

        public boolean hasNext() {
            return remaining > 0;
        }

        public void reset() {
            remaining = 1;
            for (int i = iterator.length - 1; i >= 0; --i) {
                iterator[i] = 0;
                remaining *= bounds[i];
            }
        }

        public void next() {
            for (int i = iterator.length - 1; i >= 0; --i) {
                iterator[i] += 1;
                if (iterator[i] < bounds[i]) {
                    break;
                }
                iterator[i] = 0;
            }
            remaining -= 1;
        }

        @Override
        public String toString() {
            return Arrays.toString(iterator);
        }

    }

}
