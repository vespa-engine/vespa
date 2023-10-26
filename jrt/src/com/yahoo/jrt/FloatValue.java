// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * 32-bit floating-point value
 **/
public class FloatValue extends Value
{
    private float value;

    /**
     * Create from a Java-type value
     *
     * @param value the value
     **/
    public FloatValue(float value) { this.value = value; }

    /**
     * Create by decoding the value from the given buffer
     *
     * @param src buffer where the value is stored
     **/
    FloatValue(ByteBuffer src) {
        value = src.getFloat();
    }

    /**
     * @return FLOAT
     **/
    public byte type() { return FLOAT; }
    public int count() { return 1; }

    int bytes() { return 4; }
    void encode(ByteBuffer dst) {
        dst.putFloat(value);
    }

    public float asFloat() { return value; }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

}
