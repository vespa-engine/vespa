// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class AbstractContentOutputStreamTestCase {

    @Test
    void requireThatStreamCanBeWrittenTo() throws IOException {
        MyOutputStream out = new MyOutputStream();
        int len = 2 * AbstractContentOutputStream.BUFFERSIZE;
        for (int i = 0; i < len; ++i) {
            out.write(69);
            out.write(new byte[]{});
            out.write(new byte[]{6, 9});
            out.write(new byte[]{6, 69, 9}, 1, 0); // zero length
            out.write(new byte[]{6, 69, 9}, 1, 1);
        }
        out.close();

        InputStream in = out.toInputStream();
        for (int i = 0; i < len; ++i) {
            assertEquals(69, in.read());
            assertEquals(6, in.read());
            assertEquals(9, in.read());
            assertEquals(69, in.read());
        }
        assertEquals(-1, in.read());
        assertTrue(out.closed);
    }

    @Test
    void requireThatBigBuffersAreWrittenInOrder() throws IOException {
        MyOutputStream out = new MyOutputStream();
        out.write(6);
        out.write(new byte[2 * AbstractContentOutputStream.BUFFERSIZE]);
        out.write(9);
        out.close();
        InputStream in = out.toInputStream();
        assertEquals(6, in.read());
        for (int i = 0, len = 2 * AbstractContentOutputStream.BUFFERSIZE; i < len; ++i) {
            assertEquals(0, in.read());
        }
        assertEquals(9, in.read());
        assertEquals(-1, in.read());
        assertTrue(out.closed);
    }

    @Test
    void requireThatEmptyBuffersAreNotFlushed() throws Exception {
        MyOutputStream out = new MyOutputStream();
        out.close();
        assertTrue(out.writes.isEmpty());
        assertTrue(out.closed);
    }

    @Test
    void requireThatNoExcessiveBytesAreWritten() throws Exception {
        MyOutputStream out = new MyOutputStream();
        out.write(new byte[]{6, 9});
        out.close();

        InputStream in = out.toInputStream();
        assertEquals(2, in.available());
        assertEquals(6, in.read());
        assertEquals(9, in.read());
        assertEquals(0, in.available());
        assertEquals(-1, in.read());
        assertTrue(out.closed);
    }

    @Test
    void requireThatWrittenArraysAreCopied() throws Exception {
        MyOutputStream out = new MyOutputStream();
        byte[] buf = new byte[1];
        for (byte b = 0; b < 127; ++b) {
            buf[0] = b;
            out.write(buf);
        }
        out.close();

        InputStream in = out.toInputStream();
        for (byte b = 0; b < 127; ++b) {
            assertEquals(b, in.read());
        }
    }

    private static class MyOutputStream extends AbstractContentOutputStream {

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final List<ByteBuffer> writes = new ArrayList<>();
        boolean closed;

        @Override
        protected void doFlush(ByteBuffer buf) {
            writes.add(buf);
            buf = buf.slice();
            while (buf.hasRemaining()) {
                out.write(buf.get());
            }
        }

        @Override
        protected void doClose() {
            closed = true;
        }

        InputStream toInputStream() {
            return new ByteArrayInputStream(out.toByteArray());
        }
    }
}
