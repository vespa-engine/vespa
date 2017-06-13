// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.vespa.config.util.ConfigUtils;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;

/**
 * Wrapper for LZ4 compression that selects compression level based on properties.
 *
 * @author lulf
 * @since 5.19
 */
public class LZ4PayloadCompressor {
    private static final LZ4Factory lz4Factory = LZ4Factory.safeInstance();
    private static final String VESPA_CONFIG_PROTOCOL_COMPRESSION_LEVEL = "VESPA_CONFIG_PROTOCOL_COMPRESSION_LEVEL";
    private static final int compressionLevel = getCompressionLevel();

    private static int getCompressionLevel() {
        return Integer.parseInt(ConfigUtils.getEnvValue("0",
                System.getenv(VESPA_CONFIG_PROTOCOL_COMPRESSION_LEVEL),
                System.getenv("services__config_protocol_compression_level"),
                System.getProperty(VESPA_CONFIG_PROTOCOL_COMPRESSION_LEVEL)));
    }

    public byte[] compress(byte[] input) {
        return getCompressor().compress(input);
    }

    public void decompress(byte[] input, byte[] outputbuffer) {
        if (input.length > 0) {
            lz4Factory.safeDecompressor().decompress(input, outputbuffer);
        }
    }

    private LZ4Compressor getCompressor() {
        return (compressionLevel < 7) ? lz4Factory.fastCompressor() : lz4Factory.highCompressor();
    }
}
