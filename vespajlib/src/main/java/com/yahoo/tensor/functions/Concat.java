// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.DimensionSizes;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.TypeResolver;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Concatenation of two tensors along an (indexed) dimension
 *
 * @author bratseth
 */
public class Concat<NAMETYPE extends Name> extends PrimitiveTensorFunction<NAMETYPE> {

    enum DimType { common, separate, concat }

    private final TensorFunction<NAMETYPE> argumentA, argumentB;
    private final String dimension;

    public Concat(TensorFunction<NAMETYPE> argumentA, TensorFunction<NAMETYPE> argumentB, String dimension) {
        Objects.requireNonNull(argumentA, "The first argument tensor cannot be null");
        Objects.requireNonNull(argumentB, "The second argument tensor cannot be null");
        Objects.requireNonNull(dimension, "The dimension cannot be null");
        this.argumentA = argumentA;
        this.argumentB = argumentB;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return ImmutableList.of(argumentA, argumentB); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if (arguments.size() != 2)
            throw new IllegalArgumentException("Concat must have 2 arguments, got " + arguments.size());
        return new Concat<>(arguments.get(0), arguments.get(1), dimension);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        return new Concat<>(argumentA.toPrimitive(), argumentB.toPrimitive(), dimension);
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "concat(" + argumentA.toString(context) + ", " + argumentB.toString(context) + ", " + dimension + ")";
    }

    @Override
    public int hashCode() { return Objects.hash("concat", argumentA, argumentB, dimension); }

    @Override
    public TensorType type(TypeContext<NAMETYPE> context) {
        return TypeResolver.concat(argumentA.type(context), argumentB.type(context), dimension);
    }

    @Override
    public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor a = argumentA.evaluate(context);
        Tensor b = argumentB.evaluate(context);
        if (a instanceof IndexedTensor && b instanceof IndexedTensor) {
            return oldEvaluate(a, b);
        }
        var helper = new Helper(a, b, dimension);
        return helper.result;
    }

    private Tensor oldEvaluate(Tensor a, Tensor b) {
        TensorType concatType = TypeResolver.concat(a.type(), b.type(), dimension);

        a = ensureIndexedDimension(dimension, a, concatType.valueType());
        b = ensureIndexedDimension(dimension, b, concatType.valueType());

        IndexedTensor aIndexed = (IndexedTensor) a; // If you get an exception here you have implemented a mixed tensor
        IndexedTensor bIndexed = (IndexedTensor) b;
        DimensionSizes concatSize = concatSize(concatType, aIndexed, bIndexed, dimension);

        Tensor.Builder builder = Tensor.Builder.of(concatType, concatSize);
        long aDimensionLength = aIndexed.type().indexOfDimension(dimension).map(d -> aIndexed.dimensionSizes().size(d)).orElseThrow(RuntimeException::new);
        int[] aToIndexes = mapIndexes(a.type(), concatType);
        int[] bToIndexes = mapIndexes(b.type(), concatType);
        concatenateTo(aIndexed, bIndexed, aDimensionLength, concatType, aToIndexes, bToIndexes, builder);
        concatenateTo(bIndexed, aIndexed, 0, concatType, bToIndexes, aToIndexes, builder);
        return builder.build();
    }

    private void concatenateTo(IndexedTensor a, IndexedTensor b, long offset, TensorType concatType,
                               int[] aToIndexes, int[] bToIndexes, Tensor.Builder builder) {
        Set<String> otherADimensions = a.type().dimensionNames().stream().filter(d -> !d.equals(dimension)).collect(Collectors.toSet());
        for (Iterator<IndexedTensor.SubspaceIterator> ia = a.subspaceIterator(otherADimensions); ia.hasNext();) {
            IndexedTensor.SubspaceIterator iaSubspace = ia.next();
            TensorAddress aAddress = iaSubspace.address();
            for (Iterator<IndexedTensor.SubspaceIterator> ib = b.subspaceIterator(otherADimensions); ib.hasNext();) {
                IndexedTensor.SubspaceIterator ibSubspace = ib.next();
                while (ibSubspace.hasNext()) {
                    Tensor.Cell bCell = ibSubspace.next();
                    TensorAddress combinedAddress = combineAddresses(aAddress, aToIndexes, bCell.getKey(), bToIndexes,
                                                                     concatType, offset, dimension);
                    if (combinedAddress == null) continue; // incompatible

                    builder.cell(combinedAddress, bCell.getValue());
                }
                iaSubspace.reset();
            }
        }
    }

    private Tensor ensureIndexedDimension(String dimensionName, Tensor tensor, TensorType.Value combinedValueType) {
        Optional<TensorType.Dimension> dimension = tensor.type().dimension(dimensionName);
        if ( dimension.isPresent() ) {
            if ( ! dimension.get().isIndexed())
                throw new IllegalArgumentException("Concat in dimension '" + dimensionName +
                                                   "' requires that dimension to be indexed or absent, " +
                                                   "but got a tensor with type " + tensor.type());
            return tensor;
        }
        else { // extend tensor with this dimension
            if (tensor.type().dimensions().stream().anyMatch(d -> ! d.isIndexed()))
                throw new IllegalArgumentException("Concat requires an indexed tensor, " +
                                                   "but got a tensor with type " + tensor.type());
            Tensor unitTensor = Tensor.Builder.of(new TensorType.Builder(combinedValueType)
                                                          .indexed(dimensionName, 1)
                                                          .build())
                                              .cell(1,0)
                                              .build();
            return tensor.multiply(unitTensor);
        }

    }

    /** Returns the  concrete (not type) dimension sizes resulting from combining a and b */
    private DimensionSizes concatSize(TensorType concatType, IndexedTensor a, IndexedTensor b, String concatDimension) {
        DimensionSizes.Builder concatSizes = new DimensionSizes.Builder(concatType.dimensions().size());
        for (int i = 0; i < concatSizes.dimensions(); i++) {
            String currentDimension = concatType.dimensions().get(i).name();
            long aSize = a.type().indexOfDimension(currentDimension).map(d -> a.dimensionSizes().size(d)).orElse(0L);
            long bSize = b.type().indexOfDimension(currentDimension).map(d -> b.dimensionSizes().size(d)).orElse(0L);
            if (currentDimension.equals(concatDimension))
                concatSizes.set(i, aSize + bSize);
            else if (aSize != 0 && bSize != 0 && aSize!=bSize )
                concatSizes.set(i, Math.min(aSize, bSize));
            else
                concatSizes.set(i, Math.max(aSize, bSize));
        }
        return concatSizes.build();
    }

    /**
     * Combine two addresses, adding the offset to the concat dimension
     *
     * @return the combined address or null if the addresses are incompatible
     *         (in some other dimension than the concat dimension)
     */
    private TensorAddress combineAddresses(TensorAddress a, int[] aToIndexes, TensorAddress b, int[] bToIndexes,
                                           TensorType concatType, long concatOffset, String concatDimension) {
        long[] combinedLabels = new long[concatType.dimensions().size()];
        Arrays.fill(combinedLabels, -1);
        int concatDimensionIndex = concatType.indexOfDimension(concatDimension).get();
        mapContent(a, combinedLabels, aToIndexes, concatDimensionIndex, concatOffset); // note: This sets a nonsensical value in the concat dimension
        boolean compatible = mapContent(b, combinedLabels, bToIndexes, concatDimensionIndex, concatOffset); // ... which is overwritten by the right value here
        if ( ! compatible) return null;
        return TensorAddress.of(combinedLabels);
    }

    /**
     * Returns the an array having one entry in order for each dimension of fromType
     * containing the index at which toType contains the same dimension name.
     * That is, if the returned array contains n at index i then
     * fromType.dimensions().get(i).name.equals(toType.dimensions().get(n).name())
     * If some dimension in fromType is not present in toType, the corresponding index will be -1
     */
    // TODO: Stolen from join
    private int[] mapIndexes(TensorType fromType, TensorType toType) {
        int[] toIndexes = new int[fromType.dimensions().size()];
        for (int i = 0; i < fromType.dimensions().size(); i++)
            toIndexes[i] = toType.indexOfDimension(fromType.dimensions().get(i).name()).orElse(-1);
        return toIndexes;
    }

    /**
     * Maps the content in the given list to the given array, using the given index map.
     *
     * @return true if the mapping was successful, false if one of the destination positions was
     *         occupied by a different value
     */
    private boolean mapContent(TensorAddress from, long[] to, int[] indexMap, int concatDimension, long concatOffset) {
        for (int i = 0; i < from.size(); i++) {
            int toIndex = indexMap[i];
            if (concatDimension == toIndex) {
                to[toIndex] = from.numericLabel(i) + concatOffset;
            }
            else {
                if (to[toIndex] != -1 && to[toIndex] != from.numericLabel(i)) return false;
                to[toIndex] = from.numericLabel(i);
            }
        }
        return true;
    }

    static class CellVector {
        ArrayList<Double> values = new ArrayList<>();
        void setValue(int ccDimIndex, double value) {
            while (values.size() <= ccDimIndex) {
                values.add(0.0);
            }
            values.set(ccDimIndex, value);
        }
    }

    static class CellVectorMap {
        Map<TensorAddress, CellVector> map = new HashMap<>();
        CellVector lookupCreate(TensorAddress addr) {
            return map.computeIfAbsent(addr, k -> new CellVector());
        }
    }

    static class CellVectorMapMap {
        Map<TensorAddress, CellVectorMap> map = new HashMap<>();

        CellVectorMap lookupCreate(TensorAddress addr) {
            return map.computeIfAbsent(addr, k -> new CellVectorMap());
        }

    }

    static class SplitHow {
        List<DimType> handleDims = new ArrayList<>();
        long numCommon() { return handleDims.stream().filter(t -> (t == DimType.common)).count(); }
        long numSeparate() { return handleDims.stream().filter(t -> (t == DimType.separate)).count(); }
    }

    static class ConcatPlan {

        final TensorType resultType;
        final String concatDimension;

        SplitHow splitInfoA = new SplitHow();
        SplitHow splitInfoB = new SplitHow();

        enum CombineHow { left, right, both, concat }

        List<CombineHow> combineHow = new ArrayList<>();

        void aOnly(String dimName) {
            if (dimName.equals(concatDimension)) {
                splitInfoA.handleDims.add(DimType.concat);
                combineHow.add(CombineHow.concat);
            } else {
                splitInfoA.handleDims.add(DimType.separate);
                combineHow.add(CombineHow.left);
            }
        }

        void bOnly(String dimName) {
            if (dimName.equals(concatDimension)) {
                splitInfoB.handleDims.add(DimType.concat);
                combineHow.add(CombineHow.concat);
            } else {
                splitInfoB.handleDims.add(DimType.separate);
                combineHow.add(CombineHow.right);
            }
        }

        void bothAandB(String dimName) {
            if (dimName.equals(concatDimension)) {
                splitInfoA.handleDims.add(DimType.concat);
                splitInfoB.handleDims.add(DimType.concat);
                combineHow.add(CombineHow.concat);
            } else {
                splitInfoA.handleDims.add(DimType.common);
                splitInfoB.handleDims.add(DimType.common);
                combineHow.add(CombineHow.both);
            }
        }

        ConcatPlan(TensorType aType, TensorType bType, String concatDimension) {
            this.resultType = TypeResolver.concat(aType, bType, concatDimension);
            this.concatDimension = concatDimension;
            var aDims = aType.dimensions();
            var bDims = bType.dimensions();
            int i = 0;
            int j = 0;
            while (i < aDims.size() && j < bDims.size()) {
                String aName = aDims.get(i).name();
                String bName = bDims.get(j).name();
                int cmp = aName.compareTo(bName);
                if (cmp == 0) {
                    bothAandB(aName);
                    ++i;
                    ++j;
                } else if (cmp < 0) {
                    aOnly(aName);
                    ++i;
                } else {
                    bOnly(bName);
                    ++j;
                }
            }
            while (i < aDims.size()) {
                aOnly(aDims.get(i++).name());
            }
            while (j < bDims.size()) {
                bOnly(bDims.get(j++).name());
            }
            if (combineHow.size() < resultType.rank()) {
                var idx = resultType.indexOfDimension(concatDimension);
                combineHow.add(idx.get(), CombineHow.concat);
            }
        }

    }

    static class Helper {
        ConcatPlan plan;
        Tensor result;

        Helper(Tensor a, Tensor b, String dimension) {
            this.plan = new ConcatPlan(a.type(), b.type(), dimension);
            CellVectorMapMap aData = decompose(a, plan.splitInfoA);
            CellVectorMapMap bData = decompose(b, plan.splitInfoB);
            this.result = merge(aData, bData);
        }

        static int concatDimensionSize(CellVectorMapMap data) {
            Set<Integer> sizes = new HashSet<>();
            data.map.forEach((m, cvmap) ->
                                     cvmap.map.forEach((e, vector) ->
                                                               sizes.add(vector.values.size())));
            if (sizes.isEmpty()) {
                return 1;
            }
            if (sizes.size() == 1) {
                return sizes.iterator().next();
            }
            throw new IllegalArgumentException("inconsistent size of concat dimension, had "+sizes.size()+" different values");
        }

        TensorAddress combine(TensorAddress match, TensorAddress leftOnly, TensorAddress rightOnly, int concatDimIdx) {
            String[] labels = new String[plan.resultType.rank()];
            int out = 0;
            int m = 0;
            int a = 0;
            int b = 0;
            for (var how : plan.combineHow) {
                switch (how) {
                    case left:
                        labels[out++] = leftOnly.label(a++);
                        break;
                    case right:
                        labels[out++] = rightOnly.label(b++);
                        break;
                    case both:
                        labels[out++] = match.label(m++);
                        break;
                    case concat:
                        labels[out++] = String.valueOf(concatDimIdx);
                        break;
                    default:
                        throw new IllegalArgumentException("cannot handle: "+how);
                }
            }
            return TensorAddress.of(labels);
        }

        Tensor merge(CellVectorMapMap a, CellVectorMapMap b) {
            var builder = Tensor.Builder.of(plan.resultType);
            int aConcatSize = concatDimensionSize(a);
            for (var entry : a.map.entrySet()) {
                TensorAddress common = entry.getKey();
                if (b.map.containsKey(common)) {
                    var lhs = entry.getValue();
                    var rhs = b.map.get(common);
                    lhs.map.forEach((leftOnly, leftCells) -> {
                        rhs.map.forEach((rightOnly, rightCells) -> {
                            for (int i = 0; i < leftCells.values.size(); i++) {
                                TensorAddress addr = combine(common, leftOnly, rightOnly, i);
                                builder.cell(addr, leftCells.values.get(i));
                            }
                            for (int i = 0; i < rightCells.values.size(); i++) {
                                TensorAddress addr = combine(common, leftOnly, rightOnly, i + aConcatSize);
                                builder.cell(addr, rightCells.values.get(i));
                            }
                        });
                    });
                }
            }
            return builder.build();
        }

        CellVectorMapMap decompose(Tensor input, SplitHow how) {
            var iter = input.cellIterator();
            String[] commonLabels = new String[(int)how.numCommon()];
            String[] separateLabels = new String[(int)how.numSeparate()];
            CellVectorMapMap result = new CellVectorMapMap();
            while (iter.hasNext()) {
                var cell = iter.next();
                var addr = cell.getKey();
                long ccDimIndex = 0;
                int commonIdx = 0;
                int separateIdx = 0;
                for (int i = 0; i < how.handleDims.size(); i++) {
                    switch (how.handleDims.get(i)) {
                        case common:
                            commonLabels[commonIdx++] = addr.label(i);
                            break;
                        case separate:
                            separateLabels[separateIdx++] = addr.label(i);
                            break;
                        case concat:
                            ccDimIndex = addr.numericLabel(i);
                            break;
                        default:
                            throw new IllegalArgumentException("cannot handle: "+how.handleDims.get(i));
                    }
                }
                TensorAddress commonAddr = TensorAddress.of(commonLabels);
                TensorAddress separateAddr = TensorAddress.of(separateLabels);
                result.lookupCreate(commonAddr).lookupCreate(separateAddr).setValue((int)ccDimIndex, cell.getValue());
            }
            return result;
        }
    }

}
