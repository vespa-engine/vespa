// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Returns the value of a cell of a tensor (as a rank 0 tensor).
 *
 * @author bratseth
 */
@Beta
public class Value extends PrimitiveTensorFunction {

    private final TensorFunction argument;
    private final List<DimensionValue> cellAddress;

    /**
     * Creates a value function
     *
     * @param argument the tensor to return a cell value from
     * @param cellAddress a description of the address of the cell to return the value of. This is not a TensorAddress
     *                    because those require a type, but a type is not resolved until this is evaluated
     */
    public Value(TensorFunction argument, List<DimensionValue> cellAddress) {
        this.argument = Objects.requireNonNull(argument, "Argument cannot be null");
        if (cellAddress.size() > 1 && cellAddress.stream().anyMatch(c -> c.dimension().isEmpty()))
            throw new IllegalArgumentException("Short form of cell addresses is only supported with a single dimension: " +
                                               "Specify dimension names explicitly");
        this.cellAddress = cellAddress;
    }

    @Override
    public List<TensorFunction> arguments() { return List.of(argument); }

    @Override
    public Value withArguments(List<TensorFunction> arguments) {
        if (arguments.size() != 1)
            throw new IllegalArgumentException("Value takes exactly one argument but got " + arguments.size());
        return new Value(arguments.get(0), cellAddress);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() { return this; }

    @Override
    public <NAMETYPE extends TypeContext.Name> Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor tensor = argument.evaluate(context);
        if (tensor.type().rank() != cellAddress.size())
            throw new IllegalArgumentException("Type/address size mismatch: Cannot address a value with " + toString() +
                                               " to a tensor of type " + tensor.type());
        TensorAddress.Builder b = new TensorAddress.Builder(tensor.type());
        for (int i = 0; i < cellAddress.size(); i++) {
            b.add(cellAddress.get(i).dimension().orElse(tensor.type().dimensions().get(i).name()),
                  cellAddress.get(i).label());
        }
        return Tensor.from(tensor.get(b.build()));
    }

    @Override
    public <NAMETYPE extends TypeContext.Name> TensorType type(TypeContext<NAMETYPE> context) {
        return new TensorType.Builder(argument.type(context).valueType()).build();
    }

    @Override
    public String toString(ToStringContext context) {
        return toString();
    }

    @Override
    public String toString() {
        if (cellAddress.size() == 1 && cellAddress.get(0).dimension().isEmpty()) {
            if (cellAddress.get(0).index().isPresent())
                return "[" + cellAddress.get(0).index().get() + "]";
            else
                return "{" + cellAddress.get(0).label() + "}";
        }
        else {
            return "{" + cellAddress.stream().map(i -> i.toString()).collect(Collectors.joining(", ")) + "}";
        }
   }

   public static class DimensionValue {

       private final Optional<String> dimension;

       /** The label of this. Always available, whether or not index is */
       private final String label;

       /** The index of this, or empty if this is a non-integer label */
       private final Optional<Integer> index;

       public DimensionValue(String dimension, String label) {
           this(Optional.of(dimension), label, indexOrEmpty(label));
       }

       public DimensionValue(String dimension, int index) {
           this(Optional.of(dimension), String.valueOf(index), Optional.of(index));
       }

       public DimensionValue(int index) {
           this(Optional.empty(), String.valueOf(index), Optional.of(index));
       }

       public DimensionValue(String label) {
           this(Optional.empty(), label, indexOrEmpty(label));
       }

       private DimensionValue(Optional<String> dimension, String label, Optional<Integer> index) {
            this.dimension = dimension;
            this.label = label;
            this.index = index;
       }

       /**
        * Returns the given name of the dimension, or null if dense form is used, such that name
        * must be inferred from order
        */
       public Optional<String> dimension() { return dimension; }

       /** Returns the label or index for this dimension as a string */
       public String label() { return label; }

       /** Returns the index for this dimension, or empty if it is not a number */
       Optional<Integer> index() { return index; }

       @Override
       public String toString() {
           if (dimension.isPresent())
               return dimension.get() + ":" + label;
           else
               return label;
       }

       private static Optional<Integer> indexOrEmpty(String label) {
           try {
               return Optional.of(Integer.parseInt(label));
           }
           catch (IllegalArgumentException e) {
               return Optional.empty();
           }
       }

   }

}
