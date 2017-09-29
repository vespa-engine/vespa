// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ServiceMonitorImpl implements ServiceMonitor {
    private static final Logger logger = Logger.getLogger(ServiceMonitorImpl.class.getName());

    private final Zone zone;
    private final List<String> configServerHostnames;
    private final SlobrokMonitor2 slobrokMonitor = new SlobrokMonitor2();
    private final SuperModelListenerImpl superModelListener =
            new SuperModelListenerImpl(slobrokMonitor);

    @Inject
    public ServiceMonitorImpl(SuperModelProvider superModelProvider,
                              ConfigserverConfig configserverConfig) {
        this.zone = superModelProvider.getZone();
        this.configServerHostnames = configserverConfig.zookeeperserver().stream()
                .map(server -> server.hostname())
                .collect(Collectors.toList());
        superModelListener.start(superModelProvider);
    }

    @Override
    public Map<ApplicationInstanceReference,
            ApplicationInstance<ServiceMonitorStatus>> queryStatusOfAllApplicationInstances() {
        // If we ever need to optimize this method, then consider reusing ServiceModel snapshots
        // for up to X ms.
        ServiceModel serviceModel =
                superModelListener.createServiceModelSnapshot(zone, configServerHostnames);
        return serviceModel.getAllApplicationInstances();
    }
}
