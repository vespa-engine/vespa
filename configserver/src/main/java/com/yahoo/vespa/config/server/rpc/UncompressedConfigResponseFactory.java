// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.text.AbstractUtf8Array;
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
    public ConfigResponse createResponse(AbstractUtf8Array rawPayload, long generation, boolean applyOnRestart) {
        String configMd5 = ConfigUtils.getMd5(rawPayload);
        String xxHash64 = ConfigUtils.getXxhash64(rawPayload);
        CompressionInfo info = CompressionInfo.create(CompressionType.UNCOMPRESSED, rawPayload.getByteLength());
        return new SlimeConfigResponse(rawPayload, generation, applyOnRestart, PayloadChecksums.from(configMd5, xxHash64), info);
    }

}
