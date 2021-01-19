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
        int maxCompressedLength = getMaxCompressedLength(inputLength);
        byte[] output = new byte[maxCompressedLength];
        int compressedLength = compress(input, inputOffset, inputLength, output, 0, maxCompressedLength);
        return Arrays.copyOf(output, compressedLength);
    }

    public int compress(byte[] input, int inputOffset, int inputLength, byte[] output, int outputOffset, int maxOutputLength) {
        return compressor.compress(input, inputOffset, inputLength, output, outputOffset, maxOutputLength);
    }

    /**
     *   Note:
     *   Implementation assumes single frame (since {@link #getDecompressedLength(byte[], int, int)} only includes the first frame)
     *   The {@link #decompress(byte[], int, int, byte[], int, int)} overload will try to decompress all frame, causing the output buffer to overflow.
     */
    public byte[] decompress(byte[] input, int inputOffset, int inputLength) {
        int decompressedLength = getDecompressedLength(input, inputOffset, inputLength);
        byte[] output = new byte[decompressedLength];
        decompress(input, inputOffset, inputLength, output, 0, decompressedLength);
        return output;
    }

    public int decompress(byte[] input, int inputOffset, int inputLength, byte[] output, int outputOffset, int maxOutputLength) {
        return decompressor.decompress(input, inputOffset, inputLength, output, outputOffset, maxOutputLength);
    }

    public static int getMaxCompressedLength(int uncompressedLength) {
        return compressor.maxCompressedLength(uncompressedLength);
    }

    public static int getDecompressedLength(byte[] input, int inputOffset, int inputLength) {
        return (int) io.airlift.compress.zstd.ZstdDecompressor.getDecompressedSize(input, inputOffset, inputLength);
    }
}
