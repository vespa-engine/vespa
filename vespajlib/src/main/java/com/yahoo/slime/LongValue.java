// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

final class LongValue extends Value {
    private final long value;
    public LongValue(long value) { this.value = value; }
    public final Type type() { return Type.LONG; }
    public final long asLong() { return this.value; }
    public final double asDouble() { return (double)this.value; }
    public final void accept(Visitor v) { v.visitLong(value); }
}
