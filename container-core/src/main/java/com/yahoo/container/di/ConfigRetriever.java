// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.google.common.collect.Sets;
import com.yahoo.config.ConfigInstance;
import com.yahoo.container.di.componentgraph.core.Keys;
import com.yahoo.container.di.config.Subscriber;
import com.yahoo.vespa.config.ConfigKey;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
public final class ConfigRetriever {

    private static final Logger log = Logger.getLogger(ConfigRetriever.class.getName());

    private final Set<ConfigKey<? extends ConfigInstance>> bootstrapKeys;
    private Set<ConfigKey<? extends ConfigInstance>> componentSubscriberKeys;
    private final Subscriber bootstrapSubscriber;
    private Subscriber componentSubscriber;
    private final Function<Set<ConfigKey<? extends ConfigInstance>>, Subscriber> subscribe;

    public ConfigRetriever(Set<ConfigKey<? extends ConfigInstance>> bootstrapKeys,
                           Function<Set<ConfigKey<? extends ConfigInstance>>, Subscriber> subscribe) {
        this.bootstrapKeys = bootstrapKeys;
        this.componentSubscriberKeys = new HashSet<>();
        this.subscribe = subscribe;
        if (bootstrapKeys.isEmpty()) {
            throw new IllegalArgumentException("Bootstrap key set is empty");
        }
        this.bootstrapSubscriber = subscribe.apply(bootstrapKeys);
        this.componentSubscriber = subscribe.apply(componentSubscriberKeys);
    }

    public ConfigSnapshot getConfigs(Set<ConfigKey<? extends ConfigInstance>> componentConfigKeys,
                                     long leastGeneration, boolean isInitializing) {
        // Loop until we get config.
        while (true) {
            Optional<ConfigSnapshot> maybeSnapshot = getConfigsOnce(componentConfigKeys, leastGeneration, isInitializing);
            if (maybeSnapshot.isPresent()) {
                var configSnapshot = maybeSnapshot.get();
                resetComponentSubscriberIfBootstrap(configSnapshot);
                return configSnapshot;
            }
        }
    }

    Optional<ConfigSnapshot> getConfigsOnce(Set<ConfigKey<? extends ConfigInstance>> componentConfigKeys,
                                            long leastGeneration, boolean isInitializing) {
        if (!Sets.intersection(componentConfigKeys, bootstrapKeys).isEmpty()) {
            throw new IllegalArgumentException(
                    "Component config keys [" + componentConfigKeys + "] overlaps with bootstrap config keys [" + bootstrapKeys + "]");
        }
        log.log(FINE, "getConfigsOnce: " + componentConfigKeys);

        Set<ConfigKey<? extends ConfigInstance>> allKeys = new HashSet<>(componentConfigKeys);
        allKeys.addAll(bootstrapKeys);
        setupComponentSubscriber(allKeys);

        return getConfigsOptional(leastGeneration, isInitializing);
    }

    private Optional<ConfigSnapshot> getConfigsOptional(long leastGeneration, boolean isInitializing) {
        long newestComponentGeneration = componentSubscriber.waitNextGeneration(isInitializing);
        log.log(FINE, "getConfigsOptional: new component generation: " + newestComponentGeneration);

        // leastGeneration is only used to ensure newer generation when the previous generation was invalidated due to an exception
        if (newestComponentGeneration < leastGeneration) {
            return Optional.empty();
        } else if (bootstrapSubscriber.generation() < newestComponentGeneration) {
            long newestBootstrapGeneration = bootstrapSubscriber.waitNextGeneration(isInitializing);
            log.log(FINE, "getConfigsOptional: new bootstrap generation: " + bootstrapSubscriber.generation());
            Optional<ConfigSnapshot> bootstrapConfig = bootstrapConfigIfChanged();
            if (bootstrapConfig.isPresent()) {
                return bootstrapConfig;
            } else {
                if (newestBootstrapGeneration == newestComponentGeneration) {
                    log.log(FINE, "Got new components configs with unchanged bootstrap configs.");
                    return componentsConfigIfChanged();
                } else {
                    // This should not be a normal case, and hence a warning to allow investigation.
                    log.warning("Did not get same generation for bootstrap (" + newestBootstrapGeneration +
                                ") and components configs (" + newestComponentGeneration + ").");
                    return Optional.empty();
                }
            }
        } else {
            // bootstrapGen==componentGen (happens only when a new component subscriber returns first config after bootstrap)
            return componentsConfigIfChanged();
        }
    }

    private Optional<ConfigSnapshot> bootstrapConfigIfChanged() {
        return configIfChanged(bootstrapSubscriber, BootstrapConfigs::new);
    }

    private Optional<ConfigSnapshot> componentsConfigIfChanged() {
        return configIfChanged(componentSubscriber, ComponentsConfigs::new);
    }

    private Optional<ConfigSnapshot> configIfChanged(Subscriber subscriber,
                                                     Function<Map<ConfigKey<? extends ConfigInstance>, ConfigInstance>, ConfigSnapshot> constructor) {
        if (subscriber.configChanged()) {
            return Optional.of(constructor.apply(Keys.covariantCopy(subscriber.config())));
        } else {
            return Optional.empty();
        }
    }

    private void resetComponentSubscriberIfBootstrap(ConfigSnapshot snapshot) {
        if (snapshot instanceof BootstrapConfigs) {
            setupComponentSubscriber(Collections.emptySet());
        }
    }

    private void setupComponentSubscriber(Set<ConfigKey<? extends ConfigInstance>> keys) {
        if (! componentSubscriberKeys.equals(keys)) {
            componentSubscriber.close();
            componentSubscriberKeys = keys;
            try {
                log.log(FINE, "Setting up new component subscriber for keys: " + keys);
                componentSubscriber = subscribe.apply(keys);
            } catch (Throwable e) {
                log.log(Level.WARNING, "Failed setting up subscriptions for component configs: " + e.getMessage());
                log.log(Level.WARNING, "Config keys: " + keys);
                throw e;
            }
        }
    }

    public void shutdown() {
        bootstrapSubscriber.close();
        componentSubscriber.close();
    }

    //TODO: check if these are really needed
    public long getBootstrapGeneration() {
        return bootstrapSubscriber.generation();
    }

    public long getComponentsGeneration() {
        return componentSubscriber.generation();
    }

    public static class ConfigSnapshot {
        private final Map<ConfigKey<? extends ConfigInstance>, ConfigInstance> configs;

        ConfigSnapshot(Map<ConfigKey<? extends ConfigInstance>, ConfigInstance> configs) {
            this.configs = configs;
        }

        public Map<ConfigKey<? extends ConfigInstance>, ConfigInstance> configs() {
            return configs;
        }

        public int size() {
            return configs.size();
        }
    }

    public static class BootstrapConfigs extends ConfigSnapshot {
        BootstrapConfigs(Map<ConfigKey<? extends ConfigInstance>, ConfigInstance> configs) {
            super(configs);
        }
    }

    public static class ComponentsConfigs extends ConfigSnapshot {
        ComponentsConfigs(Map<ConfigKey<? extends ConfigInstance>, ConfigInstance> configs) {
            super(configs);
        }
    }
}
