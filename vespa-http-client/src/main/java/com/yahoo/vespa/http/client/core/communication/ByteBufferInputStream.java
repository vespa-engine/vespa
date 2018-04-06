// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * @author Einar M R Rosenvinge
 */
class ByteBufferInputStream extends InputStream {
    private final Deque<ByteBuffer> currentBuffers = new ArrayDeque<>();

    ByteBufferInputStream(ByteBuffer[] buffers) {
        for (int i = buffers.length - 1; i > -1; i--) {
            currentBuffers.push(buffers[i]);
        }
    }

    @Override
    public int read() throws IOException {
        pop();
        if (currentBuffers.isEmpty()) {
            return -1;
        }
        return currentBuffers.peek().get();
    }

    private void pop() {
        if (currentBuffers.isEmpty()) {
            return;
        }

        while (!currentBuffers.isEmpty() && !currentBuffers.peek().hasRemaining()) {
            //it's exhausted, get rid of it
            currentBuffers.pop();
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }
        pop();
        if (currentBuffers.isEmpty()) {
            return -1;
        }
        int toRead = Math.min(len, currentBuffers.peek().remaining());
        currentBuffers.peek().get(b, off, toRead);
        return toRead;
    }

    @Override
    public long skip(long n) throws IOException {
        throw new IOException("skip() not supported.");
    }

    @Override
    public int available() throws IOException {
        if (currentBuffers.isEmpty()) {
            return 0;
        }

        int size = 0;
        for (ByteBuffer b : currentBuffers) {
            size += b.remaining();
        }
        return size;
    }
}
