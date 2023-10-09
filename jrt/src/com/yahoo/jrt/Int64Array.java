// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * 64-bit integer array
 **/
public class Int64Array extends Value
{
    private long[] value;

    /**
     * Create from a Java-type value
     *
     * @param value the value
     **/
    public Int64Array(long[] value) { this.value = value; }

    /**
     * Create by decoding the value from the given buffer
     *
     * @param src buffer where the value is stored
     **/
    Int64Array(ByteBuffer src) {
        int size = src.getInt();
        value = new long[size];
        src.asLongBuffer().get(value);
        src.position(src.position() + size * 8);
    }

    /**
     * @return INT64_ARRAY
     **/
    public byte type() { return INT64_ARRAY; }
    public int count() { return value.length; }

    int bytes() { return 4 + value.length * 8; }
    void encode(ByteBuffer dst) {
        dst.putInt(value.length);
        dst.asLongBuffer().put(value);
        dst.position(dst.position() + value.length * 8);
    }

    public long[] asInt64Array() { return value; }

    @Override
    public String toString() {
        return Arrays.toString(value);
    }

}
