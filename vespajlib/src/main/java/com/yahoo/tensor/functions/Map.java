// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.TypeResolver;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

/**
 * The <i>map</i> tensor function produces a tensor where the given function is applied on each cell value.
 *
 * @author bratseth
 */
public class Map<NAMETYPE extends Name> extends PrimitiveTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> argument;
    private final DoubleUnaryOperator mapper;

    public Map(TensorFunction<NAMETYPE> argument, DoubleUnaryOperator mapper) {
        Objects.requireNonNull(argument, "The argument tensor cannot be null");
        Objects.requireNonNull(mapper, "The argument function cannot be null");
        this.argument = argument;
        this.mapper = mapper;
    }

    public static TensorType outputType(TensorType inputType) {
        return TypeResolver.map(inputType);
    }

    public TensorFunction<NAMETYPE> argument() { return argument; }
    public DoubleUnaryOperator mapper() { return mapper; }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("Map must have 1 argument, got " + arguments.size());
        return new Map<>(arguments.get(0), mapper);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        return new Map<>(argument.toPrimitive(), mapper);
    }

    @Override
    public TensorType type(TypeContext<NAMETYPE> context) {
        return outputType(argument.type(context));
    }

    @Override
    public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor input = argument().evaluate(context);
        Tensor.Builder builder = Tensor.Builder.of(outputType(input.type()));
        for (Iterator<Tensor.Cell> i = input.cellIterator(); i.hasNext(); ) {
            java.util.Map.Entry<TensorAddress, Double> cell = i.next();
            builder.cell(cell.getKey(), mapper.applyAsDouble(cell.getValue()));
        }
        return builder.build();
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "map(" + argument.toString(context) + ", " + mapper + ")";
    }

    @Override
    public int hashCode() { return Objects.hash("map", argument, mapper); }

}
