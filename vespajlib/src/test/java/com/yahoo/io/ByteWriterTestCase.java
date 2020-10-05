// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.IdentityHashMap;

import com.yahoo.text.Utf8;
import com.yahoo.text.Utf8Array;
import org.junit.Test;

/**
 * Test the ByteWriter class. ByteWriter is also implicitly tested in
 * com.yahoo.prelude.templates.test.TemplateTestCase.
 *
 * @author Steinar Knutsen
 */
public class ByteWriterTestCase {

    private final CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();
    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();

    /**
     * A stream which does nothing, but complains if it is called and asked to
     * do nothing.
     */
    private static class CurmudgeonlyStream extends OutputStream {

        static final String ZERO_LENGTH_WRITE = "Was asked to do zero length write.";

        @Override
        public void write(int b) {
            // NOP
        }

        @Override
        public void close() {
            // NOP
        }

        @Override
        public void flush() {
            // NOP
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                throw new IOException(ZERO_LENGTH_WRITE);
            }
        }

        @Override
        public void write(byte[] b) throws IOException {
            if (b.length == 0) {
                throw new IOException(ZERO_LENGTH_WRITE);
            }
        }

    }

    @Test
    public void testMuchData() throws java.io.IOException {
        final int SINGLE_BUFFER = 500;
        final int APPENDS = 500;
        assertTrue("Code has been changed making constants in this test meaningless, review test.",
                BufferChain.BUFFERSIZE * BufferChain.MAXBUFFERS < SINGLE_BUFFER * APPENDS);
        assertTrue("Code has been changed making constants in this test meaningless, review test.",
                BufferChain.WATERMARK > SINGLE_BUFFER);
        stream.reset();
        byte[] c = new byte[SINGLE_BUFFER];
        Arrays.fill(c, (byte) 'a');
        ByteWriter bw = new ByteWriter(stream, encoder);
        for (int i = APPENDS; i > 0; --i) {
            bw.append(c);
        }
        bw.close();
        byte[] res = stream.toByteArray();
        assertEquals("BufferChain has duplicated or lost a buffer.", SINGLE_BUFFER * APPENDS, res.length);
        byte[] completeData = new byte[SINGLE_BUFFER * APPENDS];
        Arrays.fill(completeData, (byte) 'a');
        assertTrue("ByteWriter seems to have introduced data errors.", Arrays.equals(completeData, res));
    }

    @Test
    public void testLongString() throws IOException {
        final int length = BufferChain.BUFFERSIZE * BufferChain.MAXBUFFERS * 3;
        StringBuilder b = new StringBuilder(length);
        String s;
        for (int i = length; i > 0; --i) {
            b.append("\u00E5");
        }
        s = b.toString();
        stream.reset();
        ByteWriter bw = new ByteWriter(stream, encoder);
        bw.write(s);
        bw.close();
        String res = stream.toString("UTF-8");
        assertEquals(s, res);
    }

    @Test
    public void testNoSpuriousWrite() throws IOException {
        OutputStream grumpy = new CurmudgeonlyStream();
        ByteWriter bw = new ByteWriter(grumpy, encoder);
        final int SINGLE_BUFFER = 500;
        final int APPENDS = 500;
        assertTrue("Code has been changed making constants in this test meaningless, review test.",
                BufferChain.BUFFERSIZE * BufferChain.MAXBUFFERS < SINGLE_BUFFER * APPENDS);
        assertTrue("Code has been changed making constants in this test meaningless, review test.",
                BufferChain.WATERMARK > SINGLE_BUFFER);
        stream.reset();
        byte[] c = new byte[SINGLE_BUFFER];
        for (int i = APPENDS; i > 0; --i) {
            try {
                bw.append(c);
            } catch (IOException e) {
                if (e.getMessage() == CurmudgeonlyStream.ZERO_LENGTH_WRITE) {
                    fail(CurmudgeonlyStream.ZERO_LENGTH_WRITE);
                } else {
                    throw e;
                }
            }
        }
        try {
            bw.close();
        } catch (IOException e) {
            if (e.getMessage() == CurmudgeonlyStream.ZERO_LENGTH_WRITE) {
                fail(CurmudgeonlyStream.ZERO_LENGTH_WRITE);
            } else {
                throw e;
            }
        }
    }

    @Test
    public void testDoubleFlush() throws IOException {
        stream.reset();
        byte[] c = new byte[] { 97, 98, 99 };
        ByteWriter bw = new ByteWriter(stream, encoder);
        bw.append(c);
        bw.flush();
        bw.flush();
        bw.close();
        byte[] res = stream.toByteArray();
        assertTrue(Arrays.equals(new byte[] { 97, 98, 99 }, res));
    }

    @Test
    public void testCharArrays() throws java.io.IOException {
        stream.reset();
        char[] c = new char[] { 'a', 'b', 'c', '\u00F8' };
        ByteWriter bw = new ByteWriter(stream, encoder);
        bw.write(c);
        bw.close();
        byte[] res = stream.toByteArray();
        assertTrue(Arrays.equals(new byte[] { 97, 98, 99, (byte) 0xc3, (byte) 0xb8 }, res));
    }

    @Test
    public void testByteBuffers() throws java.io.IOException {
        stream.reset();
        ByteBuffer b = ByteBuffer.allocate(16);
        b.put((byte) 97);
        b.put((byte) 98);
        b.put((byte) 99);
        ByteWriter bw = new ByteWriter(stream, encoder);
        b.flip();
        bw.append(b);
        bw.close();
        byte[] res = stream.toByteArray();
        assertTrue(Arrays.equals(new byte[] { 97, 98, 99 }, res));
    }

    @Test
    public void testByteArrays() throws java.io.IOException {
        stream.reset();
        byte[] c = new byte[] { 97, 98, 99 };
        ByteWriter bw = new ByteWriter(stream, encoder);
        bw.append(c);
        bw.close();
        byte[] res = stream.toByteArray();
        assertTrue(Arrays.equals(new byte[] { 97, 98, 99 }, res));
    }

    @Test
    public void testByteArrayWithOffset() throws java.io.IOException {
        final int length = BufferChain.BUFFERSIZE * 3 / 2;
        final int offset = 1;
        final byte invalid = 3;
        final byte valid = 2;
        stream.reset();
        byte[] c = new byte[length];
        c[0] = invalid;
        for (int i = offset; i < length; ++i) {
            c[i] = valid;
        }
        ByteWriter bw = new ByteWriter(stream, encoder);
        bw.append(c, offset, length - offset);
        bw.close();
        byte[] res = stream.toByteArray();
        assertEquals(length - offset, res.length);
        assertEquals(valid, res[0]);
    }

    @Test
    public void testStrings() throws java.io.IOException {
        stream.reset();
        String c = "abc\u00F8";
        ByteWriter bw = new ByteWriter(stream, encoder);
        bw.write(c);
        bw.close();
        byte[] res = stream.toByteArray();
        assertTrue(Arrays.equals(new byte[] { 97, 98, 99, (byte) 0xc3, (byte) 0xb8 }, res));
    }

    @Test
    public void testStringsAndByteArrays() throws java.io.IOException {
        stream.reset();
        String c = "abc\u00F8";
        byte[] b = new byte[] { 97, 98, 99 };
        ByteWriter bw = new ByteWriter(stream, encoder);
        bw.write(c);
        bw.append(b);
        bw.close();
        byte[] res = stream.toByteArray();
        assertTrue(Arrays.equals(new byte[] { 97, 98, 99, (byte) 0xc3, (byte) 0xb8, 97, 98, 99 }, res));
    }

    @Test
    public void testByteBuffersAndByteArrays() throws java.io.IOException {
        stream.reset();
        ByteBuffer b = ByteBuffer.allocate(16);
        b.put((byte) 97);
        b.put((byte) 98);
        b.put((byte) 99);
        b.flip();
        byte[] c = new byte[] { 100, 101, 102 };
        ByteWriter bw = new ByteWriter(stream, encoder);
        bw.append(b);
        bw.append(c);
        bw.close();
        byte[] res = stream.toByteArray();
        assertTrue(Arrays.equals(new byte[] { 97, 98, 99, 100, 101, 102 }, res));
    }

    @Test
    public void testOverFlow() throws java.io.IOException {
        stream.reset();
        byte[] b = new byte[] { 97, 98, 99 };
        ByteWriter bw = new ByteWriter(stream, encoder);
        int i = 0;
        while (i < 5000) {
            bw.append(b);
            ++i;
        }
        bw.close();
        byte[] res = stream.toByteArray();
        assertEquals(15000, res.length);
        i = 0;
        int base = 0;
        while (i < 5000) {
            byte[] sub = new byte[3];
            System.arraycopy(res, base, sub, 0, 3);
            assertTrue(Arrays.equals(new byte[] { 97, 98, 99 }, sub));
            base += 3;
            ++i;
        }
    }

    @Test
    public void testUnMappableCharacter() throws java.io.IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ByteWriter writer = new ByteWriter(stream, Charset.forName("ascii").newEncoder());
        writer.write("yahoo\u9999bahoo");
        writer.close();
        assertTrue(stream.toString("ascii").contains("yahoo"));
        assertTrue(stream.toString("ascii").contains("bahoo"));
    }

    @Test
    public void testNoRecycling() throws IOException {
        final int SINGLE_BUFFER = 500;
        final int APPENDS = 500;
        assertTrue(
                "Code has been changed making constants in this test meaningless, review test.",
                BufferChain.BUFFERSIZE * BufferChain.MAXBUFFERS < SINGLE_BUFFER
                        * APPENDS);
        assertTrue(
                "Code has been changed making constants in this test meaningless, review test.",
                BufferChain.WATERMARK > SINGLE_BUFFER);
        byte[] c = new byte[SINGLE_BUFFER];
        Arrays.fill(c, (byte) 'a');
        OnlyUniqueBuffers b = new OnlyUniqueBuffers();
        try {
            for (int i = APPENDS; i > 0; --i) {
                b.insert(ByteBuffer.wrap(c));
            }
            b.flush();
        } catch (IOException e) {
            if (e.getMessage() == OnlyUniqueBuffers.RECYCLED_BYTE_BUFFER) {
                fail(OnlyUniqueBuffers.RECYCLED_BYTE_BUFFER);
            } else {
                throw e;
            }
        }
    }

    @Test
    public void testGetEncoding() throws java.io.IOException {
        stream.reset();
        ByteWriter b = new ByteWriter(stream, encoder);
        assertEquals(Utf8.getCharset(), b.getEncoding());
        b.close();
    }

    @Test
    public void testWriteLong() throws java.io.IOException {
        stream.reset();
        ByteWriter b = new ByteWriter(stream, encoder);
        b.write(1000L * 1000L * 1000L * 1000L);
        b.close();
        assertArrayEquals(Utf8.toBytes("1000000000000"), stream.toByteArray());
    }

    @Test
    public void testWriteInt() throws java.io.IOException {
        stream.reset();
        ByteWriter b = new ByteWriter(stream, encoder);
        b.write((int) 'z');
        b.close();
        assertArrayEquals(Utf8.toBytes("z"), stream.toByteArray());
    }

    @Test
    public void testSurrogatePairs() throws java.io.IOException {
        stream.reset();
        ByteWriter b = new ByteWriter(stream, encoder);
        b.write(0xD800);
        b.write(0xDFD0);
        b.close();
        assertArrayEquals(Utf8.toBytes("\uD800\uDFD0"), stream.toByteArray());
    }

    @Test
    public void testSurrogatePairsMixedWithSingleCharacters() throws java.io.IOException {
        stream.reset();
        ByteWriter b = new ByteWriter(stream, encoder);
        b.write(0x00F8);
        b.write(0xD800);
        b.write(0xDFD0);
        b.write(0x00F8);
        b.write(0xD800);
        b.write((int) 'a');
        b.write(0xDFD0);
        b.write((int) 'b');
        b.close();
        assertArrayEquals(Utf8.toBytes("\u00F8\uD800\uDFD0\u00F8ab"), stream.toByteArray());
    }

    @Test
    public void testWriteDouble() throws java.io.IOException {
        stream.reset();
        ByteWriter b = new ByteWriter(stream, encoder);
        b.write(12.0d);
        b.close();
        assertArrayEquals(Utf8.toBytes("12.0"), stream.toByteArray());
    }

    @Test
    public void testWriteFloat() throws java.io.IOException {
        stream.reset();
        ByteWriter b = new ByteWriter(stream, encoder);
        b.write(12.0f);
        b.close();
        assertArrayEquals(Utf8.toBytes("12.0"), stream.toByteArray());
    }

    @Test
    public void testWriteShort() throws java.io.IOException {
        stream.reset();
        ByteWriter b = new ByteWriter(stream, encoder);
        b.write((short) 12);
        b.close();
        assertArrayEquals(Utf8.toBytes("12"), stream.toByteArray());
    }

    @Test
    public void testWriteBoolean() throws java.io.IOException {
        stream.reset();
        ByteWriter b = new ByteWriter(stream, encoder);
        b.write(true);
        b.close();
        assertArrayEquals(Utf8.toBytes("true"), stream.toByteArray());
    }

    @Test
    public void testAppendSingleByte() throws java.io.IOException {
        stream.reset();
        ByteWriter b = new ByteWriter(stream, encoder);
        b.append((byte) 'a');
        b.close();
        assertArrayEquals(new byte[] { (byte) 'a' }, stream.toByteArray());
    }

    @Test
    public void testAppended() throws IOException {
        stream.reset();
        ByteWriter b = new ByteWriter(stream, encoder);
        final String s = "nalle";
        b.write(s);
        b.close();
        final byte[] bytes = Utf8.toBytes(s);
        assertArrayEquals(bytes, stream.toByteArray());
        assertEquals(bytes.length, b.appended());
    }

    @Test
    public void testWriteUtf8Array() throws IOException {
        stream.reset();
        ByteWriter b = new ByteWriter(stream, encoder);
        final byte[] bytes = Utf8.toBytes("nalle");
        b.write(new Utf8Array(bytes));
        b.close();
        assertArrayEquals(bytes, stream.toByteArray());
    }

    private static class OnlyUniqueBuffers implements WritableByteTransmitter {
        static final String RECYCLED_BYTE_BUFFER = "Got a ByteBuffer instance twice.";
        private final IdentityHashMap<ByteBuffer, ?> buffers = new IdentityHashMap<ByteBuffer, Object>();
        private final BufferChain datastore;

        public OnlyUniqueBuffers() {
            datastore = new BufferChain(this);
        }

        public void insert(ByteBuffer b) throws IOException {
            datastore.append(b);
        }

        @Override
        public void send(ByteBuffer src) throws IOException {
            if (buffers.containsKey(src)) {
                throw new IOException(RECYCLED_BYTE_BUFFER);
            } else {
                buffers.put(src, null);
            }
        }

        public void flush() throws IOException {
            datastore.flush();
        }
    }

}
