// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import java.util.List;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigHandle;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.TimingValues;

/**
 * A subscriber that can subscribe without supplying a config class. Used by config proxy.
 *
 * @author Vegard Havdal
 */
public class GenericConfigSubscriber extends ConfigSubscriber {

    /**
     * Constructs a new subscriber using the given requester
     *
     * @param requester a config requester
     */
    public GenericConfigSubscriber(JRTConfigRequester requester) {
        this.requester = requester;
    }

    public GenericConfigSubscriber() {
        super();
    }

    /**
     * Subscribes to config without using a config class. For internal use in config proxy.
     *
     * @param key the {@link ConfigKey to subscribe to}
     * @param defContent the config definition content for the config to subscribe to
     * @param source the config source to use
     * @param timingValues {@link TimingValues}
     * @return generic handle
     */
    public GenericConfigHandle subscribe(ConfigKey<RawConfig> key, List<String> defContent, ConfigSource source, TimingValues timingValues) {
        checkStateBeforeSubscribe();
        GenericJRTConfigSubscription sub = new GenericJRTConfigSubscription(key, defContent, this, source, timingValues);
        GenericConfigHandle handle = new GenericConfigHandle(sub);
        subscribeAndHandleErrors(sub, key, handle, timingValues);
        return handle;
    }

    @Override
    public <T extends ConfigInstance> ConfigHandle<T> subscribe(Class<T> configClass, String configId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends ConfigInstance> ConfigHandle<T> subscribe(Class<T> configClass, String configId, long timeoutMillis) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends ConfigInstance> ConfigHandle<T> subscribe(SingleSubscriber<T> singleSubscriber, Class<T> configClass, String configId) {
        throw new UnsupportedOperationException();
    }

}
