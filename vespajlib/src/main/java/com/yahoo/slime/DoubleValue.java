// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * @author havardpe
 */
final class DoubleValue extends Value {

    private final double value;
    public DoubleValue(double value) { this.value = value; }
    public Type type() { return Type.DOUBLE; }
    public long asLong() { return (long)this.value; }
    public double asDouble() { return this.value; }
    public void accept(Visitor v) { v.visitDouble(value); }

}
