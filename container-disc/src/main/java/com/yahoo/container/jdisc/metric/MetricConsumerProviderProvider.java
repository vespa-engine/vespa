// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.jdisc.MetricConsumerFactory;

/**
 * A dependency injection provider which provides the default metrics provider
 * if no provider is set up explicitly in the application package.
 * The purpose of this is to be a fallback if nothing is set up explicitly.
 *
 * @author bratseth
 */
public class MetricConsumerProviderProvider implements Provider<MetricConsumerProvider> {

    private final MetricConsumerProvider provided;

    @Inject
    public MetricConsumerProviderProvider(ComponentRegistry<MetricConsumerFactory> factoryRegistry) {
        provided = new MetricConsumerProvider(factoryRegistry);
    }

    @Override
    public MetricConsumerProvider get() { return provided; }

    @Override
    public void deconstruct() { }

}
