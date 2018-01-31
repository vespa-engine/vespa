// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.searchlib.rankingexpression.rule.Function;
import com.yahoo.searchlib.rankingexpression.rule.TruthOperator;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

/**
 * A value which acts as a double in numerical context.
 *
 * @author bratseth
 */
public abstract class DoubleCompatibleValue extends Value {

    @Override
    public TensorType type() { return TensorType.empty; }

    @Override
    public boolean hasDouble() { return true; }

    @Override
    public Tensor asTensor() {
        return doubleAsTensor(asDouble());
    }

    @Override
    public Value negate() { return new DoubleValue(-asDouble()); }

    @Override
    public Value add(Value value) {
        return new DoubleValue(asDouble() + value.asDouble());
    }

    @Override
    public Value subtract(Value value) {
        return new DoubleValue(asDouble() - value.asDouble());
    }

    @Override
    public Value multiply(Value value) {
        return new DoubleValue(asDouble() * value.asDouble());
    }

    @Override
    public Value divide(Value value) {
        return new DoubleValue(asDouble() / value.asDouble());
    }

    @Override
    public Value modulo(Value value) {
        return new DoubleValue(asDouble() % value.asDouble());
    }

    @Override
    public Value and(Value value) {
        return new BooleanValue(asBoolean() && value.asBoolean());
    }

    @Override
    public Value or(Value value) {
        return new BooleanValue(asBoolean() || value.asBoolean());
    }

    @Override
    public Value not() {
        return new BooleanValue(!asBoolean());
    }

    @Override
    public Value power(Value value) {
        return new DoubleValue(Function.pow.evaluate(asDouble(), value.asDouble()));
    }

    @Override
    public Value compare(TruthOperator operator, Value value) {
        return new BooleanValue(operator.evaluate(asDouble(), value.asDouble()));
    }

    @Override
    public Value function(Function function, Value value) {
        return new DoubleValue(function.evaluate(asDouble(),value.asDouble()));
    }

}
