// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.text.Utf8;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

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
        byte[] output = new byte[data.length];
        compressor.decompress(compressed, output);
        assertThat(data, is(output));
    }
}
