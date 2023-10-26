// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;


/**
 * 64-bit integer value
 **/
public class Int64Value extends Value
{
    private long value;

    /**
     * Create from a Java-type value
     *
     * @param value the value
     **/
    public Int64Value(long value) { this.value = value; }

    /**
     * Create by decoding the value from the given buffer
     *
     * @param src buffer where the value is stored
     **/
    Int64Value(ByteBuffer src) {
        value = src.getLong();
    }

    /**
     * @return INT64
     **/
    public byte type() { return INT64; }
    public int count() { return 1; }

    int bytes() { return 8; }
    void encode(ByteBuffer dst) {
        dst.putLong(value);
    }

    public long asInt64() { return value; }

    @Override
    public String toString() {
        return String.valueOf(value);
    }


}
