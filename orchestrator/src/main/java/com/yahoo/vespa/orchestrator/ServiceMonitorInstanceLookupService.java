// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.google.inject.Inject;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.service.monitor.ServiceMonitor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Uses slobrok data (a.k.a. heartbeat) to implement {@link InstanceLookupService}.
 *
 * @author bakksjo
 */
public class ServiceMonitorInstanceLookupService implements InstanceLookupService {

    private final ServiceMonitor serviceMonitor;

    @Inject
    public ServiceMonitorInstanceLookupService(ServiceMonitor serviceMonitor) {
        this.serviceMonitor = serviceMonitor;
    }

    @Override
    public Optional<ApplicationInstance> findInstanceById(ApplicationInstanceReference applicationInstanceReference) {
        return serviceMonitor.getServiceModelSnapshot().getApplicationInstance(applicationInstanceReference);
    }

    @Override
    public Optional<ApplicationInstance> findInstanceByHost(HostName hostName) {
        return Optional.ofNullable(serviceMonitor.getServiceModelSnapshot().getApplicationsByHostName().get(hostName));
    }

    @Override
    public Set<ApplicationInstanceReference> knownInstances() {
        return serviceMonitor.getServiceModelSnapshot().getAllApplicationInstances().keySet();
    }

}
