// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Timer;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceMonitor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ServiceMonitorImpl implements ServiceMonitor {
    private final ServiceModelCache serviceModelCache;

    @Inject
    public ServiceMonitorImpl(SuperModelProvider superModelProvider,
                              ConfigserverConfig configserverConfig,
                              SlobrokMonitorManagerImpl slobrokMonitorManager,
                              HealthMonitorManager healthMonitorManager,
                              Metric metric,
                              Timer timer) {
        Zone zone = superModelProvider.getZone();
        List<String> configServerHosts = toConfigServerList(configserverConfig);
        ServiceMonitorMetrics metrics = new ServiceMonitorMetrics(metric, timer);

        UnionMonitorManager monitorManager = new UnionMonitorManager(
                slobrokMonitorManager,
                healthMonitorManager,
                configserverConfig);

        SuperModelListenerImpl superModelListener = new SuperModelListenerImpl(
                monitorManager,
                metrics,
                new ModelGenerator(),
                zone,
                configServerHosts);
        superModelListener.start(superModelProvider);
        serviceModelCache = new ServiceModelCache(superModelListener, timer);
    }

    private List<String> toConfigServerList(ConfigserverConfig configserverConfig) {
        if (configserverConfig.multitenant()) {
            return configserverConfig.zookeeperserver().stream()
                    .map(ConfigserverConfig.Zookeeperserver::hostname)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    @Override
    public Map<ApplicationInstanceReference, ApplicationInstance> getAllApplicationInstances() {
        return serviceModelCache.get().getAllApplicationInstances();
    }

    @Override
    public ServiceModel getServiceModelSnapshot() {
        return serviceModelCache.get();
    }
}
