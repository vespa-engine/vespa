// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;


/**
 * Data value (a sequence of bytes)
 **/
public class DataValue extends Value
{
    private final byte[] value;

    /**
     * Create from a Java-type value
     *
     * @param value the value
     **/
    public DataValue(byte[] value) { this.value = value; }

    /**
     * Create by decoding the value from the given buffer
     *
     * @param src buffer where the value is stored
     **/
    DataValue(ByteBuffer src) {
        int size = src.getInt();
        value = new byte[size];
        src.get(value);
    }

    /**
     * @return DATA
     **/
    public byte type() { return DATA; }
    public int count() { return 1; }

    int bytes() { return 4 + value.length; }
    void encode(ByteBuffer dst) {
        dst.putInt(value.length);
        dst.put(value);
    }

    public byte[] asData() { return value; }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

}
