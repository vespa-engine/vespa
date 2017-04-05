// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;

import java.util.HashMap;

/**
 * A simple class to be able to test config proxy without having an RPC config
 * source.
 *
 * @author hmusum
 * @since 5.1.10
 */
class MockConfigSource implements ConfigSource {
    private final HashMap<ConfigKey<?>, RawConfig> backing = new HashMap<>();
    private final ClientUpdater clientUpdater;

    MockConfigSource(ClientUpdater clientUpdater) {
        this.clientUpdater = clientUpdater;
    }

    MockConfigSource put(ConfigKey<?> key, RawConfig config) {
        backing.put(key, config);
        clientUpdater.updateSubscribers(config);
        return this;
    }

    RawConfig getConfig(ConfigKey<?> key) {
        return backing.get(key);
    }

    void clear() {
        backing.clear();
    }

}
