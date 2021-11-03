// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.searchlib.rankingexpression.rule.Function;

/**
 * A representation for integer numbers
 *
 * @author balder
 */
public class LongValue extends DoubleCompatibleValue {
    private final long value;

    public LongValue(long value) {
        this.value = value;
    }
    @Override
    public double asDouble() {
        return value;
    }
    @Override
    public boolean asBoolean() {
        return value != 0;
    }

    @Override
    public Value asMutable() {
        return new LongValue(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public boolean equals(Object other) {
        if (this==other) return true;
        if ( ! (other instanceof Value)) return false;
        if ( ! ((Value) other).hasDouble()) return false;
        return new DoubleValue(value).equals(other);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public Value negate() {
        return new LongValue(-value);
    }

    private UnsupportedOperationException unsupported(String operation, Value value) {
        return new UnsupportedOperationException("Cannot perform " + operation + " on " + value + " and " + this);
    }

    /** Returns this or a mutable copy assigned the given value */
    private DoubleValue mutable(double value) {
        return new DoubleValue(value);
    }

    @Override
    public Value add(Value value) {
        if (value instanceof TensorValue)
            return value.add(this);

        try {
            return mutable(this.value + value.asDouble());
        }
        catch (UnsupportedOperationException e) {
            throw unsupported("add",value);
        }
    }

    @Override
    public Value subtract(Value value) {
        if (value instanceof TensorValue)
            return value.negate().add(this);

        try {
            return mutable(this.value - value.asDouble());
        }
        catch (UnsupportedOperationException e) {
            throw unsupported("subtract",value);
        }
    }

    @Override
    public Value multiply(Value value) {
        if (value instanceof TensorValue)
            return value.multiply(this);

        try {
            return mutable(this.value * value.asDouble());
        }
        catch (UnsupportedOperationException e) {
            throw unsupported("multiply", value);
        }
    }

    @Override
    public Value divide(Value value) {
        try {
            return mutable(this.value / value.asDouble());
        }
        catch (UnsupportedOperationException e) {
            throw unsupported("divide",value);
        }
    }

    @Override
    public Value modulo(Value value) {
        try {
            return mutable(this.value % value.asDouble());
        }
        catch (UnsupportedOperationException e) {
            throw unsupported("modulo",value);
        }
    }

    @Override
    public Value function(Function function, Value value) {
        // use the tensor implementation of max and min if the argument is a tensor
        if ( (function.equals(Function.min) || function.equals(Function.max)) && value instanceof TensorValue)
            return value.function(function, this);

        try {
            return mutable(function.evaluate(this.value, value.asDouble()));
        }
        catch (UnsupportedOperationException e) {
            throw unsupported("function " + function, value);
        }
    }

}
