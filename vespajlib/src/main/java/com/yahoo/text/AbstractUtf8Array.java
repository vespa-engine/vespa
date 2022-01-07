// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.nio.ByteBuffer;

/**
 * @author baldersheim
 */
public abstract class AbstractUtf8Array implements Comparable<AbstractUtf8Array> {

    /** Writes the utf8 sequence to the given target. */
    final public void writeTo(ByteBuffer target) {
        target.put(getBytes(), getByteOffset(), getByteLength());
    }

    /** Returns the byte at the given position. */
    public byte getByte(int index) { return getBytes()[getByteOffset() + index]; }

    /** Returns the length in bytes of the utf8 sequence. */
    public abstract int getByteLength();

    /** Wraps the utf8 sequence in a ByteBuffer
     *
     * @return the wrapping buffer
     */
    public ByteBuffer wrap() { return ByteBuffer.wrap(getBytes(), getByteOffset(), getByteLength()); }

    /** Returns the backing byte array. */
    protected abstract byte [] getBytes();

    public boolean isEmpty() { return getByteLength() == 0; }

    /** Returns the offset in the backing array where the utf8 sequence starts. */
    protected abstract int getByteOffset();
    @Override
    public int hashCode() {
        final int l = getByteLength();
        final int c = getByteOffset();
        final byte [] b = getBytes();
        int h = 0;
        for (int i=0; i < l; i++) {
            int v = b[c+i];
            h ^= v << ((i%4)*8);
        }
        return h;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AbstractUtf8Array) {
            AbstractUtf8Array other = (AbstractUtf8Array)o;
            return compareTo(other) == 0;
        } else if (o instanceof String) {
            return toString().equals(o);
        }
        return false;
    }

    /** Retuerns  the utf8 sequence as a Java string. */
    @Override
    public String toString() {
        return Utf8.toString(getBytes(), getByteOffset(), getByteLength());
    }

    @Override
    public int compareTo(AbstractUtf8Array rhs) {
        final int l = getByteLength();
        final int rl = rhs.getByteLength();
        if (l < rl) {
            return -1;
        } else if (l > rl) {
            return 1;
        } else {
            final byte [] b = getBytes();
            final byte [] rb = rhs.getBytes();
            final int c = getByteOffset();
            final int rc = rhs.getByteOffset();
            for (int i=0; i < l; i++) {
                if (b[c+i] < rb[rc+i]) {
                    return -1;
                } else if (b[c+i] > rb[rc+i]) {
                    return 1;
                }
            }
            return 0;
        }
    }

    public Utf8Array ascii7BitLowerCase() {
        byte [] upper = new byte[getByteLength()];

        for (int i=0; i< upper.length; i++ ) {
            byte b = getByte(i);
            if ((b >= 0x41) && (b < (0x41+26))) {
                b |= 0x20; // Lowercase
            }
            upper[i] = b;
        }
        return new Utf8Array(upper);
    }

}
