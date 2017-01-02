package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;

import java.util.*;
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
    public List<TensorFunction> functionArguments() { return ImmutableList.of(argumentA, argumentB); }

    @Override
    public TensorFunction replaceArguments(List<TensorFunction> arguments) {
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
    public Tensor evaluate(EvaluationContext context) {
        Tensor a = argumentA.evaluate(context);
        Tensor b = argumentB.evaluate(context);
        a = ensureIndexedDimension(dimension, a);
        b = ensureIndexedDimension(dimension, b);

        IndexedTensor aIndexed = (IndexedTensor) a; // If you get an exception here you have implemented a mixed tensor
        IndexedTensor bIndexed = (IndexedTensor) b;

        TensorType concatType = concatType(a, b);
        int[] concatSize = concatSize(concatType, aIndexed, bIndexed, dimension);

        Tensor.Builder builder = Tensor.Builder.of(concatType, concatSize);
        int aDimensionLength = aIndexed.type().indexOfDimension(dimension).map(aIndexed::size).orElseThrow(RuntimeException::new);
        int[] aToIndexes = mapIndexes(a.type(), concatType);
        int[] bToIndexes = mapIndexes(b.type(), concatType);
        System.out.println("Concatenating " + a + " to " + b);
        concatenateTo(aIndexed, bIndexed, aDimensionLength, concatType, aToIndexes, bToIndexes, builder);
        System.out.println("Concatenating " + b + " to " + a);
        concatenateTo(bIndexed, aIndexed, 0, concatType, bToIndexes, aToIndexes, builder);
        return builder.build();
    }
    
    private void concatenateTo(IndexedTensor a, IndexedTensor b, int offset, TensorType concatType,
                               int[] aToIndexes, int[] bToIndexes, Tensor.Builder builder) {
        Set<String> otherADimensions = a.type().dimensionNames().stream().filter(d -> !d.equals(dimension)).collect(Collectors.toSet());
        for (Iterator<IndexedTensor.SubspaceIterator> ia = a.subspaceIterator(otherADimensions); ia.hasNext();) {
            IndexedTensor.SubspaceIterator iaSubspace = ia.next();
            TensorAddress aAddress = iaSubspace.address();
            for (Iterator<IndexedTensor.SubspaceIterator> ib = b.subspaceIterator(otherADimensions); ib.hasNext();) {
                IndexedTensor.SubspaceIterator ibSubspace = ib.next();
                System.out.println("  Producing concatenation along '" + dimension + " starting at b address" + ibSubspace.address());
                while (ibSubspace.hasNext()) {
                    java.util.Map.Entry<TensorAddress, Double> bCell = ibSubspace.next(); // TODO: Create Cell convenience subclass for Map.Entry
                    TensorAddress combinedAddress = combineAddresses(aAddress, aToIndexes, bCell.getKey(), bToIndexes,
                                                                     concatType, offset, dimension);
                    if (combinedAddress == null) continue; // incompatible

                    System.out.println("    Setting " + combinedAddress + " = " +  bCell.getValue());
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

    /** Returns the type resulting from concatenating a and b */
    private TensorType concatType(Tensor a, Tensor b) {
        TensorType.Builder builder = new TensorType.Builder(a.type(), b.type());
        if (builder.getDimension(dimension).get().size().isPresent()) // both types have size: correct to concat size
            builder.set(TensorType.Dimension.indexed(dimension, a.type().dimension(dimension).get().size().get() +
                                                                b.type().dimension(dimension).get().size().get()));
        return builder.build();
    }

    /** Returns the  concrete (not type) dimension sizes resulting from combining a and b */
    private int[] concatSize(TensorType concatType, IndexedTensor a, IndexedTensor b, String concatDimension) {
        int[] joinedSizes = new int[concatType.dimensions().size()];
        for (int i = 0; i < joinedSizes.length; i++) {
            String currentDimension = concatType.dimensions().get(i).name();
            int aSize = a.type().indexOfDimension(currentDimension).map(a::size).orElse(0);
            int bSize = b.type().indexOfDimension(currentDimension).map(b::size).orElse(0);
            if (currentDimension.equals(concatDimension))
                joinedSizes[i] = aSize + bSize;
            else
                joinedSizes[i] = Math.max(aSize, bSize);
        }
        return joinedSizes;
    }

    /**
     * Combine two addresses, adding the offset to the concat dimension
     *
     * @return the combined address or null if the addresses are incompatible 
     *         (in some other dimension than the concat dimension)
     */
    private TensorAddress combineAddresses(TensorAddress a, int[] aToIndexes, TensorAddress b, int[] bToIndexes,
                                           TensorType concatType, int concatOffset, String concatDimension) {
        String[] joinedLabels = new String[concatType.dimensions().size()];
        int concatDimensionIndex = concatType.indexOfDimension(concatDimension).get();
        mapContent(a, joinedLabels, aToIndexes, concatDimensionIndex, concatOffset); // note: This sets a nonsensical value in the concat dimension
        boolean compatible = mapContent(b, joinedLabels, bToIndexes, concatDimensionIndex, concatOffset); // ... which is overwritten by the right value here
        if ( ! compatible) return null;
        return TensorAddress.of(joinedLabels);
    }

    /**
     * Returns the an array having one entry in order for each dimension of fromType
     * containing the index at which toType contains the same dimension name.
     * That is, if the returned array contains n at index i then 
     * fromType.dimensions().get(i).name.equals(toType.dimensions().get(n).name())
     * If some dimension in fromType is not present in toType, the corresponding index will be -1
     */
    // TODO: Stolen from join - put on TensorType?
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
    private boolean mapContent(TensorAddress from, String[] to, int[] indexMap, int concatDimension, int concatOffset) {
        for (int i = 0; i < from.size(); i++) {
            int toIndex = indexMap[i];
            if (concatDimension == toIndex) {
                to[toIndex] = String.valueOf(from.intLabel(i) + concatOffset);
            }
            else {
                if (to[toIndex] != null && !to[toIndex].equals(from.label(i))) return false;
                to[toIndex] = from.label(i);
            }
        }
        return true;
    }

}
