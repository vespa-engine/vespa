package com.yahoo.vespa.hosted.node.admin.maintenance.sync;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;

import static com.yahoo.vespa.hosted.node.admin.maintenance.sync.ZstdCompressingInputStream.compressor;
import static org.junit.Assert.assertArrayEquals;

/**
 * @author freva
 */
public class ZstdCompressingInputStreamTest {

    @Test
    public void compression_test() throws Exception {
        Random rnd = new Random();
        byte[] data = new byte[(int) (100_000 * (10 + rnd.nextDouble()))];
        rnd.nextBytes(data);
        assertCompression(data, 1 << 14);
    }

    private static void assertCompression(byte[] data, int bufferSize) {
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

        byte[] compressedData = baos.toByteArray();
        byte[] decompressedData = new byte[data.length];
        compressor.decompress(compressedData, 0, compressedData.length, decompressedData, 0, decompressedData.length);

        assertArrayEquals(data, decompressedData);
    }
}
