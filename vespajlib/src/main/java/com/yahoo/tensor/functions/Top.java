// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.tensor.evaluation.VariableTensor;

import java.util.List;
import java.util.Objects;

/**
 * Pick the top N cells in a mapped tensor, using cell_order and
 * filter_subspaces as primitives.
 *
 * @author arnej
 */
public class Top<NAMETYPE extends Name> extends CompositeTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> n;
    private final TensorFunction<NAMETYPE> input;

    public Top(TensorFunction<NAMETYPE> n, TensorFunction<NAMETYPE> input) {
        this.n = n;
        this.input = input;
    }

    @Override
    public TensorType type(TypeContext<NAMETYPE> context) {
        var nType = n.type(context);
        var inputType = input.type(context);
        if (nType.rank() > 0) {
            throw new IllegalArgumentException("the N argument to top(N,input) should be a number, but had type: " + nType);
        }
        if (inputType.hasIndexedDimensions() || ! inputType.hasMappedDimensions()) {
            throw new IllegalArgumentException("the input argument to top(N,input) should be a sparse tensor, but had type: " + inputType);
        }
        return super.type(context);
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() {
        return List.of(n, input);
    }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if (arguments.size() != 2)
            throw new IllegalArgumentException("Top must have 2 arguments, got " + arguments.size());
        return new Top<>(arguments.get(0), arguments.get(1));
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        TensorFunction<NAMETYPE> primitiveI = input.toPrimitive();
        TensorFunction<NAMETYPE> primitiveN = n.toPrimitive();
        var ranks = new CellOrder<>(primitiveI, CellOrder.Order.MAX);
        var masks = new Join<>(ranks, primitiveN, ScalarFunctions.less());
        var filter = new FilterSubspaces<>(masks, "s", new VariableTensor<>("s"));
        var result = new Join<>(primitiveI, filter, ScalarFunctions.multiply());
        return result;
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "top(" + n.toString(context) + ", " + input.toString(context) + ")";
    }

    @Override
    public int hashCode() {
        return Objects.hash("top_n", n, input);
    }

}
