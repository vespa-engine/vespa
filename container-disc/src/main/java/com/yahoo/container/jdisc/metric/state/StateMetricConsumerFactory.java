// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric.state;

import com.yahoo.container.jdisc.MetricConsumerFactory;
import com.yahoo.container.jdisc.state.StateMonitor;
import com.yahoo.jdisc.application.MetricConsumer;

/**
 * @author Simon Thoresen Hult
 */
public class StateMetricConsumerFactory implements MetricConsumerFactory {

    private final StateMonitor stateMonitor;

    public StateMetricConsumerFactory(StateMonitor stateMonitor) {
        this.stateMonitor = stateMonitor;
    }

    @Override
    public MetricConsumer newInstance() {
        return stateMonitor.newMetricConsumer();
    }

}
