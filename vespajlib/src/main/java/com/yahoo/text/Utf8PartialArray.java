// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * This wraps a window in a backing byte array. Without doing any copying.
 *
 * @author baldersheim
 */
public class Utf8PartialArray extends Utf8Array {

    final int offset;
    final int length;

    /**
     * Takes ownership of the given byte array. And keeps note of where
     * the interesting utf8 sequence start and its length.
     * @param utf8data The backing byte array.
     * @param offset   The start of the utf8 sequence.
     * @param bytes    The length of the utf8 sequence.
     */
    public Utf8PartialArray(byte[] utf8data, int offset, int bytes) {
        super(utf8data);
        this.offset = offset;
        this.length = bytes;
    }
    @Override
    public int getByteLength() {
        return length;
    }

    @Override
    protected int getByteOffset() {
        return offset;
    }

}
