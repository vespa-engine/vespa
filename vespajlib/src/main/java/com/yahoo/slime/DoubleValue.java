// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

final class DoubleValue extends Value {
    private final double value;
    public DoubleValue(double value) { this.value = value; }
    public final Type type() { return Type.DOUBLE; }
    public final long asLong() { return (long)this.value; }
    public final double asDouble() { return this.value; }
    public final void accept(Visitor v) { v.visitDouble(value); }
}
