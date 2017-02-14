// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.*;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author hmusum
 * @since 5.1.10
 */
class MemoryCacheConfigClient extends ConfigSourceClient {

    private final static Logger log = Logger.getLogger(MemoryCacheConfigClient.class.getName());
    private final MemoryCache cache;

    MemoryCacheConfigClient(MemoryCache cache) {
        this.cache = cache;
    }

    /**
     * Retrieves the requested config from the cache. Used when in 'memorycache' mode.
     *
     * @param request The config to retrieve - can be empty (no payload), or have a valid payload.
     * @return A Config with a payload.
     */
    @Override
    RawConfig getConfig(RawConfig input, JRTServerConfigRequest request) {
        log.log(LogLevel.DEBUG, "Getting config from cache");
        ConfigKey<?> key = input.getKey();
        RawConfig cached = cache.get(new ConfigCacheKey(key, input.getDefMd5()));
        if (cached != null) {
            log.log(LogLevel.DEBUG, "Found config " + key + " in cache");
            return cached;
        } else {
            return null;
        }
    }

    @Override
    void cancel() {
    }

    @Override
    void shutdownSourceConnections() {
    }

    @Override
    String getActiveSourceConnection() {
        return "N/A";
    }

    @Override
    List<String> getSourceConnections() {
        return Collections.singletonList("N/A");
    }

}
