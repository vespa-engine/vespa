// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * 16-bit integer value
 **/
public class Int16Value extends Value
{
    private short value;

    /**
     * Create from a Java-type value
     *
     * @param value the value
     **/
    public Int16Value(short value) { this.value = value; }

    /**
     * Create by decoding the value from the given buffer
     *
     * @param src buffer where the value is stored
     **/
    Int16Value(ByteBuffer src) {
        value = src.getShort();
    }

    /**
     * @return INT16
     **/
    public byte type() { return INT16; }
    public int count() { return 1; }

    int bytes() { return 2; }
    void encode(ByteBuffer dst) {
        dst.putShort(value);
    }

    public short asInt16() { return value; }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

}
