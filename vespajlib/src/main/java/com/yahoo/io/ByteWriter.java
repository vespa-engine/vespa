// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import com.yahoo.text.Utf8;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetEncoder;

/**
 * A buffered writer which accepts byte arrays in addition to character arrays.
 *
 * @author Steinar Knutsen
 */
public class ByteWriter extends AbstractByteWriter {

    private final OutputStream stream;

    public ByteWriter(final OutputStream stream, final CharsetEncoder encoder) {
        super(encoder);
        this.stream = stream;
    }
    public ByteWriter(final OutputStream stream) {
        super(Utf8.getNewEncoder());
        this.stream = stream;
    }

    @Override
    public void send(final ByteBuffer b) throws IOException {
        // we know from how BufferChain works we have a backing array
        stream.write(b.array(), b.position() + b.arrayOffset(), b.remaining());
    }

    @Override
    public void close() throws java.io.IOException {
        buffer.flush();
        // Unit tests in prelude depends on the stream _not_ being flushed, it
        // is necessary for Jetty to write content length headers, it seems.
        // stream.flush();
        stream.close();
    }

    @Override
    public void flush() throws IOException {
        buffer.flush();
        // Unit tests in prelude depends on the stream _not_ being flushed, it
        // is necessary for Jetty to write content length headers, it seems.
        // stream.flush();
    }
}
