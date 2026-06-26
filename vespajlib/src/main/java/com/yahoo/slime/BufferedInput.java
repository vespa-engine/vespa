// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import java.io.IOException;

final class BufferedInput {

    private final ByteSource source;
    private byte[] previous;
    private byte[] current;
    private int end;
    private int start;
    private int position;
    private String failReason;
    private int failPos;

    void fail(String reason) {
        if (failed()) {
            return;
        }
        failReason = reason;
        failPos = position;
        position = end;
    }

    BufferedInput(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    BufferedInput(byte[] bytes, int offset, int length) {
        this.source = null;
        this.current = bytes;
        this.start = offset;
        position = offset;
        this.end = offset + length;
    }

    BufferedInput(ByteSource source) {
        this.source = source;
        this.current = new byte[0];
        this.start = 0;
        this.position = 0;
        this.end = 0;
    }

    /** Make the next byte array from the source the current one, keeping the old current as previous. */
    private boolean fetchNext() {
        if (source == null) {
            return false;
        }
        byte[] next;
        try {
            do {
                next = source.next();
            } while (next != null && next.length == 0);
        } catch (IOException e) {
            fail(e.getMessage());
            return false;
        }
        if (next == null) {
            return false;
        }
        previous = current;
        current = next;
        start = 0;
        position = 0;
        end = next.length;
        return true;
    }

    private boolean available() {
        if (failed()) {
            return false;
        }
        return position < end || fetchNext();
    }

    byte getByte() {
        if ( ! available()) {
            fail("underflow");
            return 0;
        }
        return current[position++];
    }

    boolean failed() {
        return failReason != null;
    }

    boolean eof() {
        return ! available();
    }

    String getErrorMessage() {
        return failReason;
    }

    int getConsumedSize() {
        return failed() ? 0 : position - start;
    }

    byte[] getOffending() {
        int fromCurrent = failPos - start;
        if (previous == null) {
            byte[] ret = new byte[fromCurrent];
            System.arraycopy(current, start, ret, 0, fromCurrent);
            return ret;
        }
        byte[] ret = new byte[previous.length + fromCurrent];
        System.arraycopy(previous, 0, ret, 0, previous.length);
        System.arraycopy(current, start, ret, previous.length, fromCurrent);
        return ret;
    }

    byte[] getBacking() { return current; }
    int getPosition() { return position; }
    void skip(int size) {
        if (position + size > end) {
            fail("underflow");
        }  else {
            position += size;
        }
    }

    byte[] getBytes(int size) {
        if (position + size > end) {
            fail("underflow");
            return new byte[0];
        }
        byte[] ret = new byte[size];
        for (int i = 0; i < size; i++) {
            ret[i] = current[position++];
        }
        return ret;
    }

    int read_cmpr_int() {
        long next = getByte();
        long value = (next & 0x7f);
        int shift = 7;
        while (shift < 32 && (next & 0x80) != 0) {
            next = getByte();
            value |= ((next & 0x7f) << shift);
            shift += 7;
        }
        if (value > 0x7fff_ffffL) {
            fail("compressed int overflow");
            value = 0;
        }
        return (int)value;
    }

    int skip_cmpr_int() {
        int extBits = 0;
        while ((getByte() & 0x80) != 0) {
            ++extBits;
        }
        return extBits;
    }

    int read_size(int meta) {
        return (meta == 0) ? read_cmpr_int() : (meta - 1);
    }
}
