// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
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
@Beta
public class Map extends PrimitiveTensorFunction {

    private final TensorFunction argument;
    private final DoubleUnaryOperator mapper;

    public Map(TensorFunction argument, DoubleUnaryOperator mapper) {
        Objects.requireNonNull(argument, "The argument tensor cannot be null");
        Objects.requireNonNull(mapper, "The argument function cannot be null");
        this.argument = argument;
        this.mapper = mapper;
    }

    public static TensorType outputType(TensorType inputType) { return inputType; }

    public TensorFunction argument() { return argument; }
    public DoubleUnaryOperator mapper() { return mapper; }

    @Override
    public List<TensorFunction> arguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction withArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("Map must have 1 argument, got " + arguments.size());
        return new Map(arguments.get(0), mapper);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new Map(argument.toPrimitive(), mapper);
    }

    @Override
    public <NAMETYPE extends TypeContext.Name> TensorType type(TypeContext<NAMETYPE> context) {
        return argument.type(context);
    }

    @Override
    public <NAMETYPE extends TypeContext.Name> Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor argument = argument().evaluate(context);
        Tensor.Builder builder = Tensor.Builder.of(argument.type());
        for (Iterator<Tensor.Cell> i = argument.cellIterator(); i.hasNext(); ) {
            java.util.Map.Entry<TensorAddress, Double> cell = i.next();
            builder.cell(cell.getKey(), mapper.applyAsDouble(cell.getValue()));
        }
        return builder.build();
    }

    @Override
    public String toString(ToStringContext context) {
        return "map(" + argument.toString(context) + ", " + mapper + ")";
    }

}
