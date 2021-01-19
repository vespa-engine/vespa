// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.compress;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
class CompressorTest {

    @Test
    void compresses_and_decompresses_input_using_zstd() {
        byte[] inputData = "The quick brown fox jumps over the lazy dog".getBytes();
        Compressor compressor = new Compressor(CompressionType.ZSTD);
        Compressor.Compression compression = compressor.compress(CompressionType.ZSTD, inputData, Optional.empty());
        assertEquals(inputData.length, compression.uncompressedSize());
        byte[] compressedData = compression.data();
        byte[] decompressedData = compressor.decompress(CompressionType.ZSTD, compressedData, 0, inputData.length, Optional.of(compressedData.length));
        assertArrayEquals(inputData, decompressedData);
    }

}
