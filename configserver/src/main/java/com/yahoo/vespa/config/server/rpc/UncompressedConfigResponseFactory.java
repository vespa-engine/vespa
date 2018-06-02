// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.text.Utf8Array;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.protocol.CompressionInfo;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.SlimeConfigResponse;
import com.yahoo.vespa.config.util.ConfigUtils;

/**
 * Simply returns an uncompressed payload.
 *
 * @author Ulf Lilleengen
 */
public class UncompressedConfigResponseFactory implements ConfigResponseFactory {

    @Override
    public ConfigResponse createResponse(ConfigPayload payload,
                                         InnerCNode defFile,
                                         long generation,
                                         boolean internalRedeployment) {
        Utf8Array rawPayload = payload.toUtf8Array(true);
        String configMd5 = ConfigUtils.getMd5(rawPayload);
        CompressionInfo info = CompressionInfo.create(CompressionType.UNCOMPRESSED, rawPayload.getByteLength());
        return new SlimeConfigResponse(rawPayload, defFile, generation, internalRedeployment, configMd5, info);
    }

}
