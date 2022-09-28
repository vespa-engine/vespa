// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.javacc.UnicodeUtilities;
import com.yahoo.searchlib.rankingexpression.rule.Function;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

/**
 * A string value.
 *
 * @author bratseth
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
        this.value = UnicodeUtilities.unquote(value);
    }

    @Override
    public TensorType type() { return TensorType.empty; }

    /** Returns the hashcode of this, to enable strings to be encoded (with reasonable safely) as doubles for optimization */
    @Override
    public double asDouble() {
        return value.hashCode();
    }

    @Override
    public Tensor asTensor() {
        return doubleAsTensor(asDouble());
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
    public Value not() {
        throw new UnsupportedOperationException("String values ('" + value + "') do not support not");
    }

    @Override
    public Value or(Value value) {
        throw new UnsupportedOperationException("String values ('" + value + "') do not support or");
    }

    @Override
    public Value and(Value value) {
        throw new UnsupportedOperationException("String values ('" + value + "') do not support and");
    }

    @Override
    public Value largerOrEqual(Value argument) {
        throw new UnsupportedOperationException("String values ('" + value + "') do not support greaterEqual");
    }

    @Override
    public Value larger(Value argument) {
        throw new UnsupportedOperationException("String values ('" + value + "') do not support greater");
    }

    @Override
    public Value smallerOrEqual(Value argument) {
        throw new UnsupportedOperationException("String values ('" + value + "') do not support lessEqual");
    }

    @Override
    public Value smaller(Value argument) {
        throw new UnsupportedOperationException("String values ('" + value + "') do not support less");
    }

    @Override
    public Value approxEqual(Value argument) {
        return new BooleanValue(this.asDouble() == argument.asDouble());
    }

    @Override
    public Value notEqual(Value argument) {
        return new BooleanValue(this.asDouble() != argument.asDouble());
    }

    @Override
    public Value equal(Value argument) {
        return new BooleanValue(this.asDouble() == argument.asDouble());
    }

    @Override
    public Value add(Value value) {
        return new StringValue(value + value.toString());
    }

    @Override
    public Value subtract(Value value) {
        throw new UnsupportedOperationException("String values ('" + value + "') do not support subtraction");
    }

    @Override
    public Value multiply(Value value) {
        throw new UnsupportedOperationException("String values ('" + value + "') do not support multiplication");
    }

    @Override
    public Value divide(Value value) {
        throw new UnsupportedOperationException("String values ('" + value + "') do not support division");
    }

    @Override
    public Value modulo(Value value) {
        throw new UnsupportedOperationException("String values ('" + value + "') do not support modulo");
    }

    @Override
    public Value power(Value value) {
        throw new UnsupportedOperationException("String values ('" + value + "') do not support ^");
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
        return UnicodeUtilities.quote(value, '"');
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
