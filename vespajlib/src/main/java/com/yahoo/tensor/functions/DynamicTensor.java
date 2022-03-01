// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableMap;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A function which is a tensor whose values are computed by individual lambda functions on evaluation.
 *
 * @author bratseth
 */
public abstract class DynamicTensor<NAMETYPE extends Name> extends PrimitiveTensorFunction<NAMETYPE> {

    private final TensorType type;

    DynamicTensor(TensorType type) {
        this.type = type;
    }

    @Override
    public TensorType type(TypeContext<NAMETYPE> context) { return type; }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return Collections.emptyList(); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if (arguments.size() != 0)
            throw new IllegalArgumentException("Dynamic tensors must have 0 arguments, got " + arguments.size());
        return this;
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() { return this; }

    TensorType type() { return type; }

    abstract String contentToString(ToStringContext<NAMETYPE> context);

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return type().toString() + ":" + contentToString(context);
    }

    /** Creates a dynamic tensor function. The cell addresses must match the type. */
    public static <NAMETYPE extends Name> DynamicTensor<NAMETYPE> from(TensorType type, Map<TensorAddress, ScalarFunction<NAMETYPE>> cells) {
        return new MappedDynamicTensor<>(type, cells);
    }

    /** Creates a dynamic tensor function for a bound, indexed tensor */
    public static <NAMETYPE extends Name> DynamicTensor<NAMETYPE> from(TensorType type, List<ScalarFunction<NAMETYPE>> cells) {
        return new IndexedDynamicTensor<>(type, cells);
    }

    private static class MappedDynamicTensor<NAMETYPE extends Name> extends DynamicTensor<NAMETYPE> {

        private final ImmutableMap<TensorAddress, ScalarFunction<NAMETYPE>> cells;

        MappedDynamicTensor(TensorType type, Map<TensorAddress, ScalarFunction<NAMETYPE>> cells) {
            super(type);
            this.cells = ImmutableMap.copyOf(cells);
        }

        @Override
        public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
            Tensor.Builder builder = Tensor.Builder.of(type());
            for (var cell : cells.entrySet())
                builder.cell(cell.getKey(), cell.getValue().apply(context));
            return builder.build();
        }

        @Override
        String contentToString(ToStringContext<NAMETYPE> context) {
            if (type().dimensions().isEmpty()) {
                if (cells.isEmpty()) return "{}";
                return "{" + cells.values().iterator().next().toString(context) + "}";
            }

            StringBuilder b = new StringBuilder("{");
            for (var cell : cells.entrySet()) {
                b.append(cell.getKey().toString(type())).append(":").append(cell.getValue().toString(context));
                b.append(",");
            }
            if (b.length() > 1)
                b.setLength(b.length() - 1);
            b.append("}");

            return b.toString();
        }

        @Override
        public int hashCode() { return Objects.hash("mappedDynamicTensor", type(), cells); }

    }

    private static class IndexedDynamicTensor<NAMETYPE extends Name> extends DynamicTensor<NAMETYPE> {

        private final List<ScalarFunction<NAMETYPE>> cells;

        IndexedDynamicTensor(TensorType type, List<ScalarFunction<NAMETYPE>> cells) {
            super(type);
            if ( ! type.dimensions().stream().allMatch(d -> d.type() == TensorType.Dimension.Type.indexedBound))
                throw new IllegalArgumentException("A dynamic tensor can only be created from a list if the type has " +
                                                   "only indexed, bound dimensions, but this has " + type);
            this.cells = List.copyOf(cells);
        }

        @Override
        public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
            IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder)Tensor.Builder.of(type());
            for (int i = 0; i < cells.size(); i++)
                builder.cellByDirectIndex(i, cells.get(i).apply(context));
            return builder.build();
        }

        @Override
        String contentToString(ToStringContext<NAMETYPE> context) {
            if (type().dimensions().isEmpty()) {
                if (cells.isEmpty()) return "{}";
                return "{" + cells.get(0).toString(context) + "}";
            }

            IndexedTensor.Indexes indexes = IndexedTensor.Indexes.of(type());
            StringBuilder b = new StringBuilder("{");
            for (var cell : cells) {
                indexes.next();
                b.append(indexes.toAddress().toString(type())).append(":").append(cell.toString(context));
                b.append(",");
            }
            if (b.length() > 1)
                b.setLength(b.length() - 1);
            b.append("}");

            return b.toString();
        }

        @Override
        public int hashCode() { return Objects.hash("indexedDynamicTensor", type(), cells); }

    }

}
