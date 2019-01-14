// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.jrt;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.Metric;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;

/**
 * The default factory for JRT {@link Supervisor}. Tracks jrt metrics.
 *
 * @author bjorncs
 */
public class DefaultJrtFactory extends AbstractComponent implements JrtFactory {

    private final JrtMetricsUpdater metricsUpdater;

    @Inject
    public DefaultJrtFactory(Metric metric) {
        this.metricsUpdater = new JrtMetricsUpdater(metric);
    }

    @Override
    public Supervisor createSupervisor() {
        Supervisor supervisor = new Supervisor(new Transport());
        metricsUpdater.register(supervisor);
        return supervisor;
    }

    @Override
    public void deconstruct() {
        metricsUpdater.stop();
    }
}
