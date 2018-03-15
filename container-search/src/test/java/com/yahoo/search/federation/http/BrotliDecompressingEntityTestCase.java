// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.message.BasicHeader;
import org.brotli.dec.BrotliInputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.text.Utf8;

/**
 * Test Brotli support for the HTTP integration introduced in 4.2.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class BrotliDecompressingEntityTestCase {
    private static final String STREAM_CONTENT = "00000000000000000000000000000000000000000000000000";
    private static final byte[] CONTENT_AS_BYTES = Utf8.toBytes(STREAM_CONTENT);
    BrotliDecompressingEntity testEntity;

    private static final class MockEntity implements HttpEntity {

        private final InputStream inStream;

        MockEntity(InputStream is) {
            inStream = is;
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public boolean isChunked() {
            return false;
        }

        @Override
        public long getContentLength() {
            return -1;
        }

        @Override
        public Header getContentType() {
            return new BasicHeader("Content-Type", "text/plain");
        }

        @Override
        public Header getContentEncoding() {
            return new BasicHeader("Content-Encoding", "br");
        }

        @Override
        public InputStream getContent() throws IOException,
                IllegalStateException {
            return inStream;
        }

        @Override
        public void writeTo(OutputStream outstream) throws IOException {
        }

        @Override
        public boolean isStreaming() {
            return false;
        }

        @Override
        public void consumeContent() throws IOException {
        }
    }

    @Before
    public void setUp() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(out);
        gzip.write(CONTENT_AS_BYTES);
        gzip.finish();
        gzip.close();
        byte[] compressed = out.toByteArray();
        InputStream inStream = new ByteArrayInputStream(compressed);
        testEntity = new BrotliDecompressingEntity(new MockEntity(inStream));
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public final void testGetContentLength() throws UnknownHostException {
        assertEquals(STREAM_CONTENT.length(), testEntity.getContentLength());
    }

    @Test
    public final void testGetContent() throws IllegalStateException, IOException {
        InputStream in = testEntity.getContent();
        byte[] buffer = new byte[CONTENT_AS_BYTES.length];
        int read = in.read(buffer);
        assertEquals(CONTENT_AS_BYTES.length, read);
        assertArrayEquals(CONTENT_AS_BYTES, buffer);
    }

    @Test
    public final void testGetContentToBigArray() throws IllegalStateException, IOException {
        InputStream in = testEntity.getContent();
        byte[] buffer = new byte[CONTENT_AS_BYTES.length * 2];
        in.read(buffer);
        byte[] expected = Arrays.copyOf(CONTENT_AS_BYTES, CONTENT_AS_BYTES.length * 2);
        assertArrayEquals(expected, buffer);
    }

    @Test
    public final void testGetContentAvailable() throws IllegalStateException, IOException {
        InputStream in = testEntity.getContent();
        assertEquals(CONTENT_AS_BYTES.length, in.available());
    }

    @Test
    public final void testLargeZip() throws IOException {
        byte [] input = new byte [10000000];
        Random random = new Random(89);
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) random.nextInt();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(out);
        gzip.write(input);
        gzip.finish();
        gzip.close();
        byte[] compressed = out.toByteArray();
        assertEquals(10003073, compressed.length);
        InputStream inStream = new ByteArrayInputStream(compressed);
        BrotliDecompressingEntity gunzipper = new BrotliDecompressingEntity(new MockEntity(inStream));
        assertEquals(input.length, gunzipper.getContentLength());
        byte[] buffer = new byte[input.length];
        InputStream content = gunzipper.getContent();
        assertEquals(input.length, content.available());
        int read = content.read(buffer);
        assertEquals(input.length, read);
        assertArrayEquals(input, buffer);
    }

    @Test
    public final void testGetContentReadByte() throws IllegalStateException, IOException {
        InputStream in = testEntity.getContent();
        byte[] buffer = new byte[CONTENT_AS_BYTES.length * 2];
        int i = 0;
        while (i < buffer.length) {
            int r = in.read();
            if (r == -1) {
                break;
            } else {
                buffer[i++] = (byte) r;
            }
        }
        byte[] expected = Arrays.copyOf(CONTENT_AS_BYTES, CONTENT_AS_BYTES.length * 2);
        assertEquals(CONTENT_AS_BYTES.length, i);
        assertArrayEquals(expected, buffer);
    }

    @Test
    public final void testGetContentReadWithOffset() throws IllegalStateException, IOException {
        InputStream in = testEntity.getContent();
        byte[] buffer = new byte[CONTENT_AS_BYTES.length * 2];
        int read = in.read(buffer, CONTENT_AS_BYTES.length, CONTENT_AS_BYTES.length);
        assertEquals(CONTENT_AS_BYTES.length, read);
        byte[] expected = new byte[CONTENT_AS_BYTES.length * 2];
        for (int i = 0; i < CONTENT_AS_BYTES.length; ++i) {
            expected[CONTENT_AS_BYTES.length + i] = CONTENT_AS_BYTES[i];
        }
        assertArrayEquals(expected, buffer);
        read = in.read(buffer, 0, CONTENT_AS_BYTES.length);
        assertEquals(-1, read);
    }

    @Test
    public final void testGetContentSkip() throws IllegalStateException, IOException {
        InputStream in = testEntity.getContent();
        final long n = 5L;
        long skipped = in.skip(n);
        assertEquals(n, skipped);
        int read = in.read();
        assertEquals(CONTENT_AS_BYTES[(int) n], read);
        skipped = in.skip(5000);
        assertEquals(CONTENT_AS_BYTES.length - n - 1, skipped);
        assertEquals(-1L, in.skip(1L));
    }


    @Test
    public final void testWriteToOutputStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        testEntity.writeTo(out);
        assertArrayEquals(CONTENT_AS_BYTES, out.toByteArray());
    }

}
