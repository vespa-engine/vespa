// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.google.inject.Inject;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;
import com.yahoo.vespa.service.monitor.SlobrokAndConfigIntersector;

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

    private final SlobrokAndConfigIntersector slobrokAndConfigIntersector;

    @Inject
    public ServiceMonitorInstanceLookupService(SlobrokAndConfigIntersector slobrokAndConfigIntersector) {
        this.slobrokAndConfigIntersector = slobrokAndConfigIntersector;
    }

    @Override
    public Optional<ApplicationInstance<ServiceMonitorStatus>> findInstanceById(ApplicationInstanceReference applicationInstanceReference) {
        Map<ApplicationInstanceReference, ApplicationInstance<ServiceMonitorStatus>> instanceMap
                = slobrokAndConfigIntersector.queryStatusOfAllApplicationInstances();
        return Optional.ofNullable(instanceMap.get(applicationInstanceReference));
    }

    @Override
    public Optional<ApplicationInstance<ServiceMonitorStatus>> findInstanceByHost(HostName hostName) {
        Map<ApplicationInstanceReference, ApplicationInstance<ServiceMonitorStatus>> instanceMap 
                = slobrokAndConfigIntersector.queryStatusOfAllApplicationInstances();
        List<ApplicationInstance<ServiceMonitorStatus>> applicationInstancesUsingHost = instanceMap.entrySet().stream()
                .filter(entry -> applicationInstanceUsesHost(entry.getValue(), hostName))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
        if (applicationInstancesUsingHost.isEmpty()) {
            return Optional.empty();
        }
        if (applicationInstancesUsingHost.size() > 1) {
            throw new AssertionError(
                    "Major assumption broken: Multiple application instances contain host " + hostName.s()
                            + ": " + applicationInstancesUsingHost);
        }
        return Optional.of(applicationInstancesUsingHost.get(0));
    }

    @Override
    public Set<ApplicationInstanceReference> knownInstances() {
        return slobrokAndConfigIntersector.queryStatusOfAllApplicationInstances().keySet();
    }

    private static boolean applicationInstanceUsesHost(ApplicationInstance<ServiceMonitorStatus> applicationInstance,
                                                       HostName hostName) {
        return applicationInstance.serviceClusters().stream()
                .anyMatch(serviceCluster ->
                        serviceCluster.serviceInstances().stream()
                                .anyMatch(serviceInstance ->
                                        serviceInstance.hostName().equals(hostName)));
    }

}
