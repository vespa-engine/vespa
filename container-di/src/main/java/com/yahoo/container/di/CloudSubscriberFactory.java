// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigHandle;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.container.di.config.Subscriber;
import com.yahoo.container.di.config.SubscriberFactory;
import com.yahoo.vespa.config.ConfigKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class CloudSubscriberFactory implements SubscriberFactory {
    private static final Logger log = Logger.getLogger(CloudSubscriberFactory.class.getName());

    private final ConfigSource configSource;
    private Optional<Long> testGeneration = Optional.empty();
    private Map<CloudSubscriber, Integer> activeSubscribers = new WeakHashMap<>();

    public CloudSubscriberFactory(ConfigSource configSource) {
        this.configSource = configSource;
    }

    @Override
    public Subscriber getSubscriber(Set<? extends ConfigKey<?>> configKeys) {
        Set<ConfigKey<ConfigInstance>> subscriptionKeys = new HashSet<>();
        for(ConfigKey<?> key: configKeys) {
            @SuppressWarnings("unchecked") // ConfigKey is defined as <CONFIGCLASS extends ConfigInstance>
            ConfigKey<ConfigInstance> invariant = (ConfigKey<ConfigInstance>) key;
            subscriptionKeys.add(invariant);
        }
        CloudSubscriber subscriber = new CloudSubscriber(subscriptionKeys, configSource);

        testGeneration.ifPresent(subscriber.subscriber::reload); //TODO: test specific code, remove
        activeSubscribers.put(subscriber, 0);

        return subscriber;
    }

    //TODO: test specific code, remove
    @Override
    public void reloadActiveSubscribers(long generation) {
        testGeneration = Optional.of(generation);

        List<CloudSubscriber> subscribers = new ArrayList<>(activeSubscribers.keySet());
        subscribers.forEach(s -> s.subscriber.reload(generation));
    }

    private static class CloudSubscriber implements Subscriber {
        private final ConfigSubscriber subscriber;
        private final Map<ConfigKey<ConfigInstance>, ConfigHandle<ConfigInstance>> handles = new HashMap<>();

        // if waitNextGeneration has not yet been called, -1 should be returned
        private long generation = -1L;

        // True if this reconfiguration was caused by a system-internal redeploy, not an external application change
        private boolean internalRedeploy = false;

        private CloudSubscriber(Set<ConfigKey<ConfigInstance>> keys, ConfigSource configSource) {
            this.subscriber = new ConfigSubscriber(configSource);
            keys.forEach(k -> handles.put(k, subscriber.subscribe(k.getConfigClass(), k.getConfigId())));
        }

        @Override
        public boolean configChanged() {
            return handles.values().stream().anyMatch(ConfigHandle::isChanged);
        }

        @Override
        public long generation() {
            return generation;
        }

        @Override
        public boolean internalRedeploy() {
            return internalRedeploy;
        }

        //mapValues returns a view,, so we need to force evaluation of it here to prevent deferred evaluation.
        @Override
        public Map<ConfigKey<ConfigInstance>, ConfigInstance> config() {
            Map<ConfigKey<ConfigInstance>, ConfigInstance> ret = new HashMap<>();
            handles.forEach((k, v) -> ret.put(k, v.getConfig()));
            return ret;
        }

        @Override
        public long waitNextGeneration() {
            if (handles.isEmpty()) {
                throw new IllegalStateException("No config keys registered");
            }

            /* Catch and just log config exceptions due to missing config values for parameters that do
             * not have a default value. These exceptions occur when the user has removed a component
             * from services.xml, and the component takes a config that has parameters without a
             * default value in the def-file. There is a new 'components' config underway, where the
             * component is removed, so this old config generation will soon be replaced by a new one. */
            boolean gotNextGen = false;
            int numExceptions = 0;
            while (!gotNextGen) {
                try {
                    if (subscriber.nextGeneration()) {
                        gotNextGen = true;
                    }
                } catch (IllegalArgumentException e) {
                    numExceptions++;
                    log.log(Level.WARNING, "Got exception from the config system (please ignore the exception if you just removed "
                            + "a component from your application that used the mentioned config): ", e);
                    if (numExceptions >= 5) {
                        throw new IllegalArgumentException("Failed retrieving the next config generation.", e);
                    }
                }
            }

            generation = subscriber.getGeneration();
            internalRedeploy = subscriber.isInternalRedeploy();
            return generation;
        }

        @Override
        public void close() {
            subscriber.close();
        }
    }


    public static class Provider implements com.google.inject.Provider<SubscriberFactory> {
        @Override
        public SubscriberFactory get() {
            return new CloudSubscriberFactory(ConfigSourceSet.createDefault());
        }
    }
}
