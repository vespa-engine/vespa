// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.nio.ByteBuffer;

/**
 * This result holds an integer value.
 *
 * @author baldersheim
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class Int16ResultNode extends NumericResultNode {

    public static final int classId = registerClass(0x4000 + 105, Int16ResultNode.class);
    private short value = 0;

    @SuppressWarnings("UnusedDeclaration")
    public Int16ResultNode() {
        // used by deserializer
    }

    /**
     * Constructs an instance of this class with given value.
     *
     * @param value The value to assign to this.
     */
    public Int16ResultNode(short value) {
        this.value = value;
    }

    /**
     * Sets the value of this result.
     *
     * @param value The value to set.
     * @return This, to allow chaining.
     */
    public Int16ResultNode setValue(short value) {
        this.value = value;
        return this;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        buf.putShort(null, value);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        value = buf.getShort(null);
    }

    @Override
    public long getInteger() {
        return value;
    }

    @Override
    public double getFloat() {
        return value;
    }

    @Override
    public String getString() {
        return String.valueOf(value);
    }

    @Override
    public byte[] getRaw() {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    @Override
    public void add(ResultNode rhs) {
        value += rhs.getInteger();
    }

    @Override
    public void negate() {
        value = (short)-value;
    }

    @Override
    public void multiply(ResultNode rhs) {
        value *= rhs.getInteger();
    }

    @Override
    public void divide(ResultNode rhs) {
        short val = (short)rhs.getInteger();
        value = (short)((val == 0) ? 0 : (value / val));
    }

    @Override
    public void modulo(ResultNode rhs) {
        value %= rhs.getInteger();
    }

    @Override
    public void min(ResultNode rhs) {
        short value = (short)rhs.getInteger();
        if (value < this.value) {
            this.value = value;
        }
    }

    @Override
    public void max(ResultNode rhs) {
        short value = (short)rhs.getInteger();
        if (value > this.value) {
            this.value = value;
        }
    }

    @Override
    public Object getNumber() {
        return Integer.valueOf(value);
    }

    @Override
    protected int onCmp(ResultNode rhs) {
        long value = rhs.getInteger();
        return (this.value < value) ? -1 : (this.value > value) ? 1 : 0;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + (int)value;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("value", value);
    }

    @Override
    public void set(ResultNode rhs) {
        value = (short)rhs.getInteger();
    }
}
