// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.nio.ByteBuffer;

/**
 * This result holds a float value.
 *
 * @author baldersheim
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class FloatResultNode extends NumericResultNode {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 52, FloatResultNode.class);
    private static FloatResultNode negativeInfinity = new FloatResultNode(Double.NEGATIVE_INFINITY);
    private static FloatResultNode positiveInfinity = new FloatResultNode(Double.POSITIVE_INFINITY);
    // The numeric value of this node.
    private double value;

    /**
     * Constructs an empty result node.
     */
    public FloatResultNode() {
        super();
    }

    /**
     * Constructs an instance of this class with given value.
     *
     * @param value The value to assign to this.
     */
    public FloatResultNode(double value) {
        super();
        setValue(value);
    }

    /**
     * Sets the value of this result.
     *
     * @param value The value to set.
     * @return This, to allow chaining.
     */
    public FloatResultNode setValue(double value) {
        this.value = value;
        return this;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        buf.putDouble(null, value);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        value = buf.getDouble(null);
    }

    @Override
    public long getInteger() {
        return Math.round(value);
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
        return ByteBuffer.allocate(8).putDouble(value).array();
    }

    @Override
    public void add(ResultNode rhs) {
        value += rhs.getFloat();
    }

    @Override
    public void negate() {
        value = -value;
    }

    @Override
    public void multiply(ResultNode rhs) {
        value *= rhs.getFloat();
    }

    @Override
    public void divide(ResultNode rhs) {
        double val = rhs.getFloat();
        value = (val == 0.0) ? 0.0 : (value / val);
    }

    @Override
    public void modulo(ResultNode rhs) {
        value %= rhs.getInteger();
    }

    @Override
    public void min(ResultNode rhs) {
        double value = rhs.getFloat();
        if (value < this.value) {
            this.value = value;
        }
    }

    @Override
    public void max(ResultNode rhs) {
        double value = rhs.getFloat();
        if (value > this.value) {
            this.value = value;
        }
    }

    @Override
    public Object getNumber() {
        return new Double(value);
    }

    @Override
    protected int onCmp(ResultNode rhs) {
        double b = rhs.getFloat();
        if (Double.isNaN(value)) {
            return Double.isNaN(b) ? 0 : -1;
        } else {
            if (Double.isNaN(b)) {
                return 1;
            } else {
                return (value < b) ? -1 : (value > b) ? 1 : 0;
            }
        }
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
        value = rhs.getFloat();
    }

    /**
     * Will provide the smallest possible value
     *
     * @return the smallest possible FloatResultNode
     */
    public static FloatResultNode getNegativeInfinity() {
        return negativeInfinity;
    }

    /**
     * Will provide the largest possible value
     *
     * @return the smallest largest FloatResultNode
     */
    public static FloatResultNode getPositiveInfinity() {
        return positiveInfinity;
    }
}
