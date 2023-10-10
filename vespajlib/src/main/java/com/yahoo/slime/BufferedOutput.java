// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import com.yahoo.compress.Compressor;

import java.nio.charset.Charset;

final class BufferedOutput {

    private byte[] buf;
    private int capacity;
    private int pos;

    BufferedOutput(int cap) {
        capacity = Math.max(cap, 64);
        buf = new byte[capacity];
    }

    BufferedOutput() {
        this(4096);
    }

    void reset() {
        pos = 0;
    }

    private void reserve(int bytes) {
        if (pos + bytes > capacity) {
            while (pos + bytes > capacity) {
                capacity = capacity * 2;
            }
            byte[] tmp = new byte[capacity];
            System.arraycopy(buf, 0, tmp, 0, pos);
            buf = tmp;
        }
    }

    int position() { return pos; }

    void put(byte b) {
        reserve(1);
        buf[pos++] = b;
    }

    void absolutePut(int position, byte b) {
        buf[position] = b;
    }

    void put(byte[] bytes) {
        reserve(bytes.length);
        for (byte b : bytes) {
            buf[pos++] = b;
        }
    }

    byte[] toArray() {
        byte[] ret = new byte[pos];
        System.arraycopy(buf, 0, ret, 0, pos);
        return ret;
    }
    public String toString(Charset charset) {
        return new String(buf, 0, pos, charset);
    }
    Compressor.Compression compress(Compressor compressor) {
        return compressor.compress(buf, pos);
    }
}
