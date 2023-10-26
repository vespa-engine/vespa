// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * 16-bit integer array
 **/
public class Int16Array extends Value
{
    private short[] value;

    /**
     * Create from a Java-type value
     *
     * @param value the value
     **/
    public Int16Array(short[] value) { this.value = value; }

    /**
     * Create by decoding the value from the given buffer
     *
     * @param src buffer where the value is stored
     **/
    Int16Array(ByteBuffer src) {
        int size = src.getInt();
        value = new short[size];
        src.asShortBuffer().get(value);
        src.position(src.position() + size * 2);
    }

    /**
     * @return INT16_ARRAY
     **/
    public byte type() { return INT16_ARRAY; }
    public int count() { return value.length; }

    int bytes() { return 4 + value.length * 2; }
    void encode(ByteBuffer dst) {
        dst.putInt(value.length);
        dst.asShortBuffer().put(value);
        dst.position(dst.position() + value.length * 2);
    }

    public short[] asInt16Array() { return value; }

    @Override
    public String toString() {
        return Arrays.toString(value);
    }

}
