// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * <p>This class provides an adapter from a {@link ReadableContentChannel} to an InputStream. This class supports all
 * regular InputStream operations, and can be combined with any other InputStream API.</p>
 *
 * <p>Because this class encapsulates the reference-counted {@link ContentChannel} operations, one must be sure to
 * always call {@link #close()} before discarding it. Failure to do so will prevent the Container from ever shutting
 * down.</p>
 *
 * @author Simon Thoresen Hult
 */
public class UnsafeContentInputStream extends InputStream {

    private final ReadableContentChannel content;
    private ByteBuffer buf = ByteBuffer.allocate(0);

    /**
     * <p>Constructs a new ContentInputStream that reads from the given {@link ReadableContentChannel}.</p>
     *
     * @param content The content to read the stream from.
     */
    public UnsafeContentInputStream(ReadableContentChannel content) {
        this.content = content;
    }

    @Override
    public int read() {
        while (buf != null && buf.remaining() == 0) {
            buf = content.read();
        }
        if (buf == null) {
            return -1;
        }
        return ((int)buf.get()) & 0xFF;
    }

    @Override
    public int read(byte buf[], int off, int len) {
        Objects.requireNonNull(buf, "buf");
        if (off < 0 || len < 0 || len > buf.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }
        int c = read();
        if (c == -1) {
            return -1;
        }
        buf[off] = (byte)c;
        int cnt = 1;
        for (; cnt < len && available() > 0; ++cnt) {
            if ((c = read()) == -1) {
                break;
            }
            buf[off + cnt] = (byte)c;
        }
        return cnt;
    }

    @Override
    public int available() {
        if (buf != null && buf.remaining() > 0) {
            return buf.remaining();
        }
        return content.available();
    }

    @Override
    public void close() {
        // noinspection StatementWithEmptyBody
        while (content.read() != null) {

        }
    }
}
