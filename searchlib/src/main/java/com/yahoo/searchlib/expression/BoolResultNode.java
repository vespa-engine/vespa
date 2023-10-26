// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.nio.ByteBuffer;

public class BoolResultNode extends ResultNode {
    public static final int classId = registerClass(0x4000 + 146, BoolResultNode.class, BoolResultNode::new);
    private boolean value = false;

    public BoolResultNode() {
    }

    public BoolResultNode(boolean value) {
        this.value = value;
    }
    /**
     * Sets the value of this result.
     *
     * @param value The value to set.
     * @return This, to allow chaining.
     */
    public BoolResultNode setValue(boolean value) {
        this.value = value;
        return this;
    }

    public boolean getValue() { return value; }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        byte v = (byte)(value ? 1 : 0);
        buf.putByte(null, v );
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        value = buf.getByte(null) != 0;
    }

    @Override
    public long getInteger() {
        return value ? 1 : 0;
    }

    @Override
    public double getFloat() {
        return value ? 1.0 : 0.0;
    }

    @Override
    public String getString() {
        return String.valueOf(value);
    }

    @Override
    public byte[] getRaw() {
        return ByteBuffer.allocate(8).putLong(getInteger()).array();
    }

    @Override
    public void negate() {
        value = ! value;
    }


    @Override
    protected int onCmp(ResultNode rhs) {
        return Long.compare(getInteger(), rhs.getInteger());
    }

    @Override
    public int hashCode() {
        return super.hashCode() + (int)getInteger();
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("value", value);
    }

    @Override
    public void set(ResultNode rhs) {
        value = rhs.getInteger() > 0;
    }
}
