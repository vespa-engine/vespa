// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * @author havardpe
 */
final class LongValue extends Value {

    private final long value;
    public LongValue(long value) { this.value = value; }
    public Type type() { return Type.LONG; }
    public long asLong() { return this.value; }
    public double asDouble() { return this.value; }
    public void accept(Visitor v) { v.visitLong(value); }

}
