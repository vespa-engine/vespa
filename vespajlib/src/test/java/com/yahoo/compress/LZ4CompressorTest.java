// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.compress;

import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LZ4CompressorTest {

    @Test
    public void can_compress_and_decompress_partial_buffer_range() {
        byte[] toCompress = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes();
        int compressBytes = 30;
        Compressor compressor = new Compressor();
        Compressor.Compression compressed = compressor.compress(CompressionType.LZ4, toCompress, Optional.of(compressBytes));
        assertEquals(compressBytes, compressed.uncompressedSize());
        byte[] decompressed = compressor.decompress(compressed);
        assertTrue(Arrays.equals(decompressed, Arrays.copyOf(toCompress, compressBytes)));
    }

}
