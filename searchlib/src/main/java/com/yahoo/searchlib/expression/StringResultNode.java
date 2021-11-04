// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.text.Utf8;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.util.Arrays;

/**
 * This result holds a string.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class StringResultNode extends SingleResultNode {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 53, StringResultNode.class);
    private static final StringResultNode negativeInfinity = new StringResultNode("");
    private static final PositiveInfinityResultNode positiveInfinity = new PositiveInfinityResultNode();

    private static final byte[] EMPTY_UTF8_ARRAY = new byte[0];

    // The string value of this node, in raw UTF-8 octets.
    private byte[] utf8Value;

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public StringResultNode() {
        super();
        utf8Value = EMPTY_UTF8_ARRAY;
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

    private StringResultNode(byte[] rawUtf8Value) {
        super();
        utf8Value = rawUtf8Value;
    }

    /**
     * Creates a new StringResultNode backed by an underlying byte array. The input is
     * presumed to be in valid UTF-8 format, but is _not_ checked for validity.
     */
    protected static StringResultNode ofUncheckedUtf8Array(byte[] rawUtf8Value) {
        return new StringResultNode(rawUtf8Value);
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
        this.utf8Value = Utf8.toBytes(value);
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
        // We expect the UTF-8 we get from the backend to be pre-checked and valid.
        utf8Value = getRawUtf8Bytes(buf);
    }

    @Override
    public long getInteger() {
        try {
            return Integer.valueOf(getString());
        } catch (java.lang.NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public double getFloat() {
        try {
            return Double.valueOf(getString());
        } catch (java.lang.NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public String getString() {
        return Utf8.toString(utf8Value);
    }

    @Override
    public byte[] getRaw() {
        return utf8Value;
    }

    @Override
    protected int onCmp(ResultNode rhs) {
        return (rhs instanceof PositiveInfinityResultNode)
               ? -1
               : internalNonPositiveInfinityCompareTo(rhs);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + Arrays.hashCode(utf8Value);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("value", getString());
    }

    @Override
    public void add(ResultNode rhs) {
        setValue(getString() + rhs.getString());
    }

    @Override
    public void min(ResultNode rhs) {
        if (internalNonPositiveInfinityCompareTo(rhs) > 0) {
            set(rhs);
        }
    }

    @Override
    public void max(ResultNode rhs) {
        if (internalNonPositiveInfinityCompareTo(rhs) < 0) {
            set(rhs);
        }
    }

    public void append(ResultNode rhs) {
        setValue(getString() + rhs.getString());
    }

    @Override
    public Object getValue() {
        return getString();
    }

    @Override
    public void set(ResultNode rhs) {
        if (rhs instanceof StringResultNode) {
            utf8Value = ((StringResultNode) rhs).utf8Value;
        } else {
            setValue(rhs.getString());
        }
    }

    @Override
    public void negate() {
        char[] a = getString().toCharArray();
        for (int i = 0; i < a.length; i++) {
            a[i] = (char)-a[i];
        }
        setValue(new String(a));
    }

    private int internalNonPositiveInfinityCompareTo(ResultNode rhs) {
        // Note: this may not necessarily be well-defined _semantically_ unless rhs is
        // also a StringResultNode. The C++ implementation explicitly expects rhs to be
        // such an instance, but this depends on a classId check that is _not_ done in
        // the Java implementation...
        // We use getString() instead of getRaw() to support implicit stringification
        // (legacy Java implementation behavior), but it's not given that this is always
        // the desired outcome.
        var rhsAsUtf8 = (rhs instanceof StringResultNode)
                        ? ((StringResultNode)rhs).utf8Value
                        : Utf8.toBytes(rhs.getString());
        return Arrays.compareUnsigned(utf8Value, rhsAsUtf8);
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

