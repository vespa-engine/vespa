// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.compress;

import java.util.Arrays;

/**
 * Frame based Zstd compressor (https://github.com/facebook/zstd)
 * Implemented based on https://github.com/airlift/aircompressor - a pure Java implementation (no JNI).
 *
 * @author bjorncs
 */
public class ZstdCompressor {

    private static final io.airlift.compress.zstd.ZstdCompressor compressor = new io.airlift.compress.zstd.ZstdCompressor();
    private static final io.airlift.compress.zstd.ZstdDecompressor decompressor = new io.airlift.compress.zstd.ZstdDecompressor();

    public byte[] compress(byte[] input, int inputOffset, int inputLength) {
        int maxCompressedLength = compressor.maxCompressedLength(inputLength);
        byte[] output = new byte[maxCompressedLength];
        int compressedLength = compressor.compress(input, inputOffset, inputLength, output, 0, maxCompressedLength);
        return Arrays.copyOf(output, compressedLength);
    }

    public byte[] decompress(byte[] input, int inputOffset, int inputLength) {
        int decompressedLength = (int) io.airlift.compress.zstd.ZstdDecompressor.getDecompressedSize(input, inputOffset, inputLength);
        byte[] output = new byte[decompressedLength];
        decompressor.decompress(input, inputOffset, inputLength, output, 0, decompressedLength);
        return output;
    }
}
