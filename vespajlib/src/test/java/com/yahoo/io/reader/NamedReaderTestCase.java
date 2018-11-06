// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io.reader;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.CharBuffer;
import java.util.Collections;
import java.util.List;

import com.yahoo.protect.ClassValidator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests all method of NamedReader.
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
public class NamedReaderTestCase {

    @Test
    public void testIt() {
        StringReader stringReader=new StringReader("hello world");
        NamedReader r=new NamedReader("test1",stringReader);
        assertEquals("test1",r.getName());
        assertEquals("test1",r.toString());
        assertEquals(stringReader,r.getReader());
        NamedReader.closeAll(Collections.singletonList(r));
        NamedReader.closeAll(null); // noop, nor exception
    }

    @Test
    public void testMethodMasking() {
        List<Method> unmaskedMethods = ClassValidator.unmaskedMethodsFromSuperclass(NamedReader.class);
        if (! unmaskedMethods.isEmpty()) {
            fail("Unmasked methods in " + NamedReader.class.getName() + ": " + unmaskedMethods);
        }
    }

    private static class MarkerReader extends Reader {
        static final String READ_CHAR_BUFFER = "com.yahoo.io.reader.NamedReaderTestCase.MarkerReader.read(CharBuffer)";
        static final String READ = "com.yahoo.io.reader.NamedReaderTestCase.MarkerReader.read()";
        static final String READ_CHAR = "com.yahoo.io.reader.NamedReaderTestCase.MarkerReader.read(char[])";
        static final String READ_CHAR_INT_INT = "com.yahoo.io.reader.NamedReaderTestCase.MarkerReader.read(char[], int, int)";
        static final String SKIP_LONG = "com.yahoo.io.reader.NamedReaderTestCase.MarkerReader.skip(long)";
        static final String READY = "com.yahoo.io.reader.NamedReaderTestCase.MarkerReader.ready()";
        static final String MARK_SUPPORTED = "com.yahoo.io.reader.NamedReaderTestCase.MarkerReader.markSupported()";
        static final String MARK_INT = "com.yahoo.io.reader.NamedReaderTestCase.MarkerReader.mark(int)";
        static final String RESET = "com.yahoo.io.reader.NamedReaderTestCase.MarkerReader.reset()";
        static final String CLOSE = "com.yahoo.io.reader.NamedReaderTestCase.MarkerReader.close()";
        String lastMethodHit = null;

        @Override
        public int read(CharBuffer target) throws IOException {
            lastMethodHit = READ_CHAR_BUFFER;
            return 0;
        }

        @Override
        public int read() throws IOException {
            lastMethodHit = READ;
            return -1;
        }

        @Override
        public int read(char[] cbuf) throws IOException {
            lastMethodHit = READ_CHAR;
            return 0;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            lastMethodHit = READ_CHAR_INT_INT;
            return 0;
        }

        @Override
        public long skip(long n) throws IOException {
            lastMethodHit = SKIP_LONG;
            return 0;
        }

        @Override
        public boolean ready() throws IOException {
            lastMethodHit = READY;
            return false;
        }

        @Override
        public boolean markSupported() {
            lastMethodHit = MARK_SUPPORTED;
            return false;
        }

        @Override
        public void mark(int readAheadLimit) throws IOException {
            lastMethodHit = MARK_INT;
        }

        @Override
        public void reset() throws IOException {
            lastMethodHit = RESET;
        }

        @Override
        public void close() throws IOException {
            lastMethodHit = CLOSE;
        }
    }

    @Test
    public void testAllDelegators() throws IOException {
        MarkerReader m = new MarkerReader();
        NamedReader r = new NamedReader("nalle", m);
        r.read(CharBuffer.allocate(5000));
        assertEquals(MarkerReader.READ_CHAR_BUFFER, m.lastMethodHit);
        r.read();
        assertEquals(MarkerReader.READ, m.lastMethodHit);
        r.read(new char[5]);
        assertEquals(MarkerReader.READ_CHAR, m.lastMethodHit);
        r.read(new char[5], 0, 5);
        assertEquals(MarkerReader.READ_CHAR_INT_INT, m.lastMethodHit);
        r.skip(5L);
        assertEquals(MarkerReader.SKIP_LONG, m.lastMethodHit);
        r.ready();
        assertEquals(MarkerReader.READY, m.lastMethodHit);
        r.markSupported();
        assertEquals(MarkerReader.MARK_SUPPORTED, m.lastMethodHit);
        r.mark(5);
        assertEquals(MarkerReader.MARK_INT, m.lastMethodHit);
        r.reset();
        assertEquals(MarkerReader.RESET, m.lastMethodHit);
        r.close();
        assertEquals(MarkerReader.CLOSE, m.lastMethodHit);
    }

}
