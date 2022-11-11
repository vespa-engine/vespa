// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigHandle;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.container.di.config.Subscriber;
import com.yahoo.vespa.config.ConfigKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 * @author ollivir
 */
public class CloudSubscriber  implements Subscriber {
    private static final Logger log = Logger.getLogger(CloudSubscriber.class.getName());

    private final String name;
    private final ConfigSubscriber subscriber;
    private final Map<ConfigKey<ConfigInstance>, ConfigHandle<ConfigInstance>> handles = new HashMap<>();

    // if waitNextGeneration has not yet been called, -1 should be returned
    private long generation = -1L;

    CloudSubscriber(ExecutorService executor, String name, ConfigSource configSource, Set<ConfigKey<ConfigInstance>> keys) {
        this.name = name;
        this.subscriber = new ConfigSubscriber(configSource);
        Map<ConfigKey<ConfigInstance>, Future<ConfigHandle<ConfigInstance>>> futureHandles = new HashMap<>();
        keys.forEach(k -> futureHandles.put(k, executor.submit(() -> subscriber.subscribe(k.getConfigClass(), k.getConfigId()))));
        futureHandles.forEach((k, f) -> {
            try {
                handles.put(k, f.get());
            } catch (InterruptedException | ExecutionException e) {}
        });
    }

    @Override
    public boolean configChanged() {
        return handles.values().stream().anyMatch(ConfigHandle::isChanged);
    }

    @Override
    public long generation() {
        return generation;
    }

    //mapValues returns a view, so we need to force evaluation of it here to prevent deferred evaluation.
    @Override
    public Map<ConfigKey<ConfigInstance>, ConfigInstance> config() {
        Map<ConfigKey<ConfigInstance>, ConfigInstance> ret = new HashMap<>();
        handles.forEach((k, v) -> {
            ConfigInstance config = v.getConfig();
            if (config == null) {
                throw new IllegalArgumentException("Got a null config from the config system for key: " + k +
                        "\nConfig handle: " + v);
            }
            ret.put(k, config);
        });
        return ret;
    }

    @Override
    public long waitNextGeneration(boolean isInitializing) {
        if (handles.isEmpty())
            throw new IllegalStateException("No config keys registered");

        boolean gotNextGen = false;
        while ( ! gotNextGen) {
            try {
                if (subscriber.nextGeneration(isInitializing)) {
                    gotNextGen = true;
                    log.log(FINE, () -> this + " got next config generation " + subscriber.getGeneration() + "\n" + subscriber.toString());
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Failed retrieving the next config generation", e);
            }
        }

        generation = subscriber.getGeneration();
        return generation;
    }

    @Override
    public void close() {
        subscriber.close();
    }

    @Override
    public String toString() {
        return "CloudSubscriber{" + name + ", gen." + generation + "}";
    }

    // TODO: Remove, only used by test specific code in CloudSubscriberFactory
    ConfigSubscriber getSubscriber() {
        return subscriber;
    }

}
