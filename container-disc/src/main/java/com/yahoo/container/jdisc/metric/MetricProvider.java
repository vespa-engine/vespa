// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.application.MetricConsumer;

/**
 * <p>This class implements a {@link Provider} component of <tt>Metric</tt>. Because this class depends on {@link
 * MetricConsumerProvider}, any change to the consumer configuration will trigger reconfiguration of this component,
 * which in turn triggers reconfiguration of any component that depends on <tt>Metric</tt>.</p>
 *
 * @author Simon Thoresen Hult
 */
public final class MetricProvider implements Provider<Metric> {

    private final Metric metric;

    public MetricProvider(MetricConsumerProvider provider) {
        metric = new com.yahoo.jdisc.application.MetricProvider(new com.google.inject.Provider<MetricConsumer>() {

            @Override
            public MetricConsumer get() {
                return provider.newInstance();
            }
        }).get();
    }

    @Override
    public Metric get() {
        return metric;
    }

    @Override
    public void deconstruct() {

    }

}
