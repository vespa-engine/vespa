// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.compress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
class ZstdCompressorTest {

    @Test
    void compresses_and_decompresses_input() {
        byte[] inputData = "The quick brown fox jumps over the lazy dog".getBytes();
        ZstdCompressor compressor = new ZstdCompressor();
        byte[] compressedData = compressor.compress(inputData, 0, inputData.length);
        byte[] decompressedData = compressor.decompress(compressedData, 0, compressedData.length);
        assertArrayEquals(inputData, decompressedData);
    }

    @Test
    void compressed_size_is_less_than_uncompressed() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            builder.append("The quick brown fox jumps over the lazy dog").append('\n');
        }
        byte[] inputData = builder.toString().getBytes();
        ZstdCompressor compressor = new ZstdCompressor();
        byte[] compressedData = compressor.compress(inputData, 0, inputData.length);
        assertTrue(
                compressedData.length < inputData.length,
                () -> "Compressed size is " + compressedData.length + " while uncompressed size is " + inputData.length);
    }

}
