// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.javacc.UnicodeUtilities;
import com.yahoo.searchlib.rankingexpression.rule.Function;
import com.yahoo.searchlib.rankingexpression.rule.TruthOperator;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

/**
 * The result of a ranking expression evaluation.
 * Concrete subclasses of this provides implementations of these methods or throws
 * UnsupportedOperationException if the operation is not supported.
 *
 * @author bratseth
 */
public abstract class Value {

    private boolean frozen=false;

    /** Returns the type of this value */
    public abstract TensorType type();

    /** Returns this value as a double, or throws UnsupportedOperationException if it cannot be represented as a double */
    public abstract double asDouble();

    /** Returns this value as a double value, or throws UnsupportedOperationException if it cannot be represented as a double */
    public DoubleValue asDoubleValue() {
        return new DoubleValue(asDouble());
    }

    /** Returns this as a tensor value */
    public abstract Tensor asTensor();

    /** A utility method for wrapping a sdouble in a rank 0 tensor */
    protected Tensor doubleAsTensor(double value) {
        return Tensor.Builder.of(TensorType.empty).cell(TensorAddress.of(), value).build();
    }

    /** Returns true if this value can return itself as a double, i.e asDoubleValue will return a value and not throw */
    public abstract boolean hasDouble();

    /** Returns this value as a boolean. */
    public abstract boolean asBoolean();

    public abstract Value negate();

    public abstract Value add(Value value);

    public abstract Value subtract(Value value);

    public abstract Value multiply(Value value);

    public abstract Value divide(Value value);

    public abstract Value modulo(Value value);

    public abstract Value and(Value value);

    public abstract Value or(Value value);

    public abstract Value not();

    public abstract Value power(Value value);

    /** Perform the comparison specified by the operator between this value and the given value */
    public abstract Value compare(TruthOperator operator, Value value);

    /** Perform the given binary function on this value and the given value */
    public abstract Value function(Function function,Value value);

    /**
     * Irreversibly makes this immutable. Overriders must always call super.freeze() and return this
     *
     * @return this for convenience
     */
    public Value freeze() {
        frozen=true;
        return this;
    }

    /** Returns true if this is immutable, false otherwise */
    public final boolean isFrozen() { return frozen; }

    /** Returns this is mutable, or a mutable copy otherwise */
    public abstract Value asMutable();

    @Override
    public abstract String toString();

    @Override
    public abstract boolean equals(Object other);

    @Override
    public abstract int hashCode();

    /**
     * Parses the given string to a value and returns it.
     * Different subtypes of Value will be returned depending on the string.
     *
     * @return a mutable Value
     * @throws IllegalArgumentException if the given string is not parseable as a value
     */
    public static Value parse(String value) {
        if (value.equals("true"))
            return new BooleanValue(true);
        else if (value.equals("false"))
            return new BooleanValue(false);
        else if (value.startsWith("\"") || value.startsWith("'"))
            return new StringValue(UnicodeUtilities.unquote(value));
        else if (value.startsWith("{"))
            return new TensorValue(Tensor.from(value));
        else
            return new DoubleValue(Double.parseDouble(value));
    }

}
