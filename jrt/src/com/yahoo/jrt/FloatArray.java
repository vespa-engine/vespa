// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * 32-bit floating-point array
 **/
public class FloatArray extends Value
{
    private float[] value;

    /**
     * Create from a Java-type value
     *
     * @param value the value
     **/
    public FloatArray(float[] value) { this.value = value; }

    /**
     * Create by decoding the value from the given buffer
     *
     * @param src buffer where the value is stored
     **/
    FloatArray(ByteBuffer src) {
        int size = src.getInt();
        value = new float[size];
        src.asFloatBuffer().get(value);
        src.position(src.position() + size * 4);
    }

    /**
     * @return FLOAT_ARRAY
     **/
    public byte type() { return FLOAT_ARRAY; }
    public int count() { return value.length; }

    int bytes() { return 4 + value.length * 4; }
    void encode(ByteBuffer dst) {
        dst.putInt(value.length);
        dst.asFloatBuffer().put(value);
        dst.position(dst.position() + value.length * 4);
    }

    public float[] asFloatArray() { return value; }

    @Override
    public String toString() {
        return Arrays.toString(value);
    }

}
