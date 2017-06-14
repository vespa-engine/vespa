// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.vespa.config.server.rpc.ConfigResponseFactory;
import com.yahoo.vespa.config.server.rpc.LZ4ConfigResponseFactory;
import com.yahoo.vespa.config.server.rpc.UncompressedConfigResponseFactory;

/**
 * Logic to select the appropriate response factory based on config.
 * TODO: Move this to {@link ConfigResponseFactory} when we have java 8.
 *
 * @author lulf
 * @since 5.20
 */
public class ConfigResponseFactoryFactory {

    public static ConfigResponseFactory createFactory(ConfigserverConfig configserverConfig) {
        switch (configserverConfig.payloadCompressionType()) {
            case LZ4:
                return new LZ4ConfigResponseFactory();
            case UNCOMPRESSED:
                return new UncompressedConfigResponseFactory();
            default:
                throw new IllegalArgumentException("Unknown payload compression type " + configserverConfig.payloadCompressionType());
        }
    }

}
