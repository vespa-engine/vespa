// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Light-weight index structure describing the layout of a Slime value
 * encoded in binary format.
 **/
final class DecodeIndex {
    private long[] data;
    private int reserved;
    private int used = 0;
    private final int totalSize;
    private final int rootOffset;

    private int binarySize() { return totalSize - rootOffset; }

    private int adjustSize(int minSize, int maxSize, int cnt, int byteOffset) {
        double density = (double)cnt / (double)(byteOffset - rootOffset);
        double estSize = 1.1 * density * binarySize();
        double expSize = 1.25 * data.length;
        double wantedSize = (estSize > expSize) ? estSize : expSize;
        if (wantedSize < minSize) {
            return minSize;
        }
        if (wantedSize > maxSize) {
            return maxSize;
        }
        return (int)wantedSize;
    }

    DecodeIndex(int totalSize, int rootOffset) {
        this.totalSize = totalSize;
        this.rootOffset = rootOffset;
        int initialCapacity = Math.max(16, binarySize() / 24);
        data = new long[initialCapacity];
        reserved = 1;
    }

    long[] getBacking() { return data; }

    int tryReserveChildren(int n, int cnt, int byteOffset) {
        int offset = reserved;
        if (n > data.length - reserved) {
            final int maxSize = (totalSize - byteOffset) + cnt;
            if (n > maxSize - reserved) {
                return -1; // error; too much space requested
            }
            long[] old = data;
            data = new long[adjustSize(reserved + n, maxSize, cnt, byteOffset)];
            System.arraycopy(old, 0, data, 0, reserved);
        }
        reserved += n;
        return offset;
    }

    int size() { return reserved; }
    int used() { return used; }
    int capacity() { return data.length; }

    void set(int idx, int byteOffset, int firstChild, int extBits) {
        data[idx] = (long)(byteOffset & 0x7fff_ffff) << 33 |
            (long)(firstChild & 0x7fff_ffff) << 2 |
            extBits & 0x3;
        ++used;
    }
}
