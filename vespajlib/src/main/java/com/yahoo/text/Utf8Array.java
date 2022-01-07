// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.nio.ByteBuffer;

/**
 * This is a primitive class that owns an array of utf8 encoded string.
 * This is a class that has speed as its primary purpose.
 * If you have a string, consider Utf8String
 * If you have a large backing array consider Utf8PartialArray.
 *
 * @author baldersheim
 */
public class Utf8Array extends AbstractUtf8Array {

    protected final byte[] utf8;

    /**
     * This will simply wrap the given array assuming it is valid utf8.
     * Note that the immutability of this primitive class depends on that the buffer
     * is not modified after ownership has been transferred.
     * @param utf8data The utf8 byte sequence.
     */
    public Utf8Array(final byte[] utf8data) {
        utf8 = utf8data;
    }

    /**
     * This will create a new array from the window given. No validation done.
     * Note that this will copy data. You might also want to consider Utf8PartialArray
     * @param utf8data The base array.
     * @param offset   The offset from where to copy from
     * @param length   The number of bytes that should be copied.
     */
    public Utf8Array(byte[] utf8data, int offset, int length) {
        this.utf8 = new byte[length];
        System.arraycopy(utf8data, offset, this.utf8, 0, length);
    }

    /**
     * This will fetch length bytes from the given buffer.
     * @param buf     The ByteBuffer to read from
     * @param length  number of bytes to read
     */
    public Utf8Array(ByteBuffer buf, int length) {
        this.utf8 = new byte[length];
        buf.get(this.utf8, 0, length);
    }

    @Override
    public byte[] getBytes() {
        return utf8;
    }

    @Override
    public int getByteLength() {
        return utf8.length;
    }

    @Override
    protected int getByteOffset() {
        return 0;
    }

}
