// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.api.annotations.Beta;
import com.yahoo.tensor.PartialAddress;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.TypeResolver;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
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

    public List<TensorFunction<NAMETYPE>> selectorFunctions() {
        var result = new ArrayList<TensorFunction<NAMETYPE>>();
        for (var dimVal : subspaceAddress) {
            dimVal.index().ifPresent(fun -> fun.asTensorFunction().ifPresent(tf -> result.add(tf)));
        }
        return result;
    }

    public TensorFunction<NAMETYPE> withTransformedFunctions(
            Function<ScalarFunction<NAMETYPE>, ScalarFunction<NAMETYPE>> transformer)
    {
        List<DimensionValue<NAMETYPE>> transformedAddress = new ArrayList<>();
        for (var orig : subspaceAddress) {
            var idxFun = orig.index();
            if (idxFun.isPresent()) {
                var transformed = transformer.apply(idxFun.get());
                transformedAddress.add(new DimensionValue<NAMETYPE>(orig.dimension(), transformed));
            } else {
                transformedAddress.add(orig);
            }
        }
        return new Slice<>(argument, transformedAddress);
    }

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
        if (resultType.rank() == 0) { // shortcut common case
            return Tensor.from(tensor.get(subspaceAddress.asAddress(tensor.type())));
        }

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

    private List<String> findDimensions(List<TensorType.Dimension> dims, Predicate<TensorType.Dimension> pred) {
        return dims.stream().filter(pred).map(TensorType.Dimension::name).toList();
    }

    private TensorType resultType(TensorType argumentType) {
        List<String> peekDimensions;
        if (subspaceAddress.size() == 1 && subspaceAddress.get(0).dimension().isEmpty()) {
            // Special case where a single indexed or mapped dimension is sliced
            if (subspaceAddress.get(0).index().isPresent()) {
                peekDimensions = findDimensions(argumentType.dimensions(), TensorType.Dimension::isIndexed);
                if (peekDimensions.size() > 1) {
                    throw new IllegalArgumentException(this + " slices a single indexed dimension, cannot be applied " +
                                                       "to " + argumentType + ", which has multiple");
                }
            }
            else {
                peekDimensions = findDimensions(argumentType.dimensions(), TensorType.Dimension::isMapped);
                if (peekDimensions.size() > 1)
                    throw new IllegalArgumentException(this + " slices a single mapped dimension, cannot be applied " +
                                                       "to " + argumentType + ", which has multiple");
            }
        }
        else { // general slicing
            peekDimensions = subspaceAddress.stream().map(d -> d.dimension().get()).toList();
        }
        try {
            return TypeResolver.peek(argumentType, peekDimensions);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(this + " cannot slice type " + argumentType, e);
        }
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        StringBuilder b = new StringBuilder(argument.toString(context));
        if (context.typeContext().isEmpty()
            && subspaceAddress.size() == 1 && subspaceAddress.get(0).dimension().isEmpty()) { // use short forms
            if (subspaceAddress.get(0).index().isPresent())
                b.append("[").append(subspaceAddress.get(0).index().get().toString(context)).append("]");
            else
                b.append("{").append(subspaceAddress.get(0).label().get()).append("}");
        }
        else { // general form
            b.append("{").append(subspaceAddress.stream()
                                                .map(i -> i.toString(context, this))
                                                .collect(Collectors.joining(", "))).append("}");
        }
        return b.toString();
    }

    @Override
    public int hashCode() { return Objects.hash("slice", argument, subspaceAddress); }

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
            return toString(null, null);
        }

        String toString(ToStringContext<NAMETYPE> context, Slice<NAMETYPE> owner) {
            StringBuilder b = new StringBuilder();
            Optional<String> dimensionName = dimension;
            if (context != null && dimensionName.isEmpty()) { // This isn't just toString(): Output canonical form or fail
                TensorType type = context.typeContext().isPresent() ? owner.argument.type(context.typeContext().get()) : null;
                if (type == null || type.dimensions().size() != 1)
                    throw new IllegalArgumentException("The tensor dimension name being sliced by " + owner +
                                                       " cannot be uniquely resolved. Use the full form: " +
                                                       "'slice{myDimensionName:" + valueToString(context) + "}'");
                else
                    dimensionName = Optional.of(type.dimensions().get(0).name());
            }
            dimensionName.ifPresent(d -> b.append(d).append(":"));
            b.append(valueToString(context));
            return b.toString();
        }

        private String valueToString(ToStringContext<NAMETYPE> context) {
            if (label != null)
                return label;
            else
                return index.toString(context);
        }

        @Override
        public int hashCode() { return Objects.hash(dimension, label, index); }


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

        @Override
        public int hashCode() { return Objects.hash("constantIntegerFunction", value); }

    }

}
