// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class BufferedInput {

    record Bytes(byte[] data, int start, int end) {
        int size() { return end - start; }
    }

    private Bytes current;
    private Bytes previous = null;
    private ByteSource byteSource;
    private int position;
    private String failReason = null;
    private int failPos = -1;
    private int discarded = 0;

    void fail(String reason) {
        if (failed()) {
            return;
        }
        failReason = reason;
        failPos = position;
        position = current.end();
        byteSource = null;
    }

    BufferedInput(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    BufferedInput(byte[] bytes, int offset, int length) {
        this.current = new Bytes(bytes, offset, offset + length);
        this.position = offset;
        this.byteSource = null;
    }

    BufferedInput(ByteSource byteSource) {
        // Start empty; the first chunk is fetched lazily
        this.current = new Bytes(new byte[0], 0, 0);
        this.position = 0;
        this.byteSource = byteSource;
    }

    private boolean getMore() {
        if (byteSource == null) {
            return false;
        }
        try {
            byte[] next = byteSource.next();
            if (next == null || next.length == 0) {
                byteSource = null;
                return false;
            }
            if (previous != null) {
                discarded += previous.size();
            }
            if (current.size() > 0) {
                previous = current;
            }
            current = new Bytes(next, 0, next.length);
            position = current.start();
            return true;
        } catch (IOException e) {
            fail("IO error reading input: " + e);
            return false;
        }
    }

    byte getByte() {
        // Fetch the next chunk when the current one is exhausted
        if (position == current.end() && ! getMore()) {
            fail("underflow");
            return 0;
        }
        return current.data()[position++];
    }

    boolean failed() {
        return failReason != null;
    }

    boolean eof() {
        // Pull the next chunk on demand: we are at end-of-input only if the current
        // chunk is exhausted and no more chunks can be fetched.
        return position == current.end() && ! getMore();
    }

    String getErrorMessage() {
        return failReason;
    }

    int getConsumedSize() {
        if (failed()) {
            return 0;
        }
        int consumed = discarded;
        if (previous != null) {
            consumed += previous.size();
        }
        consumed += position - current.start();
        return consumed;
    }

    byte[] getOffending() {
        int fromCurr = (failPos < current.start()) ? 0 : (failPos - current.start());
        if (previous == null) {
            byte[] ret = new byte[fromCurr];
            System.arraycopy(current.data(), current.start(), ret, 0, fromCurr);
            return ret;
        }
        byte[] prefix = (discarded > 0)
                ? ("[... " + discarded + " bytes ...]").getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        int fromPrev = previous.size();
        byte[] ret = new byte[prefix.length + fromPrev + fromCurr];
        System.arraycopy(prefix, 0, ret, 0, prefix.length);
        System.arraycopy(previous.data(), previous.start(), ret, prefix.length, fromPrev);
        System.arraycopy(current.data(), current.start(), ret, prefix.length + fromPrev, fromCurr);
        return ret;
    }

    int getPosition() { return position; }

    void skip(int size) {
        if (size < 0) {
            fail("underflow");
            return;
        }
        // Compare with subtraction so a bogus huge 'size' cannot cause overflow.
        while (size > current.end() - position) {
            size -= (current.end() - position);
            position = current.end();
            if (! getMore()) {
                fail("underflow");
                return;
            }
        }
        position += size;
    }

    byte[] getBytes(int size) {
        if (size < 0) {
            fail("underflow");
            return new byte[0];
        }
        if (size <= current.end() - position) {
            byte[] ret = new byte[size];
            System.arraycopy(current.data(), position, ret, 0, size);
            skip(size);
            return ret;
        }
        // Streaming: the total length of the input is unknown up
        // front, so grow the buffer as bytes actually arrive rather
        // than trusting 'size'. A bogus large size fails with
        // underflow once the stream ends instead of OOM-ing on
        // allocation.
        byte[] ret = new byte[Math.min(size, 4096)];
        for (int i = 0; i < size; i++) {
            if (eof()) {
                fail("underflow");
                return new byte[0];
            }
            if (i == ret.length) {
                ret = Arrays.copyOf(ret, (int) Math.min((long) size, ret.length * 2L));
            }
            ret[i] = getByte();
        }
        return ret;
    }

    Bytes getBytesView(int size) {
        if (size >= 0 && size <= current.end() - position) {
            var ret = new Bytes(current.data(), position, position + size);
            skip(size);
            return ret;
        } else {
            var data = getBytes(size);
            return new Bytes(data, 0, data.length);
        }
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
