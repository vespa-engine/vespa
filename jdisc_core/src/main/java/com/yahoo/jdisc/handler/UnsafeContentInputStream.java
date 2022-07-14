// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import java.io.IOException;
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
    private ByteBuffer currBuf = ByteBuffer.allocate(0);
    private byte [] marked;
    private int readSinceMarked;

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
        fetchNonEmptyBuffer();
        if (currBuf == null) return -1;

        byte b = currBuf.get();
        if (marked != null) {
            if (readSinceMarked < marked.length) {
                marked[readSinceMarked++] = b;
            } else {
                marked = null;
            }
        }
        return ((int)b) & 0xFF;
    }

    private boolean fetchNonEmptyBuffer() {
        while (currBuf != null && currBuf.remaining() == 0) {
            currBuf = content.read();
        }
        return (currBuf != null && currBuf.hasRemaining());
    }

    @Override
    public int read(byte buf[], int off, int len) {
        Objects.requireNonNull(buf, "buf");
        if (off < 0 || len < 0 || len > buf.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) return 0;

        if ( ! fetchNonEmptyBuffer() ) return -1;
        int read = 0;
        while ((available() > 0) && fetchNonEmptyBuffer() && ((len - read) > 0)) {
            int toRead = Math.min(currBuf.remaining(), (len - read));
            currBuf.get(buf, off + read, toRead);
            read += toRead;
        }
        if (marked != null) {
            if (readSinceMarked + read <= marked.length) {
                System.arraycopy(buf, off, marked, readSinceMarked, read);
                readSinceMarked += read;
            } else {
                marked = null;
            }

        }
        return read;
    }

    @Override
    public int available() {
        if (currBuf != null && currBuf.remaining() > 0) {
            return currBuf.remaining();
        }
        return content.available();
    }

    @Override
    public void close() {
        // noinspection StatementWithEmptyBody
        while (content.read() != null) {

        }
    }

    @Override
    public synchronized void mark(int readlimit) {
        marked = new byte[readlimit];
        readSinceMarked = 0;
    }

    @Override
    public synchronized void reset() throws IOException {
        if (marked == null) {
            throw new IOException("mark has not been called, or too much has been read since marked.");
        }
        ByteBuffer newBuf = ByteBuffer.allocate(readSinceMarked + currBuf.remaining());
        newBuf.put(marked, 0, readSinceMarked);
        newBuf.put(currBuf);
        newBuf.flip();
        currBuf = newBuf;
        marked = null;
    }

    @Override
    public boolean markSupported() {
        return true;
    }
}
