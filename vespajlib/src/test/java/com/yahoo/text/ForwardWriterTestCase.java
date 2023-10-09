// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.protect.ClassValidator;

/**
 * Check all methods forward correctly and wrap exceptions as documented.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 *
 */
public class ForwardWriterTestCase {
    private static final String WRITE_ABSTRACT_UTF8_ARRAY = "write(AbstractUtf8Array)";
    private static final String WRITE_BOOLEAN = "write(boolean)";
    private static final String WRITE_CHAR = "write(char)";
    private static final String WRITE_DOUBLE = "write(double)";
    private static final String WRITE_FLOAT = "write(float)";
    private static final String WRITE_CHAR_SEQUENCE = "write(CharSequence)";
    private static final String WRITE_CHAR_INT_INT = "write(char[], int, int)";
    private static final String FLUSH = "flush()";
    private static final String CLOSE = "close()";
    private static final String WRITE_STRING = "write(String)";
    private static final String WRITE_LONG = "write(long)";
    private static final String WRITE_SHORT = "write(short)";
    private static final String WRITE_BYTE = "write(byte)";

    private static class Boom extends GenericWriter {
        @Override
        public GenericWriter write(final char c) throws IOException {
            method(WRITE_CHAR);
            final GenericWriter w = super.write(c);
            explode();
            return w;
        }

        @Override
        public GenericWriter write(final CharSequence s) throws IOException {
            method(WRITE_CHAR_SEQUENCE);
            final GenericWriter w = super.write(s);
            explode();
            return w;
        }

        @Override
        public void write(final String s) throws IOException {
            method(WRITE_STRING);
            super.write(s);
            explode();
        }

        @Override
        public GenericWriter write(final long i) throws IOException {
            method(WRITE_LONG);
            final GenericWriter w = super.write(i);
            explode();
            return w;
        }

        @Override
        public void write(final int i) throws IOException {
            method("write(int)");
            super.write(i);
            explode();
        }

        @Override
        public GenericWriter write(final short i) throws IOException {
            method(WRITE_SHORT);
            final GenericWriter w = super.write(i);
            explode();
            return w;
        }

        @Override
        public GenericWriter write(final byte i) throws IOException {
            method(WRITE_BYTE);
            final GenericWriter w = super.write(i);
            explode();
            return w;
        }

        @Override
        public GenericWriter write(final double i) throws IOException {
            method(WRITE_DOUBLE);
            final GenericWriter w = super.write(i);
            explode();
            return w;
        }

        @Override
        public GenericWriter write(final float i) throws IOException {
            method(WRITE_FLOAT);
            final GenericWriter w = super.write(i);
            explode();
            return w;
        }

        @Override
        public GenericWriter write(final boolean i) throws IOException {
            method(WRITE_BOOLEAN);
            final GenericWriter w = super.write(i);
            explode();
            return w;
        }

        @Override
        public GenericWriter write(final AbstractUtf8Array v)
                throws IOException {
            method(WRITE_ABSTRACT_UTF8_ARRAY);
            final GenericWriter w = super.write(v);
            explode();
            return w;
        }

        StringBuilder last = new StringBuilder();
        private boolean explode = false;
        private boolean toplevel;
        private String method;

        @Override
        public void write(final char[] cbuf, final int off, final int len)
                throws IOException {
            method(WRITE_CHAR_INT_INT);
            last.append(cbuf, off, len);
            explode();
        }

        @Override
        public void flush() throws IOException {
            method(FLUSH);
            explode();

        }

        @Override
        public void close() throws IOException {
            method(CLOSE);
            explode();
        }

        private void method(final String method) {
            if (toplevel) {
                this.method = method;
                toplevel = false;
            }
        }

        private void explode() throws IOException {
            if (explode) {
                throw new IOException(method);
            }
        }

        void arm() {
            explode = true;
            toplevel = true;
        }
    }

    private Boom wrapped;
    private ForwardWriter forward;
    private boolean gotException;

    @Before
    public void setUp() throws Exception {
        wrapped = new Boom();
        forward = new ForwardWriter(wrapped);
        gotException = false;
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void requireForwardWriterIsComplete() {
        final List<Method> methods = ClassValidator
                .unmaskedMethodsFromSuperclass(ForwardWriter.class);
        assertEquals(0, methods.size());
    }

    @Test
    public final void testWriteInt() {
        forward.write(0x1ECD);
        assertEquals("\u1ECD", wrapped.last.toString());
        wrapped.arm();
        try {
            forward.write(0);
        } catch (final RuntimeException e) {
            assertEquals("write(int)", e.getCause().getMessage());
            gotException = true;
        }
        assertTrue(gotException);
    }

    @Test
    public final void testWriteCharArrayIntInt() {
        writeCharArrayIntInt();
        assertEquals("0", wrapped.last.toString());
        wrapped.arm();
        try {
            writeCharArrayIntInt();
        } catch (final RuntimeException e) {
            assertEquals(WRITE_CHAR_INT_INT, e.getCause().getMessage());
            gotException = true;
        }
        assertTrue(gotException);
    }

    public void writeCharArrayIntInt() {
        forward.write(new char[] { '0' }, 0, 1);
    }

    @Test
    public final void testFlush() {
        wrapped.arm();
        try {
            forward.flush();
        } catch (final RuntimeException e) {
            assertEquals(FLUSH, e.getCause().getMessage());
            gotException = true;
        }
        assertTrue(gotException);
    }

    @Test
    public final void testClose() {
        wrapped.arm();
        try {
            forward.close();
        } catch (final RuntimeException e) {
            assertEquals(CLOSE, e.getCause().getMessage());
            gotException = true;
        }
        assertTrue(gotException);
    }

    @Test
    public final void testWriteString() {
        writeString();
        assertEquals("0", wrapped.last.toString());
        wrapped.arm();
        try {
            writeString();
        } catch (final RuntimeException e) {
            assertEquals(WRITE_STRING, e.getCause().getMessage());
            gotException = true;
        }
        assertTrue(gotException);
    }

    public void writeString() {
        forward.write("0");
    }

    @Test
    public final void testWriteCharSequence() {
        writeCharSequence();
        assertEquals("0", wrapped.last.toString());
        wrapped.arm();
        try {
            writeCharSequence();
        } catch (final RuntimeException e) {
            assertEquals(WRITE_CHAR_SEQUENCE, e.getCause().getMessage());
            gotException = true;
        }
        assertTrue(gotException);
    }

    public void writeCharSequence() {
        forward.write((CharSequence) "0");
    }

    @Test
    public final void testWriteLong() {
        writeLong();
        assertEquals("0", wrapped.last.toString());
        wrapped.arm();
        try {
            writeLong();
        } catch (final RuntimeException e) {
            assertEquals(WRITE_LONG, e.getCause().getMessage());
            gotException = true;
        }
        assertTrue(gotException);
    }

    public void writeLong() {
        forward.write((long) 0);
    }

    @Test
    public final void testWriteFloat() {
        writeFloat();
        assertEquals("0.0", wrapped.last.toString());
        wrapped.arm();
        try {
            writeFloat();
        } catch (final RuntimeException e) {
            assertEquals(WRITE_FLOAT, e.getCause().getMessage());
            gotException = true;
        }
        assertTrue(gotException);
    }

    public void writeFloat() {
        forward.write(0.0f);
    }

    @Test
    public final void testWriteDouble() {
        writeDouble();
        assertEquals("0.0", wrapped.last.toString());
        wrapped.arm();
        try {
            writeDouble();
        } catch (final RuntimeException e) {
            assertEquals(WRITE_DOUBLE, e.getCause().getMessage());
            gotException = true;
        }
        assertTrue(gotException);
    }

    public void writeDouble() {
        forward.write(0.0d);
    }

    @Test
    public final void testWriteShort() {
        writeShort();
        assertEquals("0", wrapped.last.toString());
        wrapped.arm();
        try {
            writeShort();
        } catch (final RuntimeException e) {
            assertEquals(WRITE_SHORT, e.getCause().getMessage());
            gotException = true;
        }
        assertTrue(gotException);
    }

    public void writeShort() {
        forward.write((short) 0);
    }

    @Test
    public final void testWriteChar() {
        writeChar();
        assertEquals("0", wrapped.last.toString());
        wrapped.arm();
        try {
            writeChar();
        } catch (final RuntimeException e) {
            assertEquals(WRITE_CHAR, e.getCause().getMessage());
            gotException = true;
        }
        assertTrue(gotException);
    }

    public void writeChar() {
        forward.write('0');
    }

    @Test
    public final void testWriteByte() {
        writeByte();
        assertEquals("0", wrapped.last.toString());
        wrapped.arm();
        try {
            writeByte();
        } catch (final RuntimeException e) {
            assertEquals(WRITE_BYTE, e.getCause().getMessage());
            gotException = true;
        }
        assertTrue(gotException);
    }

    public void writeByte() {
        forward.write((byte) 0);
    }

    @Test
    public final void testWriteBoolean() {
        writeBoolean();
        assertEquals("true", wrapped.last.toString());
        wrapped.arm();
        try {
            writeBoolean();
        } catch (final RuntimeException e) {
            assertEquals(WRITE_BOOLEAN, e.getCause().getMessage());
            gotException = true;
        }
        assertTrue(gotException);
    }

    public void writeBoolean() {
        forward.write(true);
    }

    @Test
    public final void testWriteAbstractUtf8Array() {
        writeUtf8Array();
        assertEquals("0", wrapped.last.toString());
        wrapped.arm();
        try {
            writeUtf8Array();
        } catch (final RuntimeException e) {
            assertEquals(WRITE_ABSTRACT_UTF8_ARRAY, e.getCause().getMessage());
            gotException = true;
        }
        assertTrue(gotException);

    }

    public void writeUtf8Array() {
        forward.write(new Utf8Array(Utf8.toBytes("0")));
    }

    @Test
    public final void testGetWriter() {
        assertSame(wrapped, forward.getWriter());
    }
}
