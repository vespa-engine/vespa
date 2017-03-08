// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * A simple class to be able to test config proxy without having an RPC config
 * source.
 *
 * @author hmusum
 * @since 5.1.10
 */
// TODO Move to src/test/
public class MapBackedConfigSource implements ConfigSource, ConfigSourceClient {
    private final HashMap<ConfigKey<?>, RawConfig> backing = new HashMap<>();
    private final ClientUpdater clientUpdater;

    MapBackedConfigSource(ClientUpdater clientUpdater) {
        this.clientUpdater = clientUpdater;
    }

    MapBackedConfigSource put(ConfigKey<?> key, RawConfig config) {
        backing.put(key, config);
        clientUpdater.updateSubscribers(config);
        return this;
    }

    @Override
    public RawConfig getConfig(RawConfig input, JRTServerConfigRequest request) {
        final RawConfig config = getConfig(input.getKey());
        clientUpdater.getMemoryCache().put(config);
        return config;
    }

    RawConfig getConfig(ConfigKey<?> configKey) {
        return backing.get(configKey);
    }

    @Override
    public void cancel() {
        clear();
    }

    @Override
    public void shutdownSourceConnections() {
    }

    public void clear() {
        backing.clear();
    }

    @Override
    public String getActiveSourceConnection() {
        return "N/A";
    }

    @Override
    public List<String> getSourceConnections() {
        return Collections.singletonList("N/A");
    }
}
