// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.searchlib.aggregation.RawData;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.util.Arrays;

/**
 * This result holds a byte array value.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class RawResultNode extends SingleResultNode {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 54, RawResultNode.class);
    private static final RawResultNode negativeInfinity = new RawResultNode();
    private static final PositiveInfinityResultNode positiveInfinity = new PositiveInfinityResultNode();

    // The raw value of this node.
    private RawData value = null;

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public RawResultNode() {
        super();
        value = new RawData();
    }

    /**
     * Constructs an instance of this class with given byte buffer.
     *
     * @param value The value to assign to this.
     */
    public RawResultNode(byte[] value) {
        super();
        setValue(value);
    }

    /**
     * Sets the value of this result.
     *
     * @param value The value to set.
     * @return This, to allow chaining.
     */
    public RawResultNode setValue(byte[] value) {
        this.value = new RawData(value);
        return this;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        value.serialize(buf);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        value = new RawData();
        value.deserialize(buf);
    }

    @Override
    public long getInteger() {
        return 0;
    }

    @Override
    public double getFloat() {
        return 0;
    }

    @Override
    public String getString() {
        return new String(value.getData());
    }

    @Override
    public byte[] getRaw() {
        return value.getData();
    }

    @Override
    public String toString() {
        if (value != null) {
            return Arrays.toString(value.getData());
        }
        return "[]";
    }

    @Override
    protected int onCmp(ResultNode rhs) {
        return (rhs instanceof PositiveInfinityResultNode)
               ? -1
               : RawData.compare(value.getData(), rhs.getRaw());
    }

    @Override
    public int hashCode() {
        return super.hashCode() + value.hashCode();
    }

    @Override
    public RawResultNode clone() {
        RawResultNode obj = (RawResultNode)super.clone();
        if (value != null) {
            obj.value = (RawData)value.clone();
        }
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("value", value);
    }

    public void add(ResultNode rhs) {
        byte[] nb = new byte[value.getData().length + rhs.getRaw().length];
        System.arraycopy(value.getData(), 0, nb, 0, value.getData().length);
        System.arraycopy(rhs.getRaw(), 0, nb, value.getData().length, rhs.getRaw().length);
        value = new RawData(nb);
    }

    public void min(ResultNode rhs) {
        RawData b = new RawData(rhs.getRaw());
        if (value.compareTo(b) > 0) {
            value = b;
        }
    }

    public void max(ResultNode rhs) {
        RawData b = new RawData(rhs.getRaw());
        if (value.compareTo(b) < 0) {
            value = b;
        }
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public void set(ResultNode rhs) {
        value = new RawData(rhs.getRaw());
    }

    @Override
    public void negate() {
        byte[] data = value.getData();
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte)-data[i];
        }
    }

    /**
     * Will provide the smallest possible value
     *
     * @return the smallest possible IntegerResultNode
     */
    public static RawResultNode getNegativeInfinity() {
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
