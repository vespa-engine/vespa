// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.text.AbstractUtf8Array;
import com.yahoo.text.Utf8Array;
import com.yahoo.vespa.config.LZ4PayloadCompressor;
import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.vespa.config.protocol.CompressionInfo;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.SlimeConfigResponse;

/**
 * Compressor that compresses config payloads to lz4.
 *
 * @author Ulf Lilleengen
 */
public class LZ4ConfigResponseFactory implements ConfigResponseFactory {

    private static final LZ4PayloadCompressor compressor = new LZ4PayloadCompressor();

    @Override
    public ConfigResponse createResponse(AbstractUtf8Array rawPayload,
                                         long generation,
                                         boolean applyOnRestart,
                                         PayloadChecksums requestsPayloadChecksums) {
        CompressionInfo info = CompressionInfo.create(CompressionType.LZ4, rawPayload.getByteLength());
        Utf8Array compressed = new Utf8Array(compressor.compress(rawPayload.wrap()));
        PayloadChecksums payloadChecksums = generatePayloadChecksums(rawPayload, requestsPayloadChecksums);
        return new SlimeConfigResponse(compressed, generation, applyOnRestart, payloadChecksums, info);
    }

}
