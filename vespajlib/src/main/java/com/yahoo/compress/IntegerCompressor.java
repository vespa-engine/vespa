// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.compress;

import java.nio.ByteBuffer;

/**
 * Utility for bytewise compressing of integers into a ByteBuffer.
 *
 * @author baldersheim
 */
public class IntegerCompressor {

    public static void putCompressedNumber(int n, ByteBuffer buf) {
        int negative = n < 0 ? 0x80 : 0x0;
        if (negative != 0) {
            n = -n;
        }
        if (n < (0x1 << 5)) {
            byte b = (byte)(n | negative);
            buf.put(b);
        } else if (n < (0x1 << 13)) {
            n = n | 0x4000 | (negative << 8);
            buf.putShort((short)n);
        } else if ( n < (0x1 << 29)) {
            n = n | 0x60000000 | (negative << 24);
            buf.putInt(n);
        } else {
            throw new IllegalArgumentException("Number '" + ((negative != 0) ? -n : n) + "' too big, must extend encoding");
        }
    }

    public static void putCompressedPositiveNumber(int n, ByteBuffer buf) {
        if (n < 0) {
            throw new IllegalArgumentException("Number '" + n + "' must be positive");
        }
        if (n < (0x1 << 6)) {
            buf.put((byte)n);
        } else if (n < (0x1 << 14)) {
            n = n | 0x8000;
            buf.putShort((short)n);
        } else if ( n < (0x1 << 30)) {
            n = n | 0xc0000000;
            buf.putInt(n);
        } else {
            throw new IllegalArgumentException("Number '" + n + "' too big, must extend encoding");
        }
    }
}
