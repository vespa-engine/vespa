// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * 32-bit integer array
 **/
public class Int32Array extends Value
{
    private int[] value;

    /**
     * Create from a Java-type value
     *
     * @param value the value
     **/
    public Int32Array(int[] value) { this.value = value; }

    /**
     * Create by decoding the value from the given buffer
     *
     * @param src buffer where the value is stored
     **/
    Int32Array(ByteBuffer src) {
        int size = src.getInt();
        value = new int[size];
        src.asIntBuffer().get(value);
        src.position(src.position() + size * 4);
    }

    /**
     * @return INT32_ARRAY
     **/
    public byte type() { return INT32_ARRAY; }
    public int count() { return value.length; }

    int bytes() { return 4 + value.length * 4; }
    void encode(ByteBuffer dst) {
        dst.putInt(value.length);
        dst.asIntBuffer().put(value);
        dst.position(dst.position() + value.length * 4);
    }

    public int[] asInt32Array() { return value; }

    @Override
    public String toString() {
        return Arrays.toString(value);
    }


}
