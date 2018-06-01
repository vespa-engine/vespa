// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone;

import com.yahoo.config.ConfigBuilder;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigInterruptedException;
import com.yahoo.container.di.config.Subscriber;
import com.yahoo.container.di.config.SubscriberFactory;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.model.VespaModel;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
public class StandaloneSubscriberFactory implements SubscriberFactory {
    private final VespaModel root;

    public StandaloneSubscriberFactory(VespaModel root) {
        this.root = root;
    }

    private class StandaloneSubscriber implements Subscriber {

        private final Set<ConfigKey<ConfigInstance>> configKeys;
        private long generation = -1L;

        StandaloneSubscriber(Set<ConfigKey<ConfigInstance>> configKeys) {
            this.configKeys = configKeys;
        }

        @Override
        public boolean internalRedeploy() { return false; }

        @Override
        public boolean configChanged() {
            return generation == 0;
        }

        @Override
        public void close() {
        }

        @Override
        public Map<ConfigKey<ConfigInstance>, ConfigInstance> config() {
            Map<ConfigKey<ConfigInstance>, ConfigInstance> ret = new HashMap<>();
            for (ConfigKey<ConfigInstance> key : configKeys) {
                ConfigInstance.Builder builder = root.getConfig(newBuilderInstance(key), key.getConfigId());
                if (builder == null) {
                    throw new RuntimeException("Invalid config id " + key.getConfigId());
                }
                ret.put(key, newConfigInstance(builder));
            }
            return ret;
        }

        @Override
        public long waitNextGeneration() {
            generation++;

            if (generation != 0) {
                try {
                    while (!Thread.interrupted()) {
                        Thread.sleep(10000);
                    }
                } catch (InterruptedException e) {
                    throw new ConfigInterruptedException(e);
                }
            }

            return generation;
        }

        // if waitNextGeneration has not yet been called, -1 should be returned
        @Override
        public long generation() {
            return generation;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Subscriber getSubscriber(Set<? extends ConfigKey<?>> configKeys) {
        return new StandaloneSubscriber((Set<ConfigKey<ConfigInstance>>) configKeys);
    }

    public void reloadActiveSubscribers(long generation) {
        throw new RuntimeException("unsupported");
    }

    private static ConfigInstance.Builder newBuilderInstance(ConfigKey<ConfigInstance> key) {
        try {
            return builderClass(key).getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException("ConfigInstance builder cannot be instantiated", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<ConfigInstance.Builder> builderClass(ConfigKey<ConfigInstance> key) {
        Class<?> configClass = key.getConfigClass();
        if (configClass != null) {
            Class<?>[] nestedClasses = configClass.getClasses();
            for (Class<?> clazz : nestedClasses) {
                if (clazz.getName().equals(key.getConfigClass().getName() + "$Builder")) {
                    return (Class<ConfigInstance.Builder>) clazz;
                }
            }
        }
        throw new RuntimeException("Builder class for " + (configClass == null ? null : configClass.getName()) + " could not be located");
    }

    private static ConfigInstance newConfigInstance(ConfigBuilder builder) {
        try {
            return configClass(builder).getConstructor(builder.getClass()).newInstance(builder);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException("ConfigInstance cannot be instantiated", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<ConfigInstance> configClass(ConfigBuilder builder) {
        return (Class<ConfigInstance>) builder.getClass().getEnclosingClass();
    }
}
