// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.container.di.config.Subscriber;
import com.yahoo.container.di.config.SubscriberFactory;
import com.yahoo.vespa.config.ConfigKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class CloudSubscriberFactory implements SubscriberFactory {

    private final ConfigSource configSource;
    private final Map<CloudSubscriber, Integer> activeSubscribers = new WeakHashMap<>();

    private Optional<Long> testGeneration = Optional.empty();

    public CloudSubscriberFactory(ConfigSource configSource) {
        this.configSource = configSource;
    }

    @Override
    public Subscriber getSubscriber(Set<? extends ConfigKey<?>> configKeys, String name) {
        Set<ConfigKey<ConfigInstance>> subscriptionKeys = new HashSet<>();
        for(ConfigKey<?> key: configKeys) {
            @SuppressWarnings("unchecked") // ConfigKey is defined as <CONFIGCLASS extends ConfigInstance>
            ConfigKey<ConfigInstance> invariant = (ConfigKey<ConfigInstance>) key;
            subscriptionKeys.add(invariant);
        }
        CloudSubscriber subscriber = new CloudSubscriber(name, configSource, subscriptionKeys);

        testGeneration.ifPresent(subscriber.getSubscriber()::reload); // TODO: test specific code, remove
        activeSubscribers.put(subscriber, 0);

        return subscriber;
    }

    //TODO: test specific code, remove
    @Override
    public void reloadActiveSubscribers(long generation) {
        testGeneration = Optional.of(generation);

        List<CloudSubscriber> subscribers = new ArrayList<>(activeSubscribers.keySet());
        subscribers.forEach(s -> s.getSubscriber().reload(generation));
    }

    public static class Provider implements com.google.inject.Provider<SubscriberFactory> {
        @Override
        public SubscriberFactory get() {
            return new CloudSubscriberFactory(ConfigSourceSet.createDefault());
        }
    }

}
