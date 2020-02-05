// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;

/**
 * To run this test, place a payload in src/test/ca.json. The file is not checked in because it is huge.
 *
 * @author Ulf Lilleengen
 */
public class LZ4CompressionTest {
    private static LZ4Factory factory = LZ4Factory.safeInstance();

    @Test
    @Ignore
    public void testCompression() throws IOException {
        byte[] data = getInput();
        System.out.println("High compressor");
        for (int i = 0; i < 10; i++) {
            timeCompressor(factory.highCompressor(), data);
        }
        System.out.println("Fast compressor");
        for (int i = 0; i < 10; i++) {
            timeCompressor(factory.fastCompressor(), data);
        }
    }

    private byte[] getInput() throws IOException {
        byte[] data = Files.readAllBytes(FileSystems.getDefault().getPath("src/test/ca.json"));
        System.out.println("Input size: " + data.length);
        return data;
    }

    private void timeCompressor(LZ4Compressor lz4Compressor, byte[] data) {
        long start = System.currentTimeMillis();
        byte[] compressed = lz4Compressor.compress(data);
        long end = System.currentTimeMillis();
        System.out.println("Compression took " + (end - start) + " millis, and size of data is " + compressed.length + " bytes");
    }

    @Test
    @Ignore
    public void testDecompression() throws IOException {
        byte[] data = getInput();
        byte[] outputbuffer = new byte[data.length];
        byte[] hcCompressedData = factory.highCompressor().compress(data);
        System.out.println("High compressor");
        for (int i = 0; i < 10; i++) {
            timeDecompressor(hcCompressedData, factory.safeDecompressor(), outputbuffer);
        }
        byte[] fastCompressedData = factory.fastCompressor().compress(data);
        System.out.println("Fast compressor");
        for (int i = 0; i < 10; i++) {
            timeDecompressor(fastCompressedData, factory.safeDecompressor(), outputbuffer);
        }
    }

    private void timeDecompressor(byte[] compressedData, LZ4SafeDecompressor decompressor, byte[] outputbuffer) {
        long start = System.currentTimeMillis();
        decompressor.decompress(compressedData, outputbuffer);
        long end = System.currentTimeMillis();
        System.out.println("Decompression took " + (end - start) + " millis");
    }

}
