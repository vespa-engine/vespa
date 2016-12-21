package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;

import java.util.Arrays;
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

    public TensorFunction argumentA() { return argumentA; }
    public TensorFunction argumentB() { return argumentB; }
    public DoubleBinaryOperator combinator() { return combinator; }

    @Override
    public List<TensorFunction> functionArguments() { return ImmutableList.of(argumentA, argumentB); }

    @Override
    public TensorFunction replaceArguments(List<TensorFunction> arguments) {
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
    public Tensor evaluate(EvaluationContext context) {
        Tensor a = argumentA.evaluate(context);
        Tensor b = argumentB.evaluate(context);
        TensorType joinedType = a.type().combineWith(b.type());

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
        int joinedLength = Math.min(a.size(0), b.size(0));
        Iterator<Double> aIterator = a.valueIterator();
        Iterator<Double> bIterator = b.valueIterator();
        IndexedTensor.Builder builder = IndexedTensor.Builder.of(type, new int[] { joinedLength});
        for (int i = 0; i < joinedLength; i++)
            builder.cell(combinator.applyAsDouble(aIterator.next(), bIterator.next()), i);
        return builder.build();
    }

    /** When both tensors have the same dimensions, at most one cell matches a cell in the other tensor */
    private Tensor singleSpaceJoin(Tensor a, Tensor b, TensorType joinedType) {
        Tensor.Builder builder = Tensor.Builder.of(joinedType);
        for (Iterator<Map.Entry<TensorAddress, Double>> i = a.cellIterator(); i.hasNext(); ) {
            Map.Entry<TensorAddress, Double> aCell = i.next();
            double bCellValue = b.get(aCell.getKey());
            if (Double.isNaN(bCellValue)) continue; // no match
            builder.cell(aCell.getKey(), combinator.applyAsDouble(aCell.getValue(), bCellValue));
        }
        return builder.build();
    }
    
    /** Join a tensor into a superspace */
    private Tensor subspaceJoin(Tensor subspace, Tensor superspace, TensorType joinedType, boolean reversedArgumentOrder) {
        if (subspace.type().isIndexed() && superspace.type().isIndexed())
            return indexedSubspaceJoin((IndexedTensor) subspace, (IndexedTensor) superspace, joinedType, reversedArgumentOrder);
        else
            return generalSubspaceJoin(subspace, superspace, joinedType, reversedArgumentOrder);
    }

    private Tensor indexedSubspaceJoin(IndexedTensor subspace, IndexedTensor superspace, TensorType joinedType, boolean reversedArgumentOrder) {
        if (subspace.size() == 0 || superspace.size() == 0) // special case empty here to avoid doing it when finding sizes
            return Tensor.Builder.of(joinedType, new int[joinedType.dimensions().size()]).build();
        
        // Find size of joined tensor
        int[] joinedSizes = new int[joinedType.dimensions().size()];
        for (int i = 0; i < joinedSizes.length; i++) {
            Optional<Integer> subspaceIndex = subspace.type().indexOfDimension(joinedType.dimensions().get(i).name());
            if (subspaceIndex.isPresent())
                joinedSizes[i] = Math.min(superspace.size(i), subspace.size(subspaceIndex.get()));
            else
                joinedSizes[i] = superspace.size(i);
        }

        Tensor.Builder builder = Tensor.Builder.of(joinedType, joinedSizes);

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

    private Tensor generalSubspaceJoin(Tensor subspace, Tensor superspace, TensorType joinedType, boolean reversedArgumentOrder) {
        int[] subspaceIndexes = subspaceIndexes(superspace.type(), subspace.type());
        Tensor.Builder builder = Tensor.Builder.of(joinedType);
        for (Iterator<Map.Entry<TensorAddress, Double>> i = superspace.cellIterator(); i.hasNext(); ) {
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

    private void joinSubspaces(Iterator<Double> subspace, int subspaceSize, 
                               Iterator<Map.Entry<TensorAddress, Double>> superspace, int superspaceSize,
                               boolean reversedArgumentOrder, Tensor.Builder builder) {
        int joinedLength = Math.min(subspaceSize, superspaceSize);
        for (int i = 0; i < joinedLength; i++) {
            Double subvalue = subspace.next();
            Map.Entry<TensorAddress, Double> supercell = superspace.next();
            builder.cell(supercell.getKey(),
                         reversedArgumentOrder ? combinator.applyAsDouble(supercell.getValue(), subvalue)
                                               : combinator.applyAsDouble(subvalue, supercell.getValue()));
        }
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
        int[] aToIndexes = mapIndexes(a.type(), joinedType);
        int[] bToIndexes = mapIndexes(b.type(), joinedType);
        Tensor.Builder builder = Tensor.Builder.of(joinedType);
        for (Iterator<Map.Entry<TensorAddress, Double>> aIterator = a.cellIterator(); aIterator.hasNext(); ) {
            Map.Entry<TensorAddress, Double> aCell = aIterator.next();
            for (Iterator<Map.Entry<TensorAddress, Double>> bIterator = b.cellIterator(); bIterator.hasNext(); ) {
                Map.Entry<TensorAddress, Double> bCell = bIterator.next();
                TensorAddress combinedAddress = combineAddresses(aCell.getKey(), aToIndexes,
                                                                 bCell.getKey(), bToIndexes, joinedType);
                if (combinedAddress == null) continue; // not combinable
                builder.cell(combinedAddress, combinator.applyAsDouble(aCell.getValue(), bCell.getValue()));
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

    private TensorAddress combineAddresses(TensorAddress a, int[] aToIndexes, TensorAddress b, int[] bToIndexes, 
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
    
}
