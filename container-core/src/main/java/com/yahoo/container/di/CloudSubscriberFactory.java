// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.container.di.config.Subscriber;
import com.yahoo.container.di.config.SubscriberFactory;
import com.yahoo.vespa.config.ConfigKey;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class CloudSubscriberFactory implements SubscriberFactory {

    private final ConfigSource configSource;

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

        return new CloudSubscriber(name, configSource, subscriptionKeys);
    }

    public static class Provider implements com.google.inject.Provider<SubscriberFactory> {
        @Override
        public SubscriberFactory get() {
            return new CloudSubscriberFactory(ConfigSourceSet.createDefault());
        }
    }

}
