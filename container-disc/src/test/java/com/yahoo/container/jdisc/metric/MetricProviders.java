// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.yahoo.container.jdisc.MetricConsumerFactory;
import com.yahoo.jdisc.application.MetricConsumer;

/**
 * @author Simon Thoresen Hult
 */
class MetricProviders {

    public static MetricProvider newInstance(MetricConsumer... consumers) {
        return new MetricProvider(MetricConsumerProviders.newSingletonFactories(consumers));
    }

    public static MetricProvider newInstance(MetricConsumerFactory... factories) {
        return new MetricProvider(MetricConsumerProviders.newInstance(factories));
    }
}
