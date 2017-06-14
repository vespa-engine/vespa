// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import java.nio.ByteBuffer;

/**
 * A Blob contains opaque data in the form of a byte array.
 **/
public class Blob {

    /**
     * Shared empty array.
     **/
    private static byte[] empty = new byte[0];

    /**
     * Internal data, will never be 'null'.
     **/
    private byte[] data;

    /**
     * Create a Blob containing an empty byte array.
     **/
    public Blob() {
        data = empty;
    }

    /**
     * Create a Blob containg a copy of a subset of the given byte
     * array.
     **/
    public Blob(byte[] src, int offset, int length) {
        data = new byte[length];
        System.arraycopy(src, offset, data, 0, length);
    }

    /**
     * Create a Blob containing a copy of the given byte array.
     **/
    public Blob(byte[] src) {
        this(src, 0, src.length);
    }

    /**
     * Create a Blob containing a copy of the data held by the given
     * blob.
     **/
    public Blob(Blob src) {
        this(src.data);
    }

    /**
     * Create a Blob containing a number of bytes read from a byte
     * buffer.
     **/
    public Blob(ByteBuffer src, int length) {
        data = new byte[length];
        src.get(data);
    }

    /**
     * Create a Blob containing all bytes that could be read from a
     * byte buffer.
     **/
    public Blob(ByteBuffer src) {
        this(src, src.remaining());
    }

    /**
     * Obtain the internal data held by this object.
     *
     * @return internal data
     **/
    public byte[] get() {
        return data;
    }

    /**
     * Write the data held by this object to the given byte buffer.
     *
     * @param dst where to write the contained data
     **/
    public void write(ByteBuffer dst) {
        dst.put(data);
    }
}
