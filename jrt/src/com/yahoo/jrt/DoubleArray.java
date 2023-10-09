// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * 64-bit floating-point array
 **/
public class DoubleArray extends Value
{
    private double[] value;

    /**
     * Create from a Java-type value
     *
     * @param value the value
     **/
    public DoubleArray(double[] value) { this.value = value; }

    /**
     * Create by decoding the value from the given buffer
     *
     * @param src buffer where the value is stored
     **/
    DoubleArray(ByteBuffer src) {
        int size = src.getInt();
        value = new double[size];
        src.asDoubleBuffer().get(value);
        src.position(src.position() + size * 8);
    }

    /**
     * @return DOUBLE_ARRAY
     **/
    public byte type() { return DOUBLE_ARRAY; }
    public int count() { return value.length; }

    int bytes() { return 4 + value.length * 8; }
    void encode(ByteBuffer dst) {
        dst.putInt(value.length);
        dst.asDoubleBuffer().put(value);
        dst.position(dst.position() + value.length * 8);
    }

    public double[] asDoubleArray() { return value; }

    @Override
    public String toString() {
        return Arrays.toString(value);
    }

}
