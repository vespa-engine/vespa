// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.compress;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void compressed_size_is_less_than_uncompressed() throws IOException {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            builder.append("The quick brown fox jumps over the lazy dog").append('\n');
        }
        byte[] inputData = builder.toString().getBytes();
        ByteArrayOutputStream arrayOut = new ByteArrayOutputStream();
        try (ZstdOuputStream zstdOut = new ZstdOuputStream(arrayOut)) {
            zstdOut.write(inputData);
        }
        int compressedSize = arrayOut.toByteArray().length;
        assertTrue(
                compressedSize < inputData.length,
                () -> "Compressed size is " + compressedSize + " while uncompressed size is " + inputData.length);
    }
}
