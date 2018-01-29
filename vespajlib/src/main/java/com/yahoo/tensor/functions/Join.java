// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.yahoo.tensor.DimensionSizes;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.PartialAddress;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;

/**
 * The <i>join</i> tensor operation produces a tensor from the argument tensors containing the set of cells
 * given by the cross product of the cells of the given tensors, having as values the value produced by
 * applying the given combinator function on the values from the two source cells.
 *
 * @author bratseth
 */
@Beta
public class Join extends PrimitiveTensorFunction {

    private final TensorFunction argumentA, argumentB;
    private final DoubleBinaryOperator combinator;

    public Join(TensorFunction argumentA, TensorFunction argumentB, DoubleBinaryOperator combinator) {
        Objects.requireNonNull(argumentA, "The first argument tensor cannot be null");
        Objects.requireNonNull(argumentB, "The second argument tensor cannot be null");
        Objects.requireNonNull(combinator, "The combinator function cannot be null");
        this.argumentA = argumentA;
        this.argumentB = argumentB;
        this.combinator = combinator;
    }

    /** Returns the type resulting from applying Join to the two given types */
    // TODO: Replace implementation by new TensorType.Builder(a.type(), b.type()).build();
    public static TensorType outputType(TensorType a, TensorType b) {
        TensorType.Builder typeBuilder = new TensorType.Builder();
        for (int i = 0; i < a.dimensions().size(); ++i) {
            TensorType.Dimension aDim = a.dimensions().get(i);
            for (int j = 0; j < b.dimensions().size(); ++j) {
                TensorType.Dimension bDim = b.dimensions().get(j);
                if (aDim.name().equals(bDim.name())) { // include
                    if (aDim.isIndexed() && bDim.isIndexed()) {
                        if (aDim.size().isPresent() || bDim.size().isPresent())
                            typeBuilder.indexed(aDim.name(), Math.min(aDim.size().orElse(Long.MAX_VALUE),
                                                                      bDim.size().orElse(Long.MAX_VALUE)));
                        else
                            typeBuilder.indexed(aDim.name());
                    }
                    else {
                        typeBuilder.mapped(aDim.name());
                    }
                }
            }
        }
        return typeBuilder.build();
    }

    public DoubleBinaryOperator combinator() { return combinator; }

    @Override
    public List<TensorFunction> arguments() { return ImmutableList.of(argumentA, argumentB); }

    @Override
    public TensorFunction withArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 2)
            throw new IllegalArgumentException("Join must have 2 arguments, got " + arguments.size());
        return new Join(arguments.get(0), arguments.get(1), combinator);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new Join(argumentA.toPrimitive(), argumentB.toPrimitive(), combinator);
    }

    @Override
    public String toString(ToStringContext context) {
        return "join(" + argumentA.toString(context) + ", " + argumentB.toString(context) + ", " + combinator + ")";
    }

    @Override
    public TensorType type(EvaluationContext context) {
        return new TensorType.Builder(argumentA.type(context), argumentB.type(context)).build();
    }

    @Override
    public Tensor evaluate(EvaluationContext context) {
        Tensor a = argumentA.evaluate(context);
        Tensor b = argumentB.evaluate(context);
        TensorType joinedType = new TensorType.Builder(a.type(), b.type()).build();

        // Choose join algorithm
        if (hasSingleIndexedDimension(a) && hasSingleIndexedDimension(b) && a.type().dimensions().get(0).name().equals(b.type().dimensions().get(0).name()))
            return indexedVectorJoin((IndexedTensor)a, (IndexedTensor)b, joinedType);
        else if (joinedType.dimensions().size() == a.type().dimensions().size() && joinedType.dimensions().size() == b.type().dimensions().size())
            return singleSpaceJoin(a, b, joinedType);
        else if (a.type().dimensions().containsAll(b.type().dimensions()))
            return subspaceJoin(b, a, joinedType, true);
        else if (b.type().dimensions().containsAll(a.type().dimensions()))
            return subspaceJoin(a, b, joinedType, false);
        else
            return generalJoin(a, b, joinedType);
    }

    private boolean hasSingleIndexedDimension(Tensor tensor) {
        return tensor.type().dimensions().size() == 1 && tensor.type().dimensions().get(0).isIndexed();
    }

    private Tensor indexedVectorJoin(IndexedTensor a, IndexedTensor b, TensorType type) {
        long joinedRank = Math.min(a.dimensionSizes().size(0), b.dimensionSizes().size(0));
        Iterator<Double> aIterator = a.valueIterator();
        Iterator<Double> bIterator = b.valueIterator();
        IndexedTensor.Builder builder = IndexedTensor.Builder.of(type, new DimensionSizes.Builder(1).set(0, joinedRank).build());
        for (int i = 0; i < joinedRank; i++)
            builder.cell(combinator.applyAsDouble(aIterator.next(), bIterator.next()), i);
        return builder.build();
    }

    /** When both tensors have the same dimensions, at most one cell matches a cell in the other tensor */
    private Tensor singleSpaceJoin(Tensor a, Tensor b, TensorType joinedType) {
        Tensor.Builder builder = Tensor.Builder.of(joinedType);
        for (Iterator<Tensor.Cell> i = a.cellIterator(); i.hasNext(); ) {
            Map.Entry<TensorAddress, Double> aCell = i.next();
            double bCellValue = b.get(aCell.getKey());
            if (Double.isNaN(bCellValue)) continue; // no match
            builder.cell(aCell.getKey(), combinator.applyAsDouble(aCell.getValue(), bCellValue));
        }
        return builder.build();
    }

    /** Join a tensor into a superspace */
    private Tensor subspaceJoin(Tensor subspace, Tensor superspace, TensorType joinedType, boolean reversedArgumentOrder) {
        if (subspace instanceof IndexedTensor && superspace instanceof IndexedTensor)
            return indexedSubspaceJoin((IndexedTensor) subspace, (IndexedTensor) superspace, joinedType, reversedArgumentOrder);
        else
            return generalSubspaceJoin(subspace, superspace, joinedType, reversedArgumentOrder);
    }

    private Tensor indexedSubspaceJoin(IndexedTensor subspace, IndexedTensor superspace, TensorType joinedType, boolean reversedArgumentOrder) {
        if (subspace.size() == 0 || superspace.size() == 0) // special case empty here to avoid doing it when finding sizes
            return Tensor.Builder.of(joinedType, new DimensionSizes.Builder(joinedType.dimensions().size()).build()).build();

        DimensionSizes joinedSizes = joinedSize(joinedType, subspace, superspace);

        IndexedTensor.Builder builder = (IndexedTensor.Builder)Tensor.Builder.of(joinedType, joinedSizes);

        // Find dimensions which are only in the supertype
        Set<String> superDimensionNames = new HashSet<>(superspace.type().dimensionNames());
        superDimensionNames.removeAll(subspace.type().dimensionNames());

        for (Iterator<IndexedTensor.SubspaceIterator> i = superspace.subspaceIterator(superDimensionNames, joinedSizes); i.hasNext(); ) {
            IndexedTensor.SubspaceIterator subspaceInSuper = i.next();
            joinSubspaces(subspace.valueIterator(), subspace.size(),
                          subspaceInSuper, subspaceInSuper.size(),
                          reversedArgumentOrder, builder);
        }

        return builder.build();
    }

    private void joinSubspaces(Iterator<Double> subspace, long subspaceSize,
                               Iterator<Tensor.Cell> superspace, long superspaceSize,
                               boolean reversedArgumentOrder, IndexedTensor.Builder builder) {
        long joinedLength = Math.min(subspaceSize, superspaceSize);
        if (reversedArgumentOrder) {
            for (int i = 0; i < joinedLength; i++) {
                Tensor.Cell supercell = superspace.next();
                builder.cell(supercell, combinator.applyAsDouble(supercell.getValue(), subspace.next()));
            }
        } else {
            for (int i = 0; i < joinedLength; i++) {
                Tensor.Cell supercell = superspace.next();
                builder.cell(supercell, combinator.applyAsDouble(subspace.next(), supercell.getValue()));
            }
        }
    }

    private DimensionSizes joinedSize(TensorType joinedType, IndexedTensor a, IndexedTensor b) {
        DimensionSizes.Builder builder = new DimensionSizes.Builder(joinedType.dimensions().size());
        for (int i = 0; i < builder.dimensions(); i++) {
            String dimensionName = joinedType.dimensions().get(i).name();
            Optional<Integer> aIndex = a.type().indexOfDimension(dimensionName);
            Optional<Integer> bIndex = b.type().indexOfDimension(dimensionName);
            if (aIndex.isPresent() && bIndex.isPresent())
                builder.set(i, Math.min(b.dimensionSizes().size(bIndex.get()), a.dimensionSizes().size(aIndex.get())));
            else if (aIndex.isPresent())
                builder.set(i, a.dimensionSizes().size(aIndex.get()));
            else if (bIndex.isPresent())
                builder.set(i, b.dimensionSizes().size(bIndex.get()));
        }
        return builder.build();
    }

    private Tensor generalSubspaceJoin(Tensor subspace, Tensor superspace, TensorType joinedType, boolean reversedArgumentOrder) {
        int[] subspaceIndexes = subspaceIndexes(superspace.type(), subspace.type());
        Tensor.Builder builder = Tensor.Builder.of(joinedType);
        for (Iterator<Tensor.Cell> i = superspace.cellIterator(); i.hasNext(); ) {
            Map.Entry<TensorAddress, Double> supercell = i.next();
            TensorAddress subaddress = mapAddressToSubspace(supercell.getKey(), subspaceIndexes);
            double subspaceValue = subspace.get(subaddress);
            if ( ! Double.isNaN(subspaceValue))
                builder.cell(supercell.getKey(),
                             reversedArgumentOrder ? combinator.applyAsDouble(supercell.getValue(), subspaceValue)
                                                   : combinator.applyAsDouble(subspaceValue, supercell.getValue()));
        }
        return builder.build();
    }

    /** Returns the indexes in the superspace type which should be retained to create the subspace type */
    private int[] subspaceIndexes(TensorType supertype, TensorType subtype) {
        int[] subspaceIndexes = new int[subtype.dimensions().size()];
        for (int i = 0; i < subtype.dimensions().size(); i++)
            subspaceIndexes[i] = supertype.indexOfDimension(subtype.dimensions().get(i).name()).get();
        return subspaceIndexes;
    }

    private TensorAddress mapAddressToSubspace(TensorAddress superAddress, int[] subspaceIndexes) {
        String[] subspaceLabels = new String[subspaceIndexes.length];
        for (int i = 0; i < subspaceIndexes.length; i++)
            subspaceLabels[i] = superAddress.label(subspaceIndexes[i]);
        return TensorAddress.of(subspaceLabels);
    }

    /** Slow join which works for any two tensors */
    private Tensor generalJoin(Tensor a, Tensor b, TensorType joinedType) {
        if (a instanceof IndexedTensor && b instanceof IndexedTensor)
            return indexedGeneralJoin((IndexedTensor) a, (IndexedTensor) b, joinedType);
        else
            return mappedHashJoin(a, b, joinedType);
    }

    private Tensor indexedGeneralJoin(IndexedTensor a, IndexedTensor b, TensorType joinedType) {
        DimensionSizes joinedSize = joinedSize(joinedType, a, b);
        Tensor.Builder builder = Tensor.Builder.of(joinedType, joinedSize);
        int[] aToIndexes = mapIndexes(a.type(), joinedType);
        int[] bToIndexes = mapIndexes(b.type(), joinedType);
        joinTo(a, b, joinedType, joinedSize, aToIndexes, bToIndexes, false, builder);
        joinTo(b, a, joinedType, joinedSize, bToIndexes, aToIndexes, true, builder);
        return builder.build();
    }

    private void joinTo(IndexedTensor a, IndexedTensor b, TensorType joinedType, DimensionSizes joinedSize,
                        int[] aToIndexes, int[] bToIndexes, boolean reversedOrder, Tensor.Builder builder) {
        Set<String> sharedDimensions = Sets.intersection(a.type().dimensionNames(), b.type().dimensionNames());
        Set<String> dimensionsOnlyInA = Sets.difference(a.type().dimensionNames(), b.type().dimensionNames());

        DimensionSizes aIterateSize = joinedSizeOf(a.type(), joinedType, joinedSize);
        DimensionSizes bIterateSize = joinedSizeOf(b.type(), joinedType, joinedSize);

        // for each combination of dimensions only in a
        for (Iterator<IndexedTensor.SubspaceIterator> ia = a.subspaceIterator(dimensionsOnlyInA, aIterateSize); ia.hasNext(); ) {
            IndexedTensor.SubspaceIterator aSubspace = ia.next();
            // for each combination of dimensions in a which is also in b
            while (aSubspace.hasNext()) {
                Tensor.Cell aCell = aSubspace.next();
                PartialAddress matchingBCells = partialAddress(a.type(), aSubspace.address(), sharedDimensions);
                // for each matching combination of dimensions ony in b
                for (IndexedTensor.SubspaceIterator bSubspace = b.cellIterator(matchingBCells, bIterateSize); bSubspace.hasNext(); ) {
                    Tensor.Cell bCell = bSubspace.next();
                    TensorAddress joinedAddress = joinAddresses(aCell.getKey(), aToIndexes, bCell.getKey(), bToIndexes, joinedType);
                    double joinedValue = reversedOrder ? combinator.applyAsDouble(bCell.getValue(), aCell.getValue())
                                                       : combinator.applyAsDouble(aCell.getValue(), bCell.getValue());
                    builder.cell(joinedAddress, joinedValue);
                }
            }
        }
    }

    private PartialAddress partialAddress(TensorType addressType, TensorAddress address, Set<String> retainDimensions) {
        PartialAddress.Builder builder = new PartialAddress.Builder(retainDimensions.size());
        for (int i = 0; i < addressType.dimensions().size(); i++)
            if (retainDimensions.contains(addressType.dimensions().get(i).name()))
                builder.add(addressType.dimensions().get(i).name(), address.numericLabel(i));
        return builder.build();
    }

    /** Returns the sizes from the joined sizes which are present in the type argument */
    private DimensionSizes joinedSizeOf(TensorType type, TensorType joinedType, DimensionSizes joinedSizes) {
        DimensionSizes.Builder builder = new DimensionSizes.Builder(type.dimensions().size());
        int dimensionIndex = 0;
        for (int i = 0; i < joinedType.dimensions().size(); i++) {
            if (type.dimensionNames().contains(joinedType.dimensions().get(i).name()))
                builder.set(dimensionIndex++, joinedSizes.size(i));
        }
        return builder.build();
    }

    private Tensor mappedGeneralJoin(Tensor a, Tensor b, TensorType joinedType) {
        int[] aToIndexes = mapIndexes(a.type(), joinedType);
        int[] bToIndexes = mapIndexes(b.type(), joinedType);
        Tensor.Builder builder = Tensor.Builder.of(joinedType);
        for (Iterator<Tensor.Cell> aIterator = a.cellIterator(); aIterator.hasNext(); ) {
            Map.Entry<TensorAddress, Double> aCell = aIterator.next();
            for (Iterator<Tensor.Cell> bIterator = b.cellIterator(); bIterator.hasNext(); ) {
                Map.Entry<TensorAddress, Double> bCell = bIterator.next();
                TensorAddress combinedAddress = joinAddresses(aCell.getKey(), aToIndexes,
                                                              bCell.getKey(), bToIndexes, joinedType);
                if (combinedAddress == null) continue; // not combinable
                builder.cell(combinedAddress, combinator.applyAsDouble(aCell.getValue(), bCell.getValue()));
            }
        }
        return builder.build();
    }

    private Tensor mappedHashJoin(Tensor a, Tensor b, TensorType joinedType) {
        TensorType commonDimensionType = commonDimensions(a, b);
        if (commonDimensionType.dimensions().isEmpty()) {
            return mappedGeneralJoin(a, b, joinedType); // fallback
        }

        boolean swapTensors = a.size() > b.size();
        if (swapTensors) {
            Tensor temp = a;
            a = b;
            b = temp;
        }

        // Map dimension indexes to common and joined type
        int[] aIndexesInCommon = mapIndexes(commonDimensionType, a.type());
        int[] bIndexesInCommon = mapIndexes(commonDimensionType, b.type());
        int[] aIndexesInJoined = mapIndexes(a.type(), joinedType);
        int[] bIndexesInJoined = mapIndexes(b.type(), joinedType);

        // Iterate once through the smaller tensor and construct a hash map for common dimensions
        Map<TensorAddress, List<Tensor.Cell>> aCellsByCommonAddress = new HashMap<>();
        for (Iterator<Tensor.Cell> cellIterator = a.cellIterator(); cellIterator.hasNext(); ) {
            Tensor.Cell aCell = cellIterator.next();
            TensorAddress partialCommonAddress = partialCommonAddress(aCell, aIndexesInCommon);
            aCellsByCommonAddress.putIfAbsent(partialCommonAddress, new ArrayList<>());
            aCellsByCommonAddress.get(partialCommonAddress).add(aCell);
        }

        // Iterate once through the larger tensor and use the hash map to find joinable cells
        Tensor.Builder builder = Tensor.Builder.of(joinedType);
        for (Iterator<Tensor.Cell> cellIterator = b.cellIterator(); cellIterator.hasNext(); ) {
            Tensor.Cell bCell = cellIterator.next();
            TensorAddress partialCommonAddress = partialCommonAddress(bCell, bIndexesInCommon);
            for (Tensor.Cell aCell : aCellsByCommonAddress.getOrDefault(partialCommonAddress, Collections.emptyList())) {
                TensorAddress combinedAddress = joinAddresses(aCell.getKey(), aIndexesInJoined,
                        bCell.getKey(), bIndexesInJoined, joinedType);
                if (combinedAddress == null) continue; // not combinable
                double combinedValue = swapTensors ?
                        combinator.applyAsDouble(bCell.getValue(), aCell.getValue()) :
                        combinator.applyAsDouble(aCell.getValue(), bCell.getValue());
                builder.cell(combinedAddress, combinedValue);
            }
        }

        return builder.build();
    }


    /**
     * Returns the an array having one entry in order for each dimension of fromType
     * containing the index at which toType contains the same dimension name.
     * That is, if the returned array contains n at index i then
     * fromType.dimensions().get(i).name.equals(toType.dimensions().get(n).name())
     * If some dimension in fromType is not present in toType, the corresponding index will be -1
     */
    private int[] mapIndexes(TensorType fromType, TensorType toType) {
        int[] toIndexes = new int[fromType.dimensions().size()];
        for (int i = 0; i < fromType.dimensions().size(); i++)
            toIndexes[i] = toType.indexOfDimension(fromType.dimensions().get(i).name()).orElse(-1);
        return toIndexes;
    }

    private TensorAddress joinAddresses(TensorAddress a, int[] aToIndexes, TensorAddress b, int[] bToIndexes,
                                        TensorType joinedType) {
        String[] joinedLabels = new String[joinedType.dimensions().size()];
        mapContent(a, joinedLabels, aToIndexes);
        boolean compatible = mapContent(b, joinedLabels, bToIndexes);
        if ( ! compatible) return null;
        return TensorAddress.of(joinedLabels);
    }

    /**
     * Maps the content in the given list to the given array, using the given index map.
     *
     * @return true if the mapping was successful, false if one of the destination positions was
     *         occupied by a different value
     */
    private boolean mapContent(TensorAddress from, String[] to, int[] indexMap) {
        for (int i = 0; i < from.size(); i++) {
            int toIndex = indexMap[i];
            if (to[toIndex] != null && ! to[toIndex].equals(from.label(i))) return false;
            to[toIndex] = from.label(i);
        }
        return true;
    }


    /**
     * Returns common dimension of a and b as a new tensor type
     */
    private TensorType commonDimensions(Tensor a, Tensor b) {
        TensorType.Builder typeBuilder = new TensorType.Builder();
        TensorType aType = a.type();
        TensorType bType = b.type();
        for (int i = 0; i < aType.dimensions().size(); ++i) {
            TensorType.Dimension aDim = aType.dimensions().get(i);
            for (int j = 0; j < bType.dimensions().size(); ++j) {
                TensorType.Dimension bDim = bType.dimensions().get(j);
                if (aDim.equals(bDim)) {
                    typeBuilder.set(bDim);
                }
            }
        }
        return typeBuilder.build();
    }

    private TensorAddress partialCommonAddress(Tensor.Cell cell, int[] indexMap) {
        TensorAddress address = cell.getKey();
        String[] labels = new String[indexMap.length];
        for (int i = 0; i < labels.length; ++i) {
            labels[i] = address.label(indexMap[i]);
        }
        return TensorAddress.of(labels);

    }

}
