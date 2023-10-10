// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * String array. The internal string representation is UTF-8 encoded
 * bytes. This means that creating an object of this class as well as
 * extracting the value contained with the {@link #asStringArray
 * asStringArray} method will incur a string conversion overhead.
 **/
public class StringArray extends Value
{
    private byte[][] value;

    /**
     * Create from a Java-type value
     *
     * @param value the value
     **/
    public StringArray(String[] value) {
        this.value = new byte[value.length][];
        for (int i = 0; i < value.length; i++) {
            try {
                this.value[i] = value[i].getBytes("UTF-8");
            } catch(java.io.UnsupportedEncodingException e) {}
        }
    }

    /**
     * Create by decoding the value from the given buffer
     *
     * @param src buffer where the value is stored
     **/
    StringArray(ByteBuffer src) {
        int size = src.getInt();
        value = new byte[size][];
        for (int i = 0; i < size; i++) {
            value[i] = new byte[src.getInt()];
            src.get(value[i]);
        }
    }

    /**
     * @return STRING_ARRAY
     **/
    public byte type() { return STRING_ARRAY; }
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

    public String[] asStringArray() {
        String[] ret = new String[value.length];
        for (int i = 0; i < value.length; i++) {
            try {
                ret[i] = new String(value[i], "UTF-8");
            } catch(java.io.UnsupportedEncodingException e) {}
        }
        return ret;
    }

    @Override
    public String toString() {
        return Arrays.toString(asStringArray());
    }

}
