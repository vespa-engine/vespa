// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.text.Utf8;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This result holds a string.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class StringResultNode extends SingleResultNode {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 53, StringResultNode.class);
    private static StringResultNode negativeInfinity = new StringResultNode("");
    private static PositiveInfinityResultNode positiveInfinity = new PositiveInfinityResultNode();

    // The string value of this node.
    private String value;

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public StringResultNode() {
        super();
        value = "";
    }

    /**
     * Constructs an instance of this class with given value.
     *
     * @param value The value to assign to this.
     */
    public StringResultNode(String value) {
        super();
        setValue(value);
    }

    /**
     * Sets the value of this result.
     *
     * @param value The value to set.
     * @return This, to allow chaining.
     */
    public StringResultNode setValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Value can not be null.");
        }
        this.value = value;
        return this;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        byte[] raw = getRaw();
        buf.putInt(null, raw.length);
        buf.put(null, raw);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        value = getUtf8(buf);
    }

    @Override
    public long getInteger() {
        try {
            return Integer.valueOf(value);
        } catch (java.lang.NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public double getFloat() {
        try {
            return Double.valueOf(value);
        } catch (java.lang.NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public String getString() {
        return value;
    }

    @Override
    public byte[] getRaw() {
        return Utf8.toBytes(value);
    }

    @Override
    protected int onCmp(ResultNode rhs) {
        return (rhs instanceof PositiveInfinityResultNode)
               ? -1
               : value.compareTo(rhs.getString());
    }

    @Override
    public int hashCode() {
        return super.hashCode() + value.hashCode();
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("value", value);
    }

    public void add(ResultNode rhs) {
        value += rhs.getString();
    }

    public void min(ResultNode rhs) {
        if (value.compareTo(rhs.getString()) > 0) {
            value = rhs.getString();
        }
    }

    public void max(ResultNode rhs) {
        if (value.compareTo(rhs.getString()) < 0) {
            value = rhs.getString();
        }
    }

    public void append(ResultNode rhs) {
        value += rhs.getString();
    }

    @Override
    public Object getValue() {
        return getString();
    }

    @Override
    public void set(ResultNode rhs) {
        value = rhs.getString();
    }

    @Override
    public void negate() {
        char a[] = value.toCharArray();
        for (int i = 0; i < a.length; i++) {
            a[i] = (char)-a[i];
        }
        value = new String(a);
    }

    /**
     * Will provide the smallest possible value
     *
     * @return the smallest possible IntegerResultNode
     */
    public static StringResultNode getNegativeInfinity() {
        return negativeInfinity;
    }

    /**
     * Will provide the largest possible value
     *
     * @return the smallest largest IntegerResultNode
     */
    public static PositiveInfinityResultNode getPositiveInfinity() {
        return positiveInfinity;
    }
}

