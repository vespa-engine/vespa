// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.protocol.ConfigResponse;

/**
 * Represents a component that creates config responses from a payload. Different implementations
 * can do transformations of the payload such as compression.
 *
 * @author Ulf Lilleengen
 */
public interface ConfigResponseFactory {

    static ConfigResponseFactory create(ConfigserverConfig configserverConfig) {
        switch (configserverConfig.payloadCompressionType()) {
            case LZ4:
                return new LZ4ConfigResponseFactory();
            case UNCOMPRESSED:
                return new UncompressedConfigResponseFactory();
            default:
                throw new IllegalArgumentException("Unknown payload compression type " + configserverConfig.payloadCompressionType());
        }
    }

    /**
     * Creates a {@link ConfigResponse} for a given payload and generation.
     *
     * @param payload        the {@link ConfigPayload} to put in the response
     * @param generation     the payload generation
     * @param applyOnRestart true if this config change should only be applied on restart,
     *                       false if it should be applied immediately
     * @return a {@link ConfigResponse} that can be sent to the client
     */
    ConfigResponse createResponse(ConfigPayload payload, long generation, boolean applyOnRestart);

}
