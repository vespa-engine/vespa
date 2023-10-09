// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;


/**
 * 32-bit integer value
 **/
public class Int32Value extends Value
{
    private int value;

    /**
     * Create from a Java-type value
     *
     * @param value the value
     **/
    public Int32Value(int value) { this.value = value; }

    /**
     * Create by decoding the value from the given buffer
     *
     * @param src buffer where the value is stored
     **/
    Int32Value(ByteBuffer src) {
        value = src.getInt();
    }

    /**
     * @return INT32
     **/
    public byte type() { return INT32; }
    public int count() { return 1; }

    int bytes() { return 4; }
    void encode(ByteBuffer dst) {
        dst.putInt(value);
    }

    public int asInt32() { return value; }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

}
