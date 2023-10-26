// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;

import java.nio.ByteBuffer;

/**
 * Wrapper for LZ4 compression that selects compression level based on properties.
 *
 * @author Ulf Lilleengen
 */
public class LZ4PayloadCompressor {

    private static final Compressor compressor = new Compressor(CompressionType.LZ4, 0);

    public byte[] compress(byte[] input) {
        return compressor.compressUnconditionally(input);
    }

    public byte[] compress(ByteBuffer input) {
        return compressor.compressUnconditionally(input);
    }

    public byte [] decompress(byte[] input, int uncompressedLen) {
        return compressor.decompressUnconditionally(input, 0, uncompressedLen);
    }

    public byte [] decompress(ByteBuffer input, int uncompressedLen) {
        ByteBuffer uncompressed = ByteBuffer.allocate(uncompressedLen);
        compressor.decompressUnconditionally(input, uncompressed);
        return uncompressed.array();
    }

}
