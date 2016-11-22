package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.yahoo.tensor.MapTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;

import java.util.ArrayList;
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
public class JoinFunction extends PrimitiveTensorFunction {
    
    private final TensorFunction argumentA, argumentB;
    private final DoubleBinaryOperator combinator;

    public JoinFunction(TensorFunction argumentA, TensorFunction argumentB, DoubleBinaryOperator combinator) {
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
    public PrimitiveTensorFunction toPrimitive() {
        return new JoinFunction(argumentA.toPrimitive(), argumentB.toPrimitive(), combinator);
    }

    @Override
    public String toString() {
        return "join(" + argumentA.toString() + ", " + argumentB.toString() + ", f(a, b) (" + combinator + "))";
    }

    private final ImmutableMap.Builder<TensorAddress, Double> cells = new ImmutableMap.Builder<>();

    @Override
    public Tensor execute() {
        Tensor a = argumentA.execute();
        Tensor b = argumentB.execute();
        
        // Dimension product
        Set<String> dimensions = combineDimensions(a, b);

        // Cell product (slow baseline implementation)
        ImmutableMap.Builder<TensorAddress, Double> cells = new ImmutableMap.Builder<>();
        for (Map.Entry<TensorAddress, Double> aCell : a.cells().entrySet()) {
            for (Map.Entry<TensorAddress, Double> bCell : b.cells().entrySet()) {
                TensorAddress combinedAddress = combineAddresses(aCell.getKey(), bCell.getKey());
                if (combinedAddress == null) continue; // not combinable
                cells.put(combinedAddress, combinator.applyAsDouble(aCell.getValue(), bCell.getValue()));
            }
        }
        
        return new MapTensor(dimensions, cells.build());
    }

    private Set<String> combineDimensions(Tensor a, Tensor b) {
        ImmutableSet.Builder<String> setBuilder = new ImmutableSet.Builder<>();
        setBuilder.addAll(a.dimensions());
        setBuilder.addAll(b.dimensions());
        return setBuilder.build();
    }

    private TensorAddress combineAddresses(TensorAddress a, TensorAddress b) {
        List<TensorAddress.Element> combined = new ArrayList<>(a.elements());
        for (TensorAddress.Element bElement : b.elements()) {
            Optional<String> aLabel = a.labelOfDimension(bElement.dimension());
            if ( ! aLabel.isPresent())
                combined.add(bElement);
            else if ( ! aLabel.get().equals(bElement.label()))
                return null; // incompatible
        }
        return TensorAddress.fromUnsorted(combined);
    }
    
}
