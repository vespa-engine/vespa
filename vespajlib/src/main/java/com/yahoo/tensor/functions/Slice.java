// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;
import com.yahoo.tensor.PartialAddress;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Returns a subspace of a tensor
 *
 * @author bratseth
 */
@Beta
public class Slice<NAMETYPE extends Name> extends PrimitiveTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> argument;
    private final List<DimensionValue<NAMETYPE>> subspaceAddress;

    /**
     * Creates a value function
     *
     * @param argument the tensor to return a cell value from
     * @param subspaceAddress a description of the address of the cell to return the value of. This is not a TensorAddress
     *                        because those require a type, but a type is not resolved until this is evaluated
     */
    public Slice(TensorFunction<NAMETYPE> argument, List<DimensionValue<NAMETYPE>> subspaceAddress) {
        this.argument = Objects.requireNonNull(argument, "Argument cannot be null");
        if (subspaceAddress.size() > 1 && subspaceAddress.stream().anyMatch(c -> c.dimension().isEmpty()))
            throw new IllegalArgumentException("Short form of subspace addresses is only supported with a single dimension: " +
                                               "Specify dimension names explicitly instead");
        this.subspaceAddress = subspaceAddress;
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return List.of(argument); }

    @Override
    public Slice<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if (arguments.size() != 1)
            throw new IllegalArgumentException("Value takes exactly one argument but got " + arguments.size());
        return new Slice<>(arguments.get(0), subspaceAddress);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() { return this; }

    @Override
    public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor tensor = argument.evaluate(context);
        TensorType resultType = resultType(tensor.type());

        PartialAddress subspaceAddress = subspaceToAddress(tensor.type(), context);
        if (resultType.rank() == 0) // shortcut common case
            return Tensor.from(tensor.get(subspaceAddress.asAddress(tensor.type())));

        Tensor.Builder b = Tensor.Builder.of(resultType);
        for (Iterator<Tensor.Cell> i = tensor.cellIterator(); i.hasNext(); ) {
            Tensor.Cell cell = i.next();
            if (matches(subspaceAddress, cell.getKey(), tensor.type()))
                b.cell(remaining(resultType, cell.getKey(), tensor.type()), cell.getValue());
        }
        return b.build();
    }

    private PartialAddress subspaceToAddress(TensorType type, EvaluationContext<NAMETYPE> context) {
        PartialAddress.Builder b = new PartialAddress.Builder(subspaceAddress.size());
        for (int i = 0; i < subspaceAddress.size(); i++) {
            if (subspaceAddress.get(i).label().isPresent())
                b.add(subspaceAddress.get(i).dimension().orElse(type.dimensions().get(i).name()),
                      subspaceAddress.get(i).label().get());
            else
                b.add(subspaceAddress.get(i).dimension().orElse(type.dimensions().get(i).name()),
                      subspaceAddress.get(i).index().get().apply(context).intValue());
        }
        return b.build();
    }

    private boolean matches(PartialAddress subspaceAddress,
                            TensorAddress address, TensorType type) {
        for (int i = 0; i < subspaceAddress.size(); i++) {
            String label = address.label(type.indexOfDimension(subspaceAddress.dimension(i)).get());
            if ( ! label.equals(subspaceAddress.label(i)))
                return false;
        }
        return true;
    }

    /** Returns the subset of the given address which is present in the subspace type */
    private TensorAddress remaining(TensorType subspaceType, TensorAddress address, TensorType type) {
        TensorAddress.Builder b = new TensorAddress.Builder(subspaceType);
        for (int i = 0; i < address.size(); i++) {
            String dimension = type.dimensions().get(i).name();
            if (subspaceType.dimension(type.dimensions().get(i).name()).isPresent())
                b.add(dimension, address.label(i));
        }
        return b.build();
    }

    @Override
    public TensorType type(TypeContext<NAMETYPE> context) {
        return resultType(argument.type(context));
    }

    private TensorType resultType(TensorType argumentType) {
        TensorType.Builder b = new TensorType.Builder();

        // Special case where a single indexed or mapped dimension is sliced
        if (subspaceAddress.size() == 1 && subspaceAddress.get(0).dimension().isEmpty()) {
            if (subspaceAddress.get(0).index().isPresent()) {
                if (argumentType.dimensions().stream().filter(d -> d.isIndexed()).count() > 1)
                    throw new IllegalArgumentException(this + " slices a single indexed dimension, cannot be applied " +
                                                       " to " + argumentType + ", which have multiple");
                for (TensorType.Dimension dimension : argumentType.dimensions()) {
                    if ( ! dimension.isIndexed())
                        b.dimension(dimension);
                }
            }
            else {
                if (argumentType.dimensions().stream().filter(d -> ! d.isIndexed()).count() > 1)
                    throw new IllegalArgumentException(this + " slices a single mapped dimension, cannot be applied " +
                                                       " to " + argumentType + ", which have multiple");
                for (TensorType.Dimension dimension : argumentType.dimensions()) {
                    if (dimension.isIndexed())
                        b.dimension(dimension);
                }

            }
        }
        else { // general slicing
            Set<String> slicedDimensions = subspaceAddress.stream().map(d -> d.dimension().get()).collect(Collectors.toSet());
            for (TensorType.Dimension dimension : argumentType.dimensions()) {
                if (slicedDimensions.contains(dimension.name()))
                    slicedDimensions.remove(dimension.name());
                else
                    b.dimension(dimension);
            }
            if ( ! slicedDimensions.isEmpty())
                throw new IllegalArgumentException(this + " slices " + slicedDimensions + " which are not present in " +
                                                   argumentType);
        }
        return b.build();
    }

    @Override
    public String toString(ToStringContext context) {
        StringBuilder b = new StringBuilder(argument.toString(context));
        if (subspaceAddress.size() == 1 && subspaceAddress.get(0).dimension().isEmpty()) {
            if (subspaceAddress.get(0).index().isPresent())
                b.append("[").append(subspaceAddress.get(0).index().get().toString(context)).append("]");
            else
                b.append("{").append(subspaceAddress.get(0).label().get()).append("}");
        }
        else {
            b.append("{").append(subspaceAddress.stream().map(i -> i.toString(context)).collect(Collectors.joining(", "))).append("}");
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
            return toString(ToStringContext.empty());
        }

        public String toString(ToStringContext context) {
            StringBuilder b = new StringBuilder();
            dimension.ifPresent(d -> b.append(d).append(":"));
            if (label != null)
                b.append(label);
            else
                b.append(index.toString(context));
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
