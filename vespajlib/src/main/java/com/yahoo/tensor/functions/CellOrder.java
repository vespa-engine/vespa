// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.TypeResolver;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * The <i>cell_order</i> tensor function produces a tensor with the rank of each
 * value in the original tensor according to the given ordering
 *
 * @author arnej
 */
public class CellOrder<NAMETYPE extends Name> extends PrimitiveTensorFunction<NAMETYPE> {

    public enum Order {
        MAX,
        MIN;

        @Override
        public String toString() { return name().toLowerCase(); }
    }

    private final TensorFunction<NAMETYPE> argument;
    private final Order order;

    public CellOrder(TensorFunction<NAMETYPE> argument, Order order) {
        Objects.requireNonNull(argument, "The argument tensor cannot be null");
        Objects.requireNonNull(order, "The order cannot be null");
        this.argument = argument;
        this.order = order;
    }

    public static TensorType outputType(TensorType inputType) {
        return TypeResolver.map(inputType);
    }

    public TensorFunction<NAMETYPE> argument() { return argument; }
    public Order order() { return order; }

    static int nanAwareCompare(double a, double b) {
        if (Double.isNaN(a)) return Double.isNaN(b) ? 0 : 1;
        if (Double.isNaN(b)) return -1;
        return Double.compare(a, b);
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return List.of(argument); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("CellOrder must have 1 argument, got " + arguments.size());
        return new CellOrder<>(arguments.get(0), order);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        return new CellOrder<>(argument.toPrimitive(), order);
    }

    @Override
    public TensorType type(TypeContext<NAMETYPE> context) {
        return outputType(argument.type(context));
    }

    @Override
    public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor input = argument().evaluate(context);
        List<Double> values = new ArrayList<>();
        for (Iterator<Tensor.Cell> i = input.cellIterator(); i.hasNext(); ) {
            values.add(i.next().getValue());
        }
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            indexes.add(i);
        }
        if (order == Order.MAX) {
            indexes.sort((a, b) -> nanAwareCompare(values.get(b), values.get(a)));
        } else {
            indexes.sort((a, b) -> nanAwareCompare(values.get(a), values.get(b)));
        }
        List<Integer> ranks = new ArrayList<>(indexes.size());
        for (int i = 0; i < indexes.size(); i++) {
            ranks.add(0);
        }
        for (int i = 0; i < indexes.size(); i++) {
            ranks.set(indexes.get(i), i);
        }
        Tensor.Builder builder = Tensor.Builder.of(outputType(input.type()));
        Iterator<Tensor.Cell> cells = input.cellIterator();
        for (int i = 0; cells.hasNext(); i++) {
            Tensor.Cell cell = cells.next();
            int rank = ranks.get(i);
            builder.cell(cell.getKey(), (double) rank);
        }
        return builder.build();
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "cell_order(" + argument.toString(context) + ", " + order + ")";
    }

    @Override
    public int hashCode() { return Objects.hash("cell_order", argument, order); }
}
