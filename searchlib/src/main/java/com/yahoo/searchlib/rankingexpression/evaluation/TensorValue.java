// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.google.common.annotations.Beta;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.searchlib.rankingexpression.rule.Function;
import com.yahoo.searchlib.rankingexpression.rule.TruthOperator;
import com.yahoo.tensor.TensorType;

import java.util.Collections;
import java.util.Optional;

/**
 * A Value containing a tensor.
 * See {@link com.yahoo.tensor.Tensor} for definition of a tensor
 * and the operations supported.
 *
 * @author bratseth
 */
@Beta
public class TensorValue extends Value {

    /** The tensor value of this */
    private final Tensor value;
    private final Optional<TensorType> type;

    public TensorValue(Tensor value) {
        this.value = value;
        this.type = Optional.empty();
    }

    public TensorValue(Tensor value, TensorType type) {
        this.value = value;
        this.type = Optional.of(type);
    }

    @Override
    public double asDouble() {
        if (value.dimensions().size() == 0)
            return value.get(TensorAddress.empty);
        throw new UnsupportedOperationException("Requires a double value from a tensor with dimensions " +
                                                value.dimensions() + ", but a tensor of order > 0 does " +
                                                "not have a double value. Input tensor: " + this);
    }

    @Override
    public boolean hasDouble() { return value.dimensions().size() == 0; }

    @Override
    public boolean asBoolean() {
        throw new UnsupportedOperationException("A tensor does not have a boolean value");
    }

    @Override
    public Value negate() {
        return new TensorValue(value.map((value) -> -value));
    }

    @Override
    public Value add(Value argument) {
        if (argument instanceof TensorValue)
            return new TensorValue(value.add(((TensorValue)argument).value));
        else
            return new TensorValue(value.map((value) -> value + argument.asDouble()));
    }

    @Override
    public Value subtract(Value argument) {
        if (argument instanceof TensorValue)
            return new TensorValue(value.subtract(((TensorValue) argument).value));
        else
            return new TensorValue(value.map((value) -> value - argument.asDouble()));
    }

    @Override
    public Value multiply(Value argument) {
        if (argument instanceof TensorValue)
            return new TensorValue(value.multiply(((TensorValue) argument).value));
        else
            return new TensorValue(value.map((value) -> value * argument.asDouble()));
    }

    @Override
    public Value divide(Value argument) {
        if (argument instanceof TensorValue)
            return new TensorValue(value.divide(((TensorValue) argument).value));
        else
            return new TensorValue(value.map((value) -> value / argument.asDouble()));
    }

    public Value min(Value argument) {
        return new TensorValue(value.min(asTensor(argument, "min")));
    }

    public Value max(Value argument) {
        return new TensorValue(value.max(asTensor(argument, "max")));
    }

    public Value sum(String dimension) {
        return new TensorValue(value.sum(Collections.singletonList(dimension)));
    }

    public Value sum() {
        return new TensorValue(value.sum(Collections.emptyList()));
    }

    private Tensor asTensor(Value value, String operationName) {
        if ( ! (value instanceof TensorValue))
            throw new UnsupportedOperationException("Could not perform " + operationName +
                                                    ": The second argument must be a tensor but was " + value);
        return ((TensorValue)value).value;
    }

    public Tensor asTensor() { return value; }

    public Optional<TensorType> getType() {
        return type;
    }

    @Override
    public boolean compare(TruthOperator operator, Value value) {
        throw new UnsupportedOperationException("A tensor cannot be compared with any value");
    }

    @Override
    public Value function(Function function, Value argument) {
        if (function.equals(Function.min) && argument instanceof TensorValue)
            return min(argument);
        else if (function.equals(Function.max) && argument instanceof TensorValue)
            return max(argument);
        else
            return new TensorValue(value.map((value) -> function.evaluate(value, argument.asDouble())));
    }

    @Override
    public Value asMutable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TensorValue that = (TensorValue) o;

        if (!type.equals(that.type)) return false;
        if (!value.equals(that.value)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = value.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }
}
