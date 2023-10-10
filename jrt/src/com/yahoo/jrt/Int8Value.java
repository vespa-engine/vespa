// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;


/**
 * 8-bit integer value
 **/
public class Int8Value extends Value
{
    private byte value;

    /**
     * Create from a Java-type value
     *
     * @param value the value
     **/
    public Int8Value(byte value) { this.value = value; }

    /**
     * Create by decoding the value from the given buffer
     *
     * @param src buffer where the value is stored
     **/
    Int8Value(ByteBuffer src) {
        value = src.get();
    }

    /**
     * @return INT8
     **/
    public byte type() { return INT8; }
    public int count() { return 1; }

    int bytes() { return 1; }
    void encode(ByteBuffer dst) {
        dst.put(value);
    }

    public byte asInt8() { return value; }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

}
