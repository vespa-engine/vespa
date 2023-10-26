// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import com.yahoo.text.Utf8Array;
import com.yahoo.text.Utf8String;

import java.nio.ByteBuffer;


/**
 * String value. The internal string representation is UTF-8 encoded
 * bytes. This means that creating an object of this class as well as
 * extracting the value contained with the {@link #asString asString}
 * method will incur a string conversion overhead.
 **/
public class StringValue extends Value
{
    private Utf8Array value;

    /**
     * Create from a Java-type value
     *
     * @param value the value
     **/
    public StringValue(String value) {
        this.value = new Utf8String(value);
    }
    public StringValue(Utf8String value) {
        this.value = value;
    }
    public StringValue(Utf8Array value) {
        this.value = value;
    }

    /**
     * Create by decoding the value from the given buffer
     *
     * @param src buffer where the value is stored
     **/
    StringValue(ByteBuffer src) {
        int size = src.getInt();
        value = new Utf8String(new Utf8Array(src, size));
    }

    /**
     * @return STRING
     **/
    public byte type() { return STRING; }
    public int count() { return 1; }

    int bytes() { return 4 + value.getByteLength(); }
    void encode(ByteBuffer dst) {
        dst.putInt(value.getByteLength());
        value.writeTo(dst);
    }

    public String asString() {
        return value.toString();
    }

    @Override
    public Utf8Array asUtf8Array() { return value; }

    @Override
    public String toString() {
        return asString();
    }

}
