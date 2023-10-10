// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

/**
 * Completeness check for GenericWriter.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class GenericWriterTestCase {
    private static class MockWriter extends GenericWriter {
        private StringBuilder written = new StringBuilder();

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            written.append(String.copyValueOf(cbuf, off, len));
        }

        @Override
        public void flush() throws IOException {
        }

        @Override
        public void close() throws IOException {
        }
    }

    MockWriter mock;

    @Before
    public void setUp() throws Exception {
        mock = new MockWriter();
    }

    @Test
    public final void testWriteInt() throws Exception {
        mock.write(0xa);
        assertEquals("\n", mock.written.toString());
    }

    @Test
    public final void testWriteChar() throws IOException {
        mock.write('\u0020');
        assertEquals(" ", mock.written.toString());
    }

    @Test
    public final void testWriteCharSequence() throws IOException {
        mock.write((CharSequence) "abc");
        assertEquals("abc", mock.written.toString());
    }

    @Test
    public final void testWriteString() throws IOException {
        mock.write("abc");
        assertEquals("abc", mock.written.toString());
    }

    @Test
    public final void testWriteLong() throws IOException {
        mock.write(42L);
        assertEquals("42", mock.written.toString());
    }

    @Test
    public final void testWriteShort() throws IOException {
        mock.write((short) 42);
        assertEquals("42", mock.written.toString());
    }

    @Test
    public final void testWriteByte() throws IOException {
        mock.write((byte) 42);
        assertEquals("42", mock.written.toString());
    }

    @Test
    public final void testWriteDouble() throws IOException {
        mock.write(0.0d);
        assertEquals("0.0", mock.written.toString());
    }

    @Test
    public final void testWriteFloat() throws IOException {
        mock.write(0.0f);
        assertEquals("0.0", mock.written.toString());
    }

    @Test
    public final void testWriteBoolean() throws IOException {
        mock.write(true);
        assertEquals("true", mock.written.toString());
    }

    @Test
    public final void testWriteAbstractUtf8Array() throws IOException {
        mock.write(new Utf8Array(Utf8.toBytes("abc")));
        assertEquals("abc", mock.written.toString());
    }

}
