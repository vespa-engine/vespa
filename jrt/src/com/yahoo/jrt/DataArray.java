// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * Data array (an array of byte sequences)
 **/
public class DataArray extends Value
{
    private byte[][] value;

    /**
     * Create from a Java-type value
     *
     * @param value the value
     **/
    public DataArray(byte[][] value) { this.value = value; }

    /**
     * Create by decoding the value from the given buffer
     *
     * @param src buffer where the value is stored
     **/
    DataArray(ByteBuffer src) {
        int size = src.getInt();
        value = new byte[size][];
        for (int i = 0; i < size; i++) {
            value[i] = new byte[src.getInt()];
            src.get(value[i]);
        }
    }

    /**
     * @return DATA_ARRAY
     **/
    public byte type() { return DATA_ARRAY; }
    public int count() { return value.length; }

    int bytes() {
        int bytes = 4;
        for (int i = 0; i < value.length; i++) {
            bytes += 4 + value[i].length;
        }
        return bytes;
    }
    void encode(ByteBuffer dst) {
        dst.putInt(value.length);
        for (int i = 0; i < value.length; i++) {
            dst.putInt(value[i].length);
            dst.put(value[i]);
        }
    }

    public byte[][] asDataArray() { return value; }

    @Override
    public String toString() {
        return Arrays.toString(value);
    }

}
