// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server.util;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.23
 */
public class ByteLimitedInputStreamTestCase {

    private static ByteLimitedInputStream create(byte[] source, int limit) {
        if (limit > source.length) {
            throw new IllegalArgumentException("Limit is greater than length of source buffer.");
        }
        InputStream wrappedStream = new ByteArrayInputStream(source);
        return new ByteLimitedInputStream(wrappedStream, limit);
    }

    @Test
    public void requireThatBasicsWork() throws IOException {
        ByteLimitedInputStream stream = create("abcdefghijklmnopqr".getBytes(StandardCharsets.US_ASCII), 9);

        assertEquals(9, stream.available());
        assertEquals(97, stream.read());
        assertEquals(8, stream.available());
        assertEquals(98, stream.read());
        assertEquals(7, stream.available());
        assertEquals(99, stream.read());
        assertEquals(6, stream.available());
        assertEquals(100, stream.read());
        assertEquals(5, stream.available());
        assertEquals(101, stream.read());
        assertEquals(4, stream.available());
        assertEquals(102, stream.read());
        assertEquals(3, stream.available());
        assertEquals(103, stream.read());
        assertEquals(2, stream.available());
        assertEquals(104, stream.read());
        assertEquals(1, stream.available());
        assertEquals(105, stream.read());
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read());
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read());
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read());
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read());
        assertEquals(0, stream.available());
        assertEquals(-1, stream.read());
        assertEquals(0, stream.available());
    }

    @Test
    public void requireThatChunkedReadWorks() throws IOException {
        ByteLimitedInputStream stream = create("abcdefghijklmnopqr".getBytes(StandardCharsets.US_ASCII), 9);

        assertEquals(9, stream.available());
        byte[] toBuf = new byte[4];
        assertEquals(4, stream.read(toBuf));
        assertEquals(97, toBuf[0]);
        assertEquals(98, toBuf[1]);
        assertEquals(99, toBuf[2]);
        assertEquals(100, toBuf[3]);
        assertEquals(5, stream.available());

        assertEquals(4, stream.read(toBuf));
        assertEquals(101, toBuf[0]);
        assertEquals(102, toBuf[1]);
        assertEquals(103, toBuf[2]);
        assertEquals(104, toBuf[3]);
        assertEquals(1, stream.available());

        assertEquals(1, stream.read(toBuf));
        assertEquals(105, toBuf[0]);
        assertEquals(0, stream.available());

        assertEquals(-1, stream.read(toBuf));
        assertEquals(0, stream.available());
    }

    @Test
    public void requireMarkWorks() throws IOException {
        InputStream stream = create("abcdefghijklmnopqr".getBytes(StandardCharsets.US_ASCII), 9);
        assertEquals(97, stream.read());
        assertTrue(stream.markSupported());
        stream.mark(5);
        assertEquals(98, stream.read());
        assertEquals(99, stream.read());
        stream.reset();
        assertEquals(98, stream.read());
        assertEquals(99, stream.read());
        assertEquals(100, stream.read());
        assertEquals(101, stream.read());
    }

}
