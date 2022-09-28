// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.searchlib.rankingexpression.rule.Function;
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
    public Value not() {
        return new BooleanValue(!asBoolean());
    }

    @Override
    public Value or(Value value) {
        return new BooleanValue(asBoolean() || value.asBoolean());
    }

    @Override
    public Value and(Value value) {
        return new BooleanValue(asBoolean() && value.asBoolean());
    }

    @Override
    public Value greaterEqual(Value value) {
        return new BooleanValue(this.asDouble() >= value.asDouble());
    }

    @Override
    public Value greater(Value value) {
        return new BooleanValue(this.asDouble() > value.asDouble());
    }

    @Override
    public Value lessEqual(Value value) {
        return new BooleanValue(this.asDouble() <= value.asDouble());
    }

    @Override
    public Value less(Value value) {
        return new BooleanValue(this.asDouble() < value.asDouble());
    }

    @Override
    public Value approx(Value value) {
        return new BooleanValue(approxEqual(this.asDouble(), value.asDouble()));
    }

    @Override
    public Value notEqual(Value value) {
        return new BooleanValue(this.asDouble() != value.asDouble());
    }

    @Override
    public Value equal(Value value) {
        return new BooleanValue(this.asDouble() == value.asDouble());
    }

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
    public Value power(Value value) {
        return new DoubleValue(Function.pow.evaluate(asDouble(), value.asDouble()));
    }

    @Override
    public Value function(Function function, Value value) {
        return new DoubleValue(function.evaluate(asDouble(),value.asDouble()));
    }

    static boolean approxEqual(double x, double y) {
        if (y < -1.0 || y > 1.0) {
            x = Math.nextAfter(x/y, 1.0);
            y = 1.0;
        } else {
            x = Math.nextAfter(x, y);
        }
        return x == y;
    }

}
