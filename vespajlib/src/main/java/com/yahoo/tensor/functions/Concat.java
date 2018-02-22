// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.yahoo.lang.MutableInteger;
import com.yahoo.lang.MutableLong;
import com.yahoo.tensor.DimensionSizes;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Concatenation of two tensors along an (indexed) dimension
 *
 * @author bratseth
 */
@Beta
public class Concat extends PrimitiveTensorFunction {

    private final TensorFunction argumentA, argumentB;
    private final String dimension;

    public Concat(TensorFunction argumentA, TensorFunction argumentB, String dimension) {
        Objects.requireNonNull(argumentA, "The first argument tensor cannot be null");
        Objects.requireNonNull(argumentB, "The second argument tensor cannot be null");
        Objects.requireNonNull(dimension, "The dimension cannot be null");
        this.argumentA = argumentA;
        this.argumentB = argumentB;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction> arguments() { return ImmutableList.of(argumentA, argumentB); }

    @Override
    public TensorFunction withArguments(List<TensorFunction> arguments) {
        if (arguments.size() != 2)
            throw new IllegalArgumentException("Concat must have 2 arguments, got " + arguments.size());
        return new Concat(arguments.get(0), arguments.get(1), dimension);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new Concat(argumentA.toPrimitive(), argumentB.toPrimitive(), dimension);
    }

    @Override
    public String toString(ToStringContext context) {
        return "concat(" + argumentA.toString(context) + ", " + argumentB.toString(context) + ", " + dimension + ")";
    }

    @Override
    public <NAMETYPE extends TypeContext.Name> TensorType type(TypeContext<NAMETYPE> context) {
        return type(argumentA.type(context), argumentB.type(context));
    }

    /** Returns the type resulting from concatenating a and b */
    private TensorType type(TensorType a, TensorType b) {
        // TODO: Fail if concat dimension is present but not indexed in a or b
        TensorType.Builder builder = new TensorType.Builder(a, b);
        if ( ! unboundIn(a, dimension) && ! unboundIn(b, dimension)) {
            builder.set(TensorType.Dimension.indexed(dimension, a.sizeOfDimension(dimension).orElse(1L) +
                                                                b.sizeOfDimension(dimension).orElse(1L)));
            /*
            MutableLong concatSize = new MutableLong(0);
            a.sizeOfDimension(dimension).ifPresent(concatSize::add);
            b.sizeOfDimension(dimension).ifPresent(concatSize::add);
                builder.set(TensorType.Dimension.indexed(dimension, concatSize.get()));
                */
        }
        return builder.build();
    }

    /** Returns true if this dimension is present and unbound */
    private boolean unboundIn(TensorType type, String dimensionName) {
        Optional<TensorType.Dimension> dimension = type.dimension(dimensionName);
        return dimension.isPresent() && ! dimension.get().size().isPresent();
    }

    @Override
    public <NAMETYPE extends TypeContext.Name> Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor a = argumentA.evaluate(context);
        Tensor b = argumentB.evaluate(context);
        a = ensureIndexedDimension(dimension, a);
        b = ensureIndexedDimension(dimension, b);

        IndexedTensor aIndexed = (IndexedTensor) a; // If you get an exception here you have implemented a mixed tensor
        IndexedTensor bIndexed = (IndexedTensor) b;

        TensorType concatType = type(a.type(), b.type());
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

    private Tensor ensureIndexedDimension(String dimensionName, Tensor tensor) {
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
            Tensor unitTensor = Tensor.Builder.of(new TensorType.Builder().indexed(dimensionName, 1).build()).cell(1,0).build();
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

}
