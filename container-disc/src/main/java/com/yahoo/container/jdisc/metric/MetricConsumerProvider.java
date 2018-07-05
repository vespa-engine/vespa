// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.google.inject.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.MetricConsumerFactory;
import com.yahoo.container.jdisc.metric.state.StateMetricConsumerFactory;
import com.yahoo.container.jdisc.state.StateMonitor;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.metrics.MetricsPresentationConfig;


/**
 * <p>The purpose of this class it to be the only provider for the <tt>MetricConsumer</tt> interface in a component
 * graph. This component is automatically registered in the graph by the config server. Configuring a different
 * <tt>MetricConsumer</tt> is done by registering one or more {@link MetricConsumerFactory} in the services-file.</p>
 *
 * <p>Because this class depends on the {@link ComponentRegistry} of {@link MetricConsumerFactory}, any added or removed
 * {@link MetricConsumerFactory} will cause this component to be reconfigured. Because {@link MetricProvider} depends on
 * this class, which means any component that uses <tt>Metric</tt> will be reconfigured. Any component that depends
 * directly on <tt>MetricConsumer</tt> will also be reconfigured.</p>
 *
 * @author Simon Thoresen Hult
 */
public class MetricConsumerProvider {

    private final MetricConsumerFactory[] factories;

    @Inject
    public MetricConsumerProvider(ComponentRegistry<MetricConsumerFactory> factoryRegistry,
                                  MetricsPresentationConfig presentationConfig,
                                  StateMonitor stateMonitor) {
        MetricConsumerFactory[] factories;
        if (factoryRegistry.getComponentCount() == 0 || ! presentationConfig.slidingwindow()) {
            factories = new MetricConsumerFactory[1];
            factories[0] = new StateMetricConsumerFactory(stateMonitor);
        } else {
            factories = new MetricConsumerFactory[factoryRegistry.getComponentCount()];
            factoryRegistry.allComponents().toArray(factories);
        }
        this.factories = factories;
    }

    public MetricConsumer newInstance() {
        if (factories.length == 1) {
            return factories[0].newInstance();
        }
        MetricConsumer[] consumers = new MetricConsumer[factories.length];
        for (int i = 0; i < factories.length; ++i) {
            consumers[i] = factories[i].newInstance();
        }
        return new ForwardingMetricConsumer(consumers);
    }

}
