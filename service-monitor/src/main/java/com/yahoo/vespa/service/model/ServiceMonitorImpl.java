// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.model;

import com.google.inject.Inject;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Timer;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.service.duper.DuperModelManager;
import com.yahoo.vespa.service.health.HealthMonitorManager;
import com.yahoo.vespa.service.manager.UnionMonitorManager;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import com.yahoo.vespa.service.slobrok.SlobrokMonitorManagerImpl;

import java.util.Map;

public class ServiceMonitorImpl implements ServiceMonitor {
    private final ServiceModelCache serviceModelProvider;

    @Inject
    public ServiceMonitorImpl(DuperModelManager duperModelManager,
                              UnionMonitorManager monitorManager,
                              Metric metric,
                              Timer timer,
                              Zone zone) {
        duperModelManager.registerListener(monitorManager);

        ServiceModelProvider uncachedServiceModelProvider = new ServiceModelProvider(
                monitorManager,
                new ServiceMonitorMetrics(metric, timer),
                duperModelManager,
                new ModelGenerator(),
                zone);
        serviceModelProvider = new ServiceModelCache(uncachedServiceModelProvider, timer);
    }

    @Override
    public Map<ApplicationInstanceReference, ApplicationInstance> getAllApplicationInstances() {
        return serviceModelProvider.get().getAllApplicationInstances();
    }

    @Override
    public ServiceModel getServiceModelSnapshot() {
        return serviceModelProvider.get();
    }
}
