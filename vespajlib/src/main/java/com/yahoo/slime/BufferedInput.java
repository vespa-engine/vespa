// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

final class BufferedInput {

    private final byte[] source;
    private final int end;
    private final int start;
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
        this.source = bytes;
        this.start = offset;
        position = offset;
        this.end = offset + length;
    }
    byte getByte() {
        if (position == end) {
            fail("underflow");
            return 0;
        }
        return source[position++];
    }

    boolean failed() {
        return failReason != null;
    }

    boolean eof() {
        return this.position == this.end;
    }

    String getErrorMessage() {
        return failReason;
    }

    int getConsumedSize() {
        return failed() ? 0 : position - start;
    }

    byte[] getOffending() {
        byte[] ret = new byte[failPos-start];
        System.arraycopy(source, start, ret, 0, failPos-start);
        return ret;
    }

    byte [] getBacking() { return source; }
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
            ret[i] = source[position++];
        }
        return ret;
    }
}
