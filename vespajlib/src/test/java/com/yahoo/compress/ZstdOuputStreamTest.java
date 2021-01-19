// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.compress;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * @author bjorncs
 */
class ZstdOuputStreamTest {

    @Test
    void output_stream_compresses_input() throws IOException {
        byte[] inputData = "The quick brown fox jumps over the lazy dog".getBytes();
        ByteArrayOutputStream arrayOut = new ByteArrayOutputStream();
        try (ZstdOuputStream zstdOut = new ZstdOuputStream(arrayOut, 12)) {
            zstdOut.write(inputData[0]);
            zstdOut.write(inputData, 1, inputData.length - 1);
        }
        byte[] compressedData = arrayOut.toByteArray();
        ZstdCompressor compressor = new ZstdCompressor();
        byte[] decompressedData = new byte[inputData.length];
        compressor.decompress(compressedData, 0, compressedData.length, decompressedData, 0, decompressedData.length);
        assertArrayEquals(inputData, decompressedData);
    }
}
