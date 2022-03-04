// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

/**
 * A value which is either true or false.
 * In numerical context true is interpreted as 1 and false as 0.
 *
 * @author bratseth
 */
public class BooleanValue extends DoubleCompatibleValue {

    private final boolean value;

    /**
     * Create a boolean value which is frozen at the outset.
     */
    public static BooleanValue frozen(boolean value) {
        BooleanValue booleanValue = new BooleanValue(value);
        booleanValue.freeze();
        return booleanValue;
    }

    public BooleanValue(boolean value) {
        this.value = value;
    }

    public boolean asBoolean() { return value; };

    @Override
    public double asDouble() {
        return value ? 1 : 0;
    }

    @Override
    public Value asMutable() {
        if ( ! isFrozen()) return this;
        return new BooleanValue(value);
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
        return this.value == ((Value) other).asBoolean();
    }

    @Override
    public int hashCode() {
        return value ? 1 : 3;
    }

}
