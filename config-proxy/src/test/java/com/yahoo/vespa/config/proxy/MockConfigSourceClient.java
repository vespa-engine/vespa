// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Mock client that always returns with config immediately
 *
 * @author hmusum
 */
public class MockConfigSourceClient implements ConfigSourceClient{
    private final MockConfigSource configSource;
    private final MemoryCache memoryCache;
    private final DelayedResponses delayedResponses = new DelayedResponses();

    MockConfigSourceClient(MockConfigSource configSource) {
        this.configSource = configSource;
        this.memoryCache = new MemoryCache();
    }

    @Override
    public Optional<RawConfig> getConfig(RawConfig input, JRTServerConfigRequest request) {
        RawConfig config = getConfig(input.getKey());
        memoryCache.update(config);
        return Optional.of(config);
    }

    private RawConfig getConfig(ConfigKey<?> configKey) {
        return configSource.getConfig(configKey);
    }

    @Override
    public void shutdown() {
        configSource.clear();
    }

    @Override
    public void shutdownSourceConnections() {
    }

    @Override
    public String getActiveSourceConnection() {
        return "N/A";
    }

    @Override
    public List<String> getSourceConnections() {
        return Collections.singletonList("N/A");
    }

    @Override
    public DelayedResponses delayedResponses() { return delayedResponses; }

    @Override
    public MemoryCache memoryCache() { return memoryCache; }

}
