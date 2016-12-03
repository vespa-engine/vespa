package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yahoo.tensor.MapTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleBinaryOperator;

/**
 * The <i>join</i> tensor operation produces a tensor from the argument tensors containing the set of cells
 * given by the cross product of the cells of the given tensors, having as values the value produced by
 * applying the given combinator function on the values from the two source cells.
 * 
 * @author bratseth
 */
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

    private final ImmutableMap.Builder<TensorAddress, Double> cells = new ImmutableMap.Builder<>();

    @Override
    public Tensor evaluate(EvaluationContext context) {
        Tensor a = argumentA.evaluate(context);
        Tensor b = argumentB.evaluate(context);
        
        TensorType joinedType = a.type().combineWith(b.type());
        int[] aToIndexes = mapIndexes(a.type(), joinedType);
        int[] bToIndexes = mapIndexes(b.type(), joinedType);        

        ImmutableMap.Builder<TensorAddress, Double> cells = new ImmutableMap.Builder<>();
        for (Map.Entry<TensorAddress, Double> aCell : a.cells().entrySet()) {
            for (Map.Entry<TensorAddress, Double> bCell : b.cells().entrySet()) {
                TensorAddress combinedAddress = combineAddresses(aCell.getKey(), aToIndexes, 
                                                                 bCell.getKey(), bToIndexes, joinedType);
                if (combinedAddress == null) continue; // not combinable
                cells.put(combinedAddress, combinator.applyAsDouble(aCell.getValue(), bCell.getValue()));
            }
        }
        return new MapTensor(joinedType, cells.build());
    }

    /**
     * Returns the an array having one entry in order for each dimension of fromType
     * containing the index at which toType contains the same dimension name.
     * That is, if the returned array contains n at index i then 
     * fromType.dimensions().get(i).name.equals(toType.dimensions().get(n).name())
     * If some dimension in fromType is not present in toType, the corresponding index will be -1
     */
    public int[] mapIndexes(TensorType fromType, TensorType toType) {
        int[] toIndexes = new int[fromType.dimensions().size()];
        for (int i = 0; i < fromType.dimensions().size(); i++)
            toIndexes[i] = toType.indexOfDimension(fromType.dimensions().get(i).name()).orElse(-1);
        return toIndexes;
    }

    private TensorAddress combineAddresses(TensorAddress a, int[] aToIndexes, TensorAddress b, int[] bToIndexes, 
                                           TensorType joinedType) {
        String[] joinedLabels = new String[joinedType.dimensions().size()];
        mapContent(a.elements(), joinedLabels, aToIndexes);
        boolean compatible = mapContent(b.elements(), joinedLabels, bToIndexes);
        if ( ! compatible) return null;
        return new TensorAddress(joinedLabels);
    }

    /** 
     * Maps the content in the given list to the given array, using the given index map.
     *
     * @return true if the mapping was successful, false if one of the destination positions was
     *         occupied by a different value
     */
    private boolean mapContent(List<String> from, String[] to, int[] indexMap) {
        for (int i = 0; i < from.size(); i++) {
            int toIndex = indexMap[i];
            if (to[toIndex] != null && ! to[toIndex].equals(from.get(i))) return false;
            to[toIndex] = from.get(i);
        }
        return true;
    }
    
}
