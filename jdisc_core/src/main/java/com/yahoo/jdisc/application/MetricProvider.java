// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.yahoo.jdisc.Metric;

/**
 * @author Simon Thoresen Hult
 */
public class MetricProvider implements Provider<Metric> {

    private final Provider<MetricConsumer> consumerProvider;

    @Inject
    public MetricProvider(Provider<MetricConsumer> consumerProvider) {
        this.consumerProvider = consumerProvider;
    }

    @Override
    public Metric get() {
        return new MetricImpl(consumerProvider);
    }

}
