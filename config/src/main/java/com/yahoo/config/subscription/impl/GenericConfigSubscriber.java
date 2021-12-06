// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigHandle;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.TimingValues;

import java.util.List;

/**
 * A subscriber that can subscribe without the class. Used by config proxy.
 *
 * @author Vegard Havdal
 */
public class GenericConfigSubscriber extends ConfigSubscriber {

    private final JRTConfigRequester requester;

    /**
     * Constructs a new subscriber using the given pool of requesters (JRTConfigRequester holds 1 connection which in
     * turn is subject to failover across the elements in the source set.)
     * The behaviour is undefined if the map key is different from the source set the requester was built with.
     * See also {@link JRTConfigRequester#JRTConfigRequester(com.yahoo.vespa.config.ConnectionPool, com.yahoo.vespa.config.TimingValues)}
     *
     * @param requester a config requester
     */
    public GenericConfigSubscriber(JRTConfigRequester requester) {
        this.requester = requester;
    }

    /**
     * Subscribes to config without using the class. For internal use in config proxy.
     *
     * @param key the {@link ConfigKey to subscribe to}
     * @param defContent the config definition content for the config to subscribe to
     * @param timingValues {@link TimingValues}
     * @return generic handle
     */
    public GenericConfigHandle subscribe(ConfigKey<RawConfig> key, List<String> defContent, TimingValues timingValues) {
        checkStateBeforeSubscribe();
        GenericJRTConfigSubscription sub = new GenericJRTConfigSubscription(key, defContent, requester, timingValues);
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

    /**
     * Do nothing, since we share requesters
     */
    public void closeRequesters() {
    }

}
