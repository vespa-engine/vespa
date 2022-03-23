// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.searchlib.rankingexpression.rule.Function;

/**
 * A double value result of a ranking expression evaluation.
 * In a boolean context doubles are true if they are different from 0.0
 *
 * @author bratseth
 */
public final class DoubleValue extends DoubleCompatibleValue {

    // A note on performance: Reusing double values like below is actually slightly slower per evaluation,
    // but the reduced garbage cost seems to regain this plus some additional percentages

    private double value;

    /** The double value instance for 0 */
    public final static DoubleValue zero = DoubleValue.frozen(0);

    /** The double value instance for NaN */
    public final static DoubleValue NaN = DoubleValue.frozen(Double.NaN);

    public DoubleValue(double value) {
        this.value = value;
    }

    /**
     * Create a double which is frozen at the outset.
     */
    public static DoubleValue frozen(double value) {
        DoubleValue doubleValue = new DoubleValue(value);
        doubleValue.freeze();
        return doubleValue;
    }

    @Override
    public double asDouble() { return value; }

    @Override
    public DoubleValue asDoubleValue() { return this; }

    @Override
    public boolean asBoolean() { return value != 0.0; }

    @Override
    public DoubleValue negate() {
        return mutable(-value);
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

    private UnsupportedOperationException unsupported(String operation, Value value) {
        return new UnsupportedOperationException("Cannot perform " + operation + " on " + value + " and " + this);
    }

    /** Returns this or a mutable copy assigned the given value */
    private DoubleValue mutable(double value) {
        DoubleValue mutable = this.asMutable();
        mutable.value = value;
        return mutable;
    }

    @Override
    public DoubleValue asMutable() {
        if ( ! isFrozen()) return this;
        return new DoubleValue(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if ( ! (other instanceof Value)) return false;
        if ( ! ((Value) other).hasDouble()) return false;
        return this.asDouble() == ((Value) other).asDouble();
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

}
