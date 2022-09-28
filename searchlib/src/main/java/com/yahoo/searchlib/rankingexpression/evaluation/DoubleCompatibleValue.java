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
        if (value instanceof TensorValue tensor)
            return tensor.or(this);
        else
            return new BooleanValue(asBoolean() || value.asBoolean());
    }

    @Override
    public Value and(Value value) {
        if (value instanceof TensorValue tensor)
            return tensor.and(this);
        else
            return new BooleanValue(asBoolean() && value.asBoolean());
    }

    @Override
    public Value largerOrEqual(Value value) {
        if (value instanceof TensorValue tensor)
            return tensor.largerOrEqual(this);
        else
            return new BooleanValue(this.asDouble() >= value.asDouble());
    }

    @Override
    public Value larger(Value value) {
        if (value instanceof TensorValue tensor)
            return tensor.larger(this);
        else
            return new BooleanValue(this.asDouble() > value.asDouble());
    }

    @Override
    public Value smallerOrEqual(Value value) {
        if (value instanceof TensorValue tensor)
            return tensor.smallerOrEqual(this);
        else
            return new BooleanValue(this.asDouble() <= value.asDouble());
    }

    @Override
    public Value smaller(Value value) {
        if (value instanceof TensorValue tensor)
            return tensor.smaller(this);
        else
            return new BooleanValue(this.asDouble() < value.asDouble());
    }

    @Override
    public Value approxEqual(Value value) {
        if (value instanceof TensorValue tensor)
            return tensor.approxEqual(this);
        else
            return new BooleanValue(approxEqual(this.asDouble(), value.asDouble()));
    }

    @Override
    public Value notEqual(Value value) {
        if (value instanceof TensorValue tensor)
            return tensor.notEqual(this);
        else
            return new BooleanValue(this.asDouble() != value.asDouble());
    }

    @Override
    public Value equal(Value value) {
        if (value instanceof TensorValue tensor)
            return tensor.equal(this);
        else
            return new BooleanValue(this.asDouble() == value.asDouble());
    }

    @Override
    public Value add(Value value) {
        if (value instanceof TensorValue tensor)
            return tensor.add(this);
        else
            return new DoubleValue(asDouble() + value.asDouble());
    }

    @Override
    public Value subtract(Value value) {
        if (value instanceof TensorValue tensor)
            return tensor.subtract(this);
        else
            return new DoubleValue(asDouble() - value.asDouble());
    }

    @Override
    public Value multiply(Value value) {
        if (value instanceof TensorValue tensor)
            return tensor.multiply(this);
        else
            return new DoubleValue(asDouble() * value.asDouble());
    }

    @Override
    public Value divide(Value value) {
        if (value instanceof TensorValue tensor)
            return tensor.divide(this);
        else
            return new DoubleValue(asDouble() / value.asDouble());
    }

    @Override
    public Value modulo(Value value) {
        if (value instanceof TensorValue tensor)
            return tensor.modulo(this);
        else
            return new DoubleValue(asDouble() % value.asDouble());
    }

    @Override
    public Value power(Value value) {
        if (value instanceof TensorValue tensor)
            return tensor.power(this);
        else
            return new DoubleValue(Function.pow.evaluate(asDouble(), value.asDouble()));
    }

    @Override
    public Value function(Function function, Value value) {
        if (value instanceof TensorValue tensor)
            return tensor.function(function, this);
        else
            return new DoubleValue(function.evaluate(asDouble(), value.asDouble()));
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
