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
        Map<ApplicationInstanceReference, ApplicationInstance> instanceMap
                = serviceMonitor.getAllApplicationInstances();
        return Optional.ofNullable(instanceMap.get(applicationInstanceReference));
    }

    @Override
    public Optional<ApplicationInstance> findInstanceByHost(HostName hostName) {
        Map<ApplicationInstanceReference, ApplicationInstance> instanceMap 
                = serviceMonitor.getAllApplicationInstances();
        List<ApplicationInstance> applicationInstancesUsingHost = instanceMap.entrySet().stream()
                .filter(entry -> applicationInstanceUsesHost(entry.getValue(), hostName))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
        if (applicationInstancesUsingHost.isEmpty()) {
            return Optional.empty();
        }
        if (applicationInstancesUsingHost.size() > 1) {
            throw new IllegalStateException(
                    "Major assumption broken: Multiple application instances contain host " + hostName.s()
                            + ": " + applicationInstancesUsingHost);
        }
        return Optional.of(applicationInstancesUsingHost.get(0));
    }

    @Override
    public Set<ApplicationInstanceReference> knownInstances() {
        return serviceMonitor.getAllApplicationInstances().keySet();
    }

    private static boolean applicationInstanceUsesHost(ApplicationInstance applicationInstance,
                                                       HostName hostName) {
        return applicationInstance.serviceClusters().stream()
                .anyMatch(serviceCluster ->
                        serviceCluster.serviceInstances().stream()
                                .anyMatch(serviceInstance ->
                                        serviceInstance.hostName().equals(hostName)));
    }

}
