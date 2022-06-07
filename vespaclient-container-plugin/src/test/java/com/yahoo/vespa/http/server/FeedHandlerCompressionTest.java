// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.container.jdisc.HttpRequest;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FeedHandlerCompressionTest {

    public static byte[] compress(final String dataToBrCompressed) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(dataToBrCompressed.length());
        final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
        gzipOutputStream.write(dataToBrCompressed.getBytes());
        gzipOutputStream.close();
        byte[] compressedBytes = byteArrayOutputStream.toByteArray();
        byteArrayOutputStream.close();
        return compressedBytes;
    }

    @Test
    public void testUnzipStreamIfNeeded() throws Exception {
        final String testData = "foo bar";
        InputStream inputStream = new ByteArrayInputStream(compress(testData));
        HttpRequest httpRequest = mock(HttpRequest.class);
        when(httpRequest.getHeader("content-encoding")).thenReturn("gzip");
        InputStream decompressedStream = FeedHandler.unzipStreamIfNeeded(inputStream, httpRequest);
        final StringBuilder processedInput = new StringBuilder();
        while (true) {
            int readValue = decompressedStream.read();
            if (readValue < 0) {
                break;
            }
            processedInput.append((char)readValue);
        }
        assertEquals(processedInput.toString(), testData);
    }

    /**
     * Test by setting encoding, but not compressing data.
     * @throws Exception
     */
    @Test
    public void testUnzipFails() throws Exception {
        final String testData = "foo bar";
        InputStream inputStream = new ByteArrayInputStream(testData.getBytes());
        HttpRequest httpRequest = mock(HttpRequest.class);
        when(httpRequest.getHeader("Content-Encoding")).thenReturn("gzip");
        InputStream decompressedStream = FeedHandler.unzipStreamIfNeeded(inputStream, httpRequest);
        final StringBuilder processedInput = new StringBuilder();
        while (true) {
            int readValue = decompressedStream.read();
            if (readValue < 0) {
                break;
            }
            processedInput.append((char)readValue);
        }
        assertEquals(processedInput.toString(), testData);
    }

}
