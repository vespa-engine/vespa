// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.compress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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

}