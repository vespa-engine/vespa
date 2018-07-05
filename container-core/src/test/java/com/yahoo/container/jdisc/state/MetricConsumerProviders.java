// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.google.inject.Provider;
import com.yahoo.jdisc.application.MetricConsumer;

/**
 * @author Simon Thoresen Hult
 */
class MetricConsumerProviders {

    public static Provider<MetricConsumer> wrap(final StateMonitor statetMonitor) {
        return new Provider<MetricConsumer>() {

            @Override
            public MetricConsumer get() {
                return statetMonitor.newMetricConsumer();
            }
        };
    }
}
