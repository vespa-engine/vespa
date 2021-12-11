// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * @author havardpe
 */
final class BoolValue extends Value {

    private static final BoolValue trueValue = new BoolValue(true);
    private static final BoolValue falseValue = new BoolValue(false);
    private final boolean value;
    private BoolValue(boolean value) { this.value = value; }
    public Type type() { return Type.BOOL; }
    public boolean asBool() { return this.value; }
    public void accept(Visitor v) { v.visitBool(value); }
    public static BoolValue instance(boolean bit) { return (bit ? trueValue : falseValue); }

}
