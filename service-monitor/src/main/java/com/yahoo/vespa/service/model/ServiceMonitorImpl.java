// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.model;

import com.google.inject.Inject;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Timer;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.service.duper.DuperModelManager;
import com.yahoo.vespa.service.manager.UnionMonitorManager;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceMonitor;

public class ServiceMonitorImpl implements ServiceMonitor {

    private final ServiceModelCache serviceModelProvider;

    @Inject
    public ServiceMonitorImpl(DuperModelManager duperModelManager,
                              UnionMonitorManager monitorManager,
                              Metric metric,
                              Timer timer,
                              Zone zone,
                              FlagSource flagSource) {
        duperModelManager.registerListener(monitorManager);

        ServiceModelProvider uncachedServiceModelProvider = new ServiceModelProvider(
                monitorManager,
                new ServiceMonitorMetrics(metric, timer),
                duperModelManager,
                new ModelGenerator(),
                zone);
        boolean cache = Flags.SERVICE_MODEL_CACHE.bindTo(flagSource).value();
        serviceModelProvider = new ServiceModelCache(uncachedServiceModelProvider, timer, cache);
    }

    @Override
    public ServiceModel getServiceModelSnapshot() {
        return serviceModelProvider.get();
    }

}
