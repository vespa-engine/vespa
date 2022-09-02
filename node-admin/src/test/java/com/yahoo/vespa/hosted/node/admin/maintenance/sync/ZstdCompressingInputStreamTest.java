// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.sync;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;

import static com.yahoo.vespa.hosted.node.admin.maintenance.sync.ZstdCompressingInputStream.compressor;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
public class ZstdCompressingInputStreamTest {

    @Test
    void compression_test() {
        Random rnd = new Random();
        byte[] data = new byte[(int) (100_000 * (10 + rnd.nextDouble()))];
        rnd.nextBytes(data);
        assertCompression(data, 1 << 14);
    }

    @Test
    void compress_empty_file_test() {
        byte[] compressedData = compress(new byte[0], 1 << 10);
        assertEquals(13, compressedData.length, "zstd compressing an empty file results in a 13 bytes file");
    }

    private static void assertCompression(byte[] data, int bufferSize) {
        byte[] compressedData = compress(data, bufferSize);
        byte[] decompressedData = new byte[data.length];
        compressor.decompress(compressedData, 0, compressedData.length, decompressedData, 0, decompressedData.length);

        assertArrayEquals(data, decompressedData);
    }

    private static byte[] compress(byte[] data, int bufferSize) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZstdCompressingInputStream zcis = new ZstdCompressingInputStream(bais, bufferSize)) {
            byte[] buffer = new byte[bufferSize];
            for (int nRead; (nRead = zcis.read(buffer, 0, buffer.length)) != -1; )
                baos.write(buffer, 0, nRead);
            baos.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return baos.toByteArray();
    }
}
