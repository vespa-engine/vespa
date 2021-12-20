// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.text.Utf8;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/**
 * @author Ulf Lilleengen
 */
public class LZ4PayloadCompressorTest {

    @Test
    public void testCompression() {
        assertCompression("hei hallo der");
        assertCompression("");
        assertCompression("{}");
    }

    private void assertCompression(String input) {
        LZ4PayloadCompressor compressor = new LZ4PayloadCompressor();
        byte[] data = Utf8.toBytes(input);
        byte[] compressed = compressor.compress(data);
        byte[] output = compressor.decompress(compressed, data.length);
        assertArrayEquals(output, data);
    }
}
