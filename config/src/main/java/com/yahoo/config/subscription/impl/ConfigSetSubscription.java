// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigSet;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.vespa.config.ConfigKey;

import java.lang.reflect.Constructor;

/**
 * Subscription on a programmatically built set of configs
 * @author vegardh
 * @since 5.1
 */
public class ConfigSetSubscription<T extends ConfigInstance> extends ConfigSubscription<T> {

    private final ConfigSet set;
    private final ConfigKey<T> subKey;

    ConfigSetSubscription(ConfigKey<T> key,
            ConfigSubscriber subscriber, ConfigSource cset) {
        super(key, subscriber);
        if (!(cset instanceof ConfigSet)) throw new IllegalArgumentException("Source is not a ConfigSet: "+cset);
        this.set=(ConfigSet) cset;
        subKey = new ConfigKey<T>(configClass, key.getConfigId());
        if (!set.contains(subKey)) {
            throw new IllegalArgumentException("The given ConfigSet "+set+" does not contain a config for "+subKey);
        }
        setGeneration(0L);
    }

    @Override
    public boolean nextConfig(long timeout) {
        long end = System.currentTimeMillis() + timeout;
        do {
            ConfigInstance myInstance = getNewInstance();
            ConfigState<T> configState = getConfigState();
            // User forced reload
            if (checkReloaded()) {
                setConfigIfChanged((T)myInstance);
                return true;
            }
            if (!myInstance.equals(configState.getConfig())) {
                setConfigIfChangedIncGen((T)myInstance);
                return true;
            }
            sleep();
        } while (System.currentTimeMillis() < end);
        return false;
    }

    private void sleep() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            throw new RuntimeException("nextConfig aborted", e);
        }
    }

    @Override
    public boolean subscribe(long timeout) {
        return true;
    }

    @SuppressWarnings("unchecked")
    private T getNewInstance() {
        try {
            ConfigInstance.Builder builder = set.get(subKey);
            Constructor<?> constructor = builder.getClass().getDeclaringClass().getConstructor(builder.getClass());
            return (T) constructor.newInstance(builder);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
