// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * 8-bit integer array
 **/
public class Int8Array extends Value
{
    private byte[] value;

    /**
     * Create from a Java-type value
     *
     * @param value the value
     **/
    public Int8Array(byte[] value) { this.value = value; }

    /**
     * Create by decoding the value from the given buffer
     *
     * @param src buffer where the value is stored
     **/
    Int8Array(ByteBuffer src) {
        int size = src.getInt();
        value = new byte[size];
        src.get(value);
    }

    /**
     * @return INT8_ARRAY
     **/
    public byte type() { return INT8_ARRAY; }
    public int count() { return value.length; }

    int bytes() { return 4 + value.length; }
    void encode(ByteBuffer dst) {
        dst.putInt(value.length);
        dst.put(value);
    }

    public byte[] asInt8Array() { return value; }

    @Override
    public String toString() {
        return Arrays.toString(value);
    }

}
