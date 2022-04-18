// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The client used for getting config when running in 'memorycache' mode.
 *
 * @author hmusum
 */
class MemoryCacheConfigClient implements ConfigSourceClient {

    private final static Logger log = Logger.getLogger(MemoryCacheConfigClient.class.getName());
    private final MemoryCache cache;
    private final DelayedResponses delayedResponses = new DelayedResponses();

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
    public Optional<RawConfig> getConfig(RawConfig input, JRTServerConfigRequest request) {
        ConfigKey<?> key = input.getKey();

        Optional<RawConfig> cached = cache.get(new ConfigCacheKey(key, input.getDefMd5()));
        if (cached.isPresent())
            log.log(Level.FINE, () -> "Found config " + key + " in cache");
        return cached;
    }

    @Override
    public void shutdown() {}

    @Override
    public void shutdownSourceConnections() {}

    @Override
    public String getActiveSourceConnection() {
        return "N/A";
    }

    @Override
    public List<String> getSourceConnections() {
        return Collections.singletonList("N/A");
    }

    @Override
    public DelayedResponses delayedResponses() {
        return delayedResponses;
    }

    @Override
    public MemoryCache memoryCache() { return cache; }

}
