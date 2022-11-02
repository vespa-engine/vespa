// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.Metric;

/**
 * An implementation of {@link Provider} component of <code>Metric</code>. Because this class depends on {@link
 * MetricConsumerProvider}, any change to the consumer configuration will trigger reconfiguration of this component,
 * which in turn triggers reconfiguration of any component that depends on <code>Metric</code>.
 *
 * @author Simon Thoresen Hult
 */
public final class MetricProvider implements Provider<Metric> {

    private final Metric metric;

    public MetricProvider(MetricConsumerProvider provider) {
        metric = new com.yahoo.jdisc.application.MetricProvider(provider::newInstance).get();
    }

    @Override
    public Metric get() {
        return metric;
    }

    @Override
    public void deconstruct() { }

}
