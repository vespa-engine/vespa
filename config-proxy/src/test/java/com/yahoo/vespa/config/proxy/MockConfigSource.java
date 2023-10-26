// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

/**
 * A simple class to be able to test config proxy without having an RPC config
 * source.
 *
 * @author hmusum
 */
class MockConfigSource extends ConfigSourceSet {
    private final HashMap<ConfigKey<?>, RawConfig> backing = new HashMap<>();

    void put(ConfigKey<?> key, RawConfig config) {
        backing.put(key, config);
    }

    RawConfig getConfig(ConfigKey<?> key) {
        return backing.get(key);
    }

    void clear() {
        backing.clear();
    }

    @Override
    public Set<String> getSources() {
        return Collections.singleton("tcp/localhost:19070,tcp/localhost:19071,tcp/localhost:19072");
    }

}
