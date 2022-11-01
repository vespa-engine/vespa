// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigSet;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.vespa.config.ConfigKey;

import java.lang.reflect.Constructor;

/**
 * Subscription on a programmatically built set of configs
 *
 * @author Vegard Havdal
 */
public class ConfigSetSubscription<T extends ConfigInstance> extends ConfigSubscription<T> {

    private final ConfigSet set;
    private final ConfigKey<T> subKey;

    ConfigSetSubscription(ConfigKey<T> key, ConfigSet cset) {
        super(key);
        this.set = cset;
        this.subKey = new ConfigKey<>(configClass, key.getConfigId());
        if ( ! set.contains(subKey)) {
            throw new IllegalArgumentException("The given ConfigSet " + set + " does not contain a config for " + subKey);
        }
        setGeneration(0L);
    }

    private boolean hasConfigChanged() {
        T myInstance = getNewInstance();
        ConfigState<T> configState = getConfigState();
        // User forced reload
        if (checkReloaded()) {
            setConfigIfChanged(myInstance);
            return true;
        }
        if (!myInstance.equals(configState.getConfig())) {
            setConfigIncGen(myInstance);
            return true;
        }
        return false;
    }

    @Override
    public boolean nextConfig(long timeout) {
        if (hasConfigChanged()) return true;
        if (timeout <= 0) return false;

        long end = System.nanoTime() + timeout * 1_000_000;
        do {
            sleep();
            if (hasConfigChanged()) return true;
        } while (System.nanoTime() < end);
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
