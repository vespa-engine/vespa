// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import com.yahoo.text.AbstractUtf8Array;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Data store for AbstractByteWriter. Tested in unit tests for ByteWriter.
 *
 * @author Steinar Knutsen
 */
public final class BufferChain {
    // refer to the revision history of ByteWriter for more information about
    // the reasons behind the sizing of BUFFERSIZE, WATERMARK and MAXBUFFERS
    static final int BUFFERSIZE = 4096;
    static final int WATERMARK = 1024;
    static final int MAXBUFFERS = 50;
    static {
        //noinspection ConstantConditions
        assert BUFFERSIZE > WATERMARK;
    }
    private final List<ByteBuffer> buffers = new ArrayList<>();
    private final WritableByteTransmitter endpoint;
    private ByteBuffer current = ByteBuffer.allocate(BUFFERSIZE);
    private long appended = 0L;

    public BufferChain(final WritableByteTransmitter endpoint) {
        this.endpoint = endpoint;
    }

    public void append(final byte b) throws IOException {
        makeRoom(1);
        current.put(b);
    }
    private final boolean shouldCopy(int length) {
         return (length < WATERMARK);
    }
    private final void makeRoom(int length) throws IOException {
        if (current.remaining() < length) {
            scratch();
        }
    }
    public void append(AbstractUtf8Array v) throws IOException {
        final int length = v.getByteLength();
        if (shouldCopy(length)) {
            makeRoom(length);
            v.writeTo(current);
        } else {
            append(v.wrap());
        }
    }
    public void append(final byte[] alreadyEncoded) throws java.io.IOException {
        if (alreadyEncoded.length > 0) {
            append(alreadyEncoded, 0, alreadyEncoded.length);
        }
    }

    public void append(final byte[] alreadyEncoded, final int offset, final int length) throws java.io.IOException {
        if (shouldCopy(length)) {
            makeRoom(length);
            current.put(alreadyEncoded, offset, length);
        } else {
            append(ByteBuffer.wrap(alreadyEncoded, offset, length));
        }
    }

    public void append(final ByteBuffer alreadyEncoded) throws java.io.IOException {
        if (alreadyEncoded.remaining() == 0) {
            return;
        }
        final int length = alreadyEncoded.limit() - alreadyEncoded.position();
        if (shouldCopy(length)) {
            makeRoom(length);
            current.put(alreadyEncoded);
        } else {
            scratch();
            add(alreadyEncoded);
        }
    }
    private final void add(final ByteBuffer buf) {
        buffers.add(buf);
        appended += buf.limit();
    }

    public void append(final CharBuffer toEncode, final CharsetEncoder encoder)
            throws java.io.IOException {
        CoderResult overflow;
        do {
            overflow = encoder.encode(toEncode, current, true);
            if (overflow.isOverflow()) {
                scratch();
            } else if (overflow.isError()) {
                try {
                    toEncode.get();
                } catch (final BufferUnderflowException e) {
                    // Give up if we can't discard some presumptively malformed
                    // or unmappable data
                    break;
                }
            }
        } while (!overflow.isUnderflow());
    }

    private void scratch() throws java.io.IOException {
        if (!possibleFlush() && current.position() != 0) {
            current.flip();
            add(current);
            current = ByteBuffer.allocate(BUFFERSIZE);
        }
    }

    private boolean possibleFlush() throws java.io.IOException {
        if (buffers.size() > MAXBUFFERS) {
            flush();
            return true;
        }
        return false;
    }

    public void flush() throws IOException {
        for (final ByteBuffer b : buffers) {
            endpoint.send(b);
        }
        buffers.clear();
        if (current.position() > 0) {
            current.flip();
            appended += current.limit();
            endpoint.send(current);
            current = ByteBuffer.allocate(BUFFERSIZE);
        }
    }

    /**
     * @return number of bytes written to this buffer
     */
    public long appended() {
        return appended + current.position();
    }
}
