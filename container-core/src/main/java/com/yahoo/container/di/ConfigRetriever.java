// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.google.common.collect.Sets;
import com.yahoo.config.ConfigInstance;
import com.yahoo.container.di.componentgraph.core.Keys;
import com.yahoo.container.di.config.Subscriber;
import com.yahoo.container.di.config.SubscriberFactory;
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

    private final SubscriberFactory subscriberFactory;
    private final Subscriber bootstrapSubscriber;
    private Subscriber componentSubscriber;
    private int componentSubscriberIndex;

    public ConfigRetriever(Set<ConfigKey<? extends ConfigInstance>> bootstrapKeys, SubscriberFactory subscriberFactory) {
        this.bootstrapKeys = bootstrapKeys;
        this.componentSubscriberKeys = new HashSet<>();
        this.subscriberFactory = subscriberFactory;
        if (bootstrapKeys.isEmpty()) {
            throw new IllegalArgumentException("Bootstrap key set is empty");
        }
        this.bootstrapSubscriber = this.subscriberFactory.getSubscriber(bootstrapKeys, "bootstrap");
        this.componentSubscriber = this.subscriberFactory.getSubscriber(componentSubscriberKeys, "component_" + ++componentSubscriberIndex);
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

    private Optional<ConfigSnapshot> getConfigsOnce(Set<ConfigKey<? extends ConfigInstance>> componentConfigKeys,
                                                    long leastGeneration, boolean isInitializing) {
        if (!Sets.intersection(componentConfigKeys, bootstrapKeys).isEmpty()) {
            throw new IllegalArgumentException(
                    "Component config keys [" + componentConfigKeys + "] overlaps with bootstrap config keys [" + bootstrapKeys + "]");
        }
        Set<ConfigKey<? extends ConfigInstance>> allKeys = new HashSet<>(componentConfigKeys);
        allKeys.addAll(bootstrapKeys);
        setupComponentSubscriber(allKeys);

        if (componentSubscriber.generation() < bootstrapSubscriber.generation()) {
            return getComponentsSnapshot(leastGeneration, isInitializing);
        }
        long newestBootstrapGeneration = bootstrapSubscriber.waitNextGeneration(isInitializing);
        log.log(FINE, () -> "getConfigsOptional: new bootstrap generation: " + newestBootstrapGeneration);

        // leastGeneration is used to ensure newer generation (than the latest bootstrap or component gen)
        // when the previous generation was invalidated due to an exception upon creating the component graph.
        if (newestBootstrapGeneration < leastGeneration) {
            return Optional.empty();
        }
        return bootstrapConfigIfChanged()
                // At this point, we normally assume that the bootstrap subscriber is one generation ahead
                // of the component subscriber, but this is not always the case in practice.
                .or(this::componentsSnapshotReceivedBeforeBootstrap);
    }

    private Optional<ConfigSnapshot> componentsSnapshotReceivedBeforeBootstrap() {
        if (componentSubscriber.generation() == bootstrapSubscriber.generation()) {
            // The component subscriber originally had a newer config generation than the bootstrap subscriber.
            // Ensure that this generation is applied if it contains changed configs.
            // The root cause is probably that the component subscriber skipped a generation and got
            // one ahead of the bootstrap subscriber.
            return componentsConfigIfChanged();
        }
        return Optional.empty();
    }

    private Optional<ConfigSnapshot> getComponentsSnapshot(long leastGeneration, boolean isInitializing) {
        long newestBootstrapGeneration = bootstrapSubscriber.generation();
        long newestComponentGeneration = componentSubscriber.waitNextGeneration(isInitializing);
        if (newestComponentGeneration < leastGeneration) {
            log.log(FINE, () -> "Component generation too old: " + componentSubscriber.generation() + " < " + leastGeneration);
            return Optional.empty();
        }
        if (newestComponentGeneration == newestBootstrapGeneration) {
            log.log(FINE, () -> "getConfigsOptional: new component generation: " + componentSubscriber.generation());
            return componentsConfigIfChanged();
        } else {
            // Should not be a normal case, and hence a warning to allow investigation.
            log.warning("Did not get same generation for bootstrap (" + newestBootstrapGeneration +
                                ") and components configs (" + newestComponentGeneration + ").");
            return Optional.empty();
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

    private void resetComponentSubscriberIfBootstrap(ConfigSnapshot configSnapshot) {
        if (configSnapshot instanceof BootstrapConfigs) {
            setupComponentSubscriber(Collections.emptySet());
        }
    }

    private void setupComponentSubscriber(Set<ConfigKey<? extends ConfigInstance>> keys) {
        if (! componentSubscriberKeys.equals(keys)) {
            componentSubscriber.close();
            log.log(FINE, () -> "Closed " + componentSubscriber);
            componentSubscriberKeys = keys;
            try {
                componentSubscriber = subscriberFactory.getSubscriber(keys, "component_" + ++componentSubscriberIndex);
                log.log(FINE, () -> "Set up new subscriber " + componentSubscriber + " for keys: " + keys);
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
