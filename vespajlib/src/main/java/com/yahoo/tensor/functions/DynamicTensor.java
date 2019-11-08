// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableMap;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A function which is a tensor whose values are computed by individual lambda functions on evaluation.
 *
 * @author bratseth
 */
public abstract class DynamicTensor extends PrimitiveTensorFunction {

    private final TensorType type;

    DynamicTensor(TensorType type) {
        this.type = type;
    }

    @Override
    public <NAMETYPE extends TypeContext.Name> TensorType type(TypeContext<NAMETYPE> context) { return type; }

    @Override
    public List<TensorFunction> arguments() { return Collections.emptyList(); }

    @Override
    public TensorFunction withArguments(List<TensorFunction> arguments) {
        if (arguments.size() != 0)
            throw new IllegalArgumentException("Dynamic tensors must have 0 arguments, got " + arguments.size());
        return this;
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() { return this; }

    TensorType type() { return type; }

    @Override
    public String toString(ToStringContext context) {
        return type().toString() + ":" + contentToString(context);
    }

    abstract String contentToString(ToStringContext context);

    /** Creates a dynamic tensor function. The cell addresses must match the type. */
    public static DynamicTensor from(TensorType type, Map<TensorAddress, ScalarFunction> cells) {
        return new MappedDynamicTensor(type, cells);
    }

    /** Creates a dynamic tensor function for a bound, indexed tensor */
    public static DynamicTensor from(TensorType type, List<ScalarFunction> cells) {
        return new IndexedDynamicTensor(type, cells);
    }

    private static class MappedDynamicTensor extends DynamicTensor {

        private final ImmutableMap<TensorAddress, ScalarFunction> cells;

        MappedDynamicTensor(TensorType type, Map<TensorAddress, ScalarFunction> cells) {
            super(type);
            this.cells = ImmutableMap.copyOf(cells);
        }

        @Override
        public <NAMETYPE extends TypeContext.Name> Tensor evaluate(EvaluationContext<NAMETYPE> context) {
            Tensor.Builder builder = Tensor.Builder.of(type());
            for (var cell : cells.entrySet())
                builder.cell(cell.getKey(), cell.getValue().apply(context));
            return builder.build();
        }

        @Override
        String contentToString(ToStringContext context) {
            if (type().dimensions().isEmpty()) {
                if (cells.isEmpty()) return "{}";
                return "{" + cells.values().iterator().next() + "}";
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

    }

    private static class IndexedDynamicTensor extends DynamicTensor {

        private final List<ScalarFunction> cells;

        IndexedDynamicTensor(TensorType type, List<ScalarFunction> cells) {
            super(type);
            if ( ! type.dimensions().stream().allMatch(d -> d.type() == TensorType.Dimension.Type.indexedBound))
                throw new IllegalArgumentException("A dynamic tensor can only be created from a list if the type has " +
                                                   "only indexed, bound dimensions, but this has " + type);
            this.cells = List.copyOf(cells);
        }

        @Override
        public <NAMETYPE extends TypeContext.Name> Tensor evaluate(EvaluationContext<NAMETYPE> context) {
            IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder)Tensor.Builder.of(type());
            for (int i = 0; i < cells.size(); i++)
                builder.cellByDirectIndex(i, cells.get(i).apply(context));
            return builder.build();
        }

        @Override
        String contentToString(ToStringContext context) {
            if (type().dimensions().isEmpty()) {
                if (cells.isEmpty()) return "{}";
                return "{" + cells.get(0) + "}";
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

    }

}
