// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.text.AbstractUtf8Array;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.PayloadChecksum;
import com.yahoo.vespa.config.protocol.ConfigResponse;

/**
 * Represents a component that creates config responses from a payload. Different implementations
 * can do transformations of the payload such as compression.
 *
 * @author Ulf Lilleengen
 */
public interface ConfigResponseFactory {

    static ConfigResponseFactory create(ConfigserverConfig configserverConfig) {
        return switch (configserverConfig.payloadCompressionType()) {
            case LZ4 -> new LZ4ConfigResponseFactory();
            case UNCOMPRESSED -> new UncompressedConfigResponseFactory();
        };
    }

    /**
     * Creates a {@link ConfigResponse} for a given payload and generation.
     *
     * @param rawPayload                the {@link ConfigPayload} to put in the response
     * @param generation                the payload generation
     * @param applyOnRestart            true if this config change should only be applied on restart,
     *                                  false if it should be applied immediately
     * @param requestPayloadChecksum  payload checksum from request
     * @return a {@link ConfigResponse} that can be sent to the client
     */
    ConfigResponse createResponse(AbstractUtf8Array rawPayload,
                                  long generation,
                                  boolean applyOnRestart,
                                  PayloadChecksum requestPayloadChecksum);

    /** Generates payload checksum for the given payload */
    default PayloadChecksum generatePayloadChecksum(AbstractUtf8Array rawPayload, PayloadChecksum requestPayloadChecksum) {
        return PayloadChecksum.fromData(rawPayload);
    }

}
