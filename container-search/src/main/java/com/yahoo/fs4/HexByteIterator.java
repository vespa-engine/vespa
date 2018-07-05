// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * Provides sequential access to each byte of a buffer
 * as a hexadecimal string of length 2.
 *
 * @author Tony Vaagenes
 */
public final class HexByteIterator implements Iterator<String> {
    private final ByteBuffer buffer;

    private String hexByte(byte b) {
        final int unsignedValue = ((int)b) & 0xff;
        String s = Integer.toHexString(unsignedValue).toUpperCase();

        boolean singleChar = unsignedValue < 0x10;
        if (singleChar)
            return '0' + s;
        else
            return s;
    }

    public boolean hasNext() {
        return buffer.hasRemaining();
    }

    public String next() {
        return hexByte(buffer.get());
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }

    public HexByteIterator(ByteBuffer buffer) {
        this.buffer = buffer.slice();
    }
}
