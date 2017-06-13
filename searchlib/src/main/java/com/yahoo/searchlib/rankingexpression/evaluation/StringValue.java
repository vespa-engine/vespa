// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.javacc.UnicodeUtilities;
import com.yahoo.searchlib.rankingexpression.rule.Function;
import com.yahoo.searchlib.rankingexpression.rule.TruthOperator;

/**
 * A string value.
 *
 * @author bratseth
 * @since 5.1.21
 */
public class StringValue extends Value {

    private final String value;

    /**
     * Create a string value which is frozen at the outset.
     */
    public static StringValue frozen(String value) {
        StringValue stringValue=new StringValue(value);
        stringValue.freeze();
        return stringValue;
    }

    public StringValue(String value) {
        this.value = value;
    }

    /** Returns the hashcode of this, to enable strings to be encoded (with reasonable safely) as doubles for optimization */
    @Override
    public double asDouble() {
        return UnicodeUtilities.unquote(value).hashCode();
    }

    @Override
    public boolean hasDouble() { return true; }

    @Override
    public boolean asBoolean() {
        throw new UnsupportedOperationException("A string value ('" + value + "') does not have a boolean value");
    }

    @Override
    public Value negate() {
        throw new UnsupportedOperationException("A string value ('" + value + "') cannot be negated");
    }

    @Override
    public Value add(Value value) {
        return new StringValue(value + value.toString());
    }

    @Override
    public Value subtract(Value value) {
        throw new UnsupportedOperationException("String values ('" + value + "') does not support subtraction");
    }

    @Override
    public Value multiply(Value value) {
        throw new UnsupportedOperationException("String values ('" + value + "') does not support multiplication");
    }

    @Override
    public Value divide(Value value) {
        throw new UnsupportedOperationException("String values ('" + value + "') does not support division");
    }

    @Override
    public Value compare(TruthOperator operator, Value value) {
        if (operator.equals(TruthOperator.EQUAL))
            return new BooleanValue(this.equals(value));
        throw new UnsupportedOperationException("String values ('" + value + "') cannot be compared except with '=='");
    }

    @Override
    public Value function(Function function, Value value) {
        throw new UnsupportedOperationException("Mathematical functions cannot be applied on strings ('" + value + "')");
    }

    @Override
    public Value asMutable() {
        if ( ! isFrozen()) return this;
        return new StringValue(value);
    }

    @Override
    public String toString() {
        return "\"" + value + "\"";
    }

    @Override
    public boolean equals(Object other) {
        if (this==other) return true;
        if ( ! (other instanceof StringValue)) return false;
        return ((StringValue)other).value.equals(this.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /** Returns the value of this as a string */
    public String asString() { return value; }

}
