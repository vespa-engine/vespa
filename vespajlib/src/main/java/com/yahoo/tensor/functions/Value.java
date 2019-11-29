// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
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
public class Value<NAMETYPE extends Name> extends PrimitiveTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> argument;
    private final List<DimensionValue<NAMETYPE>> cellAddress;

    /**
     * Creates a value function
     *
     * @param argument the tensor to return a cell value from
     * @param cellAddress a description of the address of the cell to return the value of. This is not a TensorAddress
     *                    because those require a type, but a type is not resolved until this is evaluated
     */
    public Value(TensorFunction<NAMETYPE> argument, List<DimensionValue<NAMETYPE>> cellAddress) {
        this.argument = Objects.requireNonNull(argument, "Argument cannot be null");
        if (cellAddress.size() > 1 && cellAddress.stream().anyMatch(c -> c.dimension().isEmpty()))
            throw new IllegalArgumentException("Short form of cell addresses is only supported with a single dimension: " +
                                               "Specify dimension names explicitly");
        this.cellAddress = cellAddress;
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return List.of(argument); }

    @Override
    public Value<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if (arguments.size() != 1)
            throw new IllegalArgumentException("Value takes exactly one argument but got " + arguments.size());
        return new Value<>(arguments.get(0), cellAddress);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() { return this; }

    @Override
    public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor tensor = argument.evaluate(context);
        if (tensor.type().rank() != cellAddress.size())
            throw new IllegalArgumentException("Type/address size mismatch: Cannot address a value with " + toString() +
                                               " to a tensor of type " + tensor.type());
        TensorAddress.Builder b = new TensorAddress.Builder(tensor.type());
        for (int i = 0; i < cellAddress.size(); i++) {
            if (cellAddress.get(i).label().isPresent())
                b.add(cellAddress.get(i).dimension().orElse(tensor.type().dimensions().get(i).name()),
                      cellAddress.get(i).label().get());
            else
                b.add(cellAddress.get(i).dimension().orElse(tensor.type().dimensions().get(i).name()),
                      String.valueOf(cellAddress.get(i).index().get().apply(context).intValue()));
        }
        return Tensor.from(tensor.get(b.build()));
    }

    @Override
    public TensorType type(TypeContext<NAMETYPE> context) {
        return new TensorType.Builder(argument.type(context).valueType()).build();
    }

    @Override
    public String toString(ToStringContext context) {
        StringBuilder b = new StringBuilder(argument.toString());
        if (cellAddress.size() == 1 && cellAddress.get(0).dimension().isEmpty()) {
            if (cellAddress.get(0).index().isPresent())
                b.append("[").append(cellAddress.get(0).index().get()).append("]");
            else
                b.append("{").append(cellAddress.get(0).label()).append("}");
        }
        else {
            b.append("{").append(cellAddress.stream().map(i -> i.toString()).collect(Collectors.joining(", "))).append("}");
        }
        return b.toString();
    }

    public static class DimensionValue<NAMETYPE extends Name>  {

        private final Optional<String> dimension;

        /** The label of this, or null if index is set */
        private final String label;

        /** The function returning the index of this, or null if label is set */
        private final ScalarFunction<NAMETYPE> index;

        public DimensionValue(String dimension, String label) {
            this(Optional.of(dimension), label, null);
        }

        public DimensionValue(String dimension, int index) {
            this(Optional.of(dimension), null, new ConstantIntegerFunction<>(index));
        }

        public DimensionValue(int index) {
            this(Optional.empty(), null, new ConstantIntegerFunction<>(index));
        }

        public DimensionValue(String label) {
            this(Optional.empty(), label, null);
        }

        public DimensionValue(ScalarFunction<NAMETYPE> index) {
            this(Optional.empty(), null, index);
        }

        public DimensionValue(Optional<String> dimension, String label) {
            this(dimension, label, null);
        }

        public DimensionValue(Optional<String> dimension, ScalarFunction<NAMETYPE> index) {
            this(dimension, null, index);
        }

        public DimensionValue(String dimension, ScalarFunction<NAMETYPE> index) {
            this(Optional.of(dimension), null, index);
        }

        private DimensionValue(Optional<String> dimension, String label, ScalarFunction<NAMETYPE> index) {
            this.dimension = dimension;
            this.label = label;
            this.index = index;
        }

        /**
         * Returns the given name of the dimension, or null if dense form is used, such that name
         * must be inferred from order
         */
        public Optional<String> dimension() { return dimension; }

        /** Returns the label for this dimension or empty if it is provided by an index function */
        public Optional<String> label() { return Optional.ofNullable(label); }

        /** Returns the index expression for this dimension, or empty if it is not a number */
        public Optional<ScalarFunction<NAMETYPE>> index() { return Optional.ofNullable(index); }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            dimension.ifPresent(d -> b.append(d).append(":"));
            if (label != null)
                b.append(label);
            else
                b.append(index);
            return b.toString();
        }

    }

    private static class ConstantIntegerFunction<NAMETYPE extends Name> implements ScalarFunction<NAMETYPE> {

        private final int value;

        public ConstantIntegerFunction(int value) {
            this.value = value;
        }

        @Override
        public Double apply(EvaluationContext<NAMETYPE> context) {
            return (double)value;
        }

        @Override
        public String toString() { return String.valueOf(value); }

    }

}
