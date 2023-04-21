// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Light-weight index structure describing the layout of a Slime value
 * encoded in binary format.
 **/
final class DecodeIndex {
    static final int initial_capacity = 16;
    private long[] data = new long[initial_capacity];
    private int reserved = 0;

    private int adjustSize(int minSize) {
        int capacity = initial_capacity;
        while (capacity < minSize) {
            capacity = capacity << 1;
        }
        return capacity;
    }

    int reserve(int n) {
        int offset = reserved;
        if (reserved + n > data.length) {
            long[] old = data;
            data = new long[adjustSize(reserved + n)];
            System.arraycopy(old, 0, data, 0, reserved);
        }
        reserved += n;
        return offset;
    }

    int size() { return reserved; }

    void set(int idx, int byteOffset, int firstChild, int extBits) {
        data[idx] = (long)(byteOffset & 0x7fff_ffff) << 33 |
            (long)(firstChild & 0x7fff_ffff) << 2 |
            extBits & 0x3;
    }

    int getByteOffset(int idx) {
        return (int)(data[idx] >> 33) & 0x7fff_ffff;
    }

    int getFirstChild(int idx) {
        return (int)(data[idx] >> 2) & 0x7fff_ffff;
    }

    int getExtBits(int idx) {
        return (int)data[idx] & 0x3;
    }
}
