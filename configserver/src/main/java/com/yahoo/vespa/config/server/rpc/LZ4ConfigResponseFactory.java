// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.text.Utf8Array;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.LZ4PayloadCompressor;
import com.yahoo.vespa.config.protocol.CompressionInfo;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.SlimeConfigResponse;
import com.yahoo.vespa.config.util.ConfigUtils;

/**
 * Compressor that compresses config payloads to lz4.
 *
 * @author Ulf Lilleengen
 */
public class LZ4ConfigResponseFactory implements ConfigResponseFactory {

    private static LZ4PayloadCompressor compressor = new LZ4PayloadCompressor();

    @Override
    public ConfigResponse createResponse(ConfigPayload payload,
                                         InnerCNode defFile,
                                         long generation,
                                         boolean internalRedeployment) {
        Utf8Array rawPayload = payload.toUtf8Array(true);
        String configMd5 = ConfigUtils.getMd5(rawPayload);
        CompressionInfo info = CompressionInfo.create(CompressionType.LZ4, rawPayload.getByteLength());
        Utf8Array compressed = new Utf8Array(compressor.compress(rawPayload.getBytes()));
        return new SlimeConfigResponse(compressed, defFile, generation, internalRedeployment, configMd5, info);
    }

}
