// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.MetricConsumerFactory;
import com.yahoo.container.jdisc.state.StateMonitor;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.metrics.MetricsPresentationConfig;

/**
 * @author Simon Thoresen Hult
 */
class MetricConsumerProviders {

    public static MetricConsumerProvider newSingletonFactories(MetricConsumer... consumers) {
        MetricConsumerFactory[] factories = new MetricConsumerFactory[consumers.length];
        for (int i = 0; i < consumers.length; ++i) {
            factories[i] = MetricConsumerFactories.newSingleton(consumers[i]);
        }
        return newInstance(factories);
    }

    public static MetricConsumerProvider newInstance(MetricConsumerFactory... factories) {
        return new MetricConsumerProvider(newComponentRegistry(factories));
    }

    public static ComponentRegistry<MetricConsumerFactory> newComponentRegistry(MetricConsumerFactory... factories) {
        ComponentRegistry<MetricConsumerFactory> registry = new ComponentRegistry<>();
        for (MetricConsumerFactory factory : factories) {
            registry.register(new ComponentId(String.valueOf(factory.hashCode())), factory);
        }
        return registry;
    }

}
