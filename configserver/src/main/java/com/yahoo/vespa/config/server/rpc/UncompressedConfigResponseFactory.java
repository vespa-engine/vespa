// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.text.AbstractUtf8Array;
import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.vespa.config.protocol.CompressionInfo;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.SlimeConfigResponse;

/**
 * Simply returns an uncompressed payload.
 *
 * @author Ulf Lilleengen
 */
public class UncompressedConfigResponseFactory implements ConfigResponseFactory {

    @Override
    public ConfigResponse createResponse(AbstractUtf8Array rawPayload,
                                         long generation,
                                         boolean applyOnRestart,
                                         PayloadChecksums requestsPayloadChecksums) {
        CompressionInfo info = CompressionInfo.create(CompressionType.UNCOMPRESSED, rawPayload.getByteLength());
        PayloadChecksums payloadChecksums = generatePayloadChecksums(rawPayload, requestsPayloadChecksums);
        return new SlimeConfigResponse(rawPayload, generation, applyOnRestart, payloadChecksums, info);
    }

}
