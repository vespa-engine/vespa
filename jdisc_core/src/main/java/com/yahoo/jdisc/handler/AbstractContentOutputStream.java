// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
abstract class AbstractContentOutputStream extends OutputStream {

    public static final int BUFFERSIZE = 4096;
    private ByteBuffer current;

    @Override
    public final void write(int b) {
        if (current == null) {
            current = ByteBuffer.allocate(BUFFERSIZE);
        }
        current.put((byte)b);
        if (current.remaining() == 0) {
            flush();
        }
    }

    @Override
    public final void write(byte[] buffer, int offset, int length) {
        Objects.requireNonNull(buffer, "buf");
        if (current == null) {
            current = ByteBuffer.allocate(BUFFERSIZE + length);
        }
        int part = Math.min(length, current.remaining());
        current.put(buffer, offset, part);
        if (current.remaining() == 0) {
            flush();
        }
        if (part < length) {
            write(buffer, offset + part, length - part);
        }
    }

    @Override
    public final void write(byte[] buffer) {
        write(buffer, 0, buffer.length);
    }

    @Override
    public final void flush() {
        if (current == null || current.position() == 0) {
            return;
        }
        ByteBuffer buf = current;
        current = null;
        buf.flip();
        doFlush(buf);
    }

    @Override
    public final void close() {
        flush();
        doClose();
    }

    protected abstract void doFlush(ByteBuffer buf);

    protected abstract void doClose();

}
