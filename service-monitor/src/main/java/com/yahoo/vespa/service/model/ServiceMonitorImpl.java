// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.model;

import com.google.inject.Inject;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Timer;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.service.duper.DuperModelManager;
import com.yahoo.vespa.service.manager.UnionMonitorManager;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceMonitor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ServiceMonitorImpl implements ServiceMonitor {

    private final ServiceMonitor delegate;

    @Inject
    public ServiceMonitorImpl(DuperModelManager duperModelManager,
                              UnionMonitorManager monitorManager,
                              Metric metric,
                              Timer timer,
                              Zone zone,
                              FlagSource flagSource) {
        duperModelManager.registerListener(monitorManager);

        ServiceMonitor serviceMonitor = new ServiceModelProvider(
                monitorManager,
                new ServiceMonitorMetrics(metric, timer),
                duperModelManager,
                new ModelGenerator(zone),
                zone);

        if (Flags.SERVICE_MODEL_CACHE.bindTo(flagSource).value()) {
            delegate = new ServiceModelCache(serviceMonitor::getServiceModelSnapshot, timer);
        } else {
            delegate = serviceMonitor;
        }
    }

    @Override
    public ServiceModel getServiceModelSnapshot() {
        return delegate.getServiceModelSnapshot();
    }

    @Override
    public Set<ApplicationInstanceReference> getAllApplicationInstanceReferences() {
        return delegate.getAllApplicationInstanceReferences();
    }

    @Override
    public Optional<ApplicationInstance> getApplication(HostName hostname) {
        return delegate.getApplication(hostname);
    }

    @Override
    public Optional<ApplicationInstance> getApplication(ApplicationInstanceReference reference) {
        return delegate.getApplication(reference);
    }

    @Override
    public Optional<ApplicationInstance> getApplicationNarrowedTo(HostName hostname) {
        return delegate.getApplicationNarrowedTo(hostname);
    }

    @Override
    public Map<HostName, List<ServiceInstance>> getServicesByHostname() {
        return delegate.getServicesByHostname();
    }
}
