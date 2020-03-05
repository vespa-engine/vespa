// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;
import com.yahoo.vespa.config.util.ConfigUtils;

/**
 * Wrapper for LZ4 compression that selects compression level based on properties.
 *
 * @author Ulf Lilleengen
 */
public class LZ4PayloadCompressor {

    private static final String VESPA_CONFIG_PROTOCOL_COMPRESSION_LEVEL = "VESPA_CONFIG_PROTOCOL_COMPRESSION_LEVEL";
    private static final Compressor compressor = new Compressor(CompressionType.LZ4, getCompressionLevel());

    private static int getCompressionLevel() {
        return Integer.parseInt(ConfigUtils.getEnvValue("0",
                System.getenv(VESPA_CONFIG_PROTOCOL_COMPRESSION_LEVEL),
                System.getenv("services__config_protocol_compression_level"),
                System.getProperty(VESPA_CONFIG_PROTOCOL_COMPRESSION_LEVEL)));
    }

    public byte[] compress(byte[] input) {
        return compressor.compressUnconditionally(input);
    }

    public void decompress(byte[] input, byte[] output) {
        compressor.decompressUnconditionally(input, output);
    }

}
