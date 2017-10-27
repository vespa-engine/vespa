// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Timer;
import com.yahoo.vespa.service.monitor.internal.ServiceMonitorImpl;
import com.yahoo.vespa.service.monitor.internal.SlobrokMonitorManagerImpl;

public class ServiceMonitorProvider implements Provider<ServiceMonitor> {
    private final ServiceMonitorImpl serviceMonitor;

    @Inject
    public ServiceMonitorProvider(SuperModelProvider superModelProvider,
                                  ConfigserverConfig configserverConfig,
                                  SlobrokMonitorManagerImpl slobrokMonitorManager,
                                  Metric metric,
                                  Timer timer) {
        serviceMonitor = new ServiceMonitorImpl(
                superModelProvider,
                configserverConfig,
                slobrokMonitorManager,
                metric,
                timer);
    }
    @Override
    public ServiceMonitor get() {
        return serviceMonitor;
    }

    @Override
    public void deconstruct() {}
}
