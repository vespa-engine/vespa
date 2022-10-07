// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.service.monitor.ServiceId;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Responsible for monitoring a whole application using /state/v1/health.
 *
 * @author hakon
 */
class ApplicationHealthMonitor implements ServiceStatusProvider, AutoCloseable {

    private final ApplicationId applicationId;
    private final StateV1HealthModel healthModel;

    // Guards against concurrent access to monitors field w/objects in 1. monitor() (called from e.g. DuperModel),
    // 2. getStatus() called from caching layer and Orchestrator above, and 3. REST calls to getAllServiceStatuses().
    private final Object guard = new Object();
    private final Map<ServiceId, HealthMonitor> monitors = new HashMap<>();

    ApplicationHealthMonitor(ApplicationId applicationId, StateV1HealthModel healthModel) {
        this.applicationId = applicationId;
        this.healthModel = healthModel;
    }

    void monitor(ApplicationInfo applicationInfo) {
        if (!applicationInfo.getApplicationId().equals(applicationId)) {
            throw new IllegalArgumentException("Monitors " + applicationId + " but was asked to monitor " + applicationInfo.getApplicationId());
        }

        Map<ServiceId, HealthEndpoint> endpoints = healthModel.extractHealthEndpoints(applicationInfo);

        synchronized (guard) {
            // Remove obsolete monitors
            Set<ServiceId> removed = new HashSet<>(monitors.keySet());
            removed.removeAll(endpoints.keySet());
            removed.stream().map(monitors::remove).forEach(HealthMonitor::close);

            // Add new monitors.
            endpoints.forEach((serviceId, endpoint) -> monitors.computeIfAbsent(serviceId, ignoredId -> endpoint.startMonitoring()));
        }
    }

    @Override
    public ServiceStatusInfo getStatus(ApplicationId applicationId,
                                       ClusterId clusterId,
                                       ServiceType serviceType,
                                       ConfigId configId) {
        ServiceId serviceId = new ServiceId(applicationId, clusterId, serviceType, configId);

        synchronized (guard) {
            HealthMonitor monitor = monitors.get(serviceId);
            if (monitor == null) {
                return new ServiceStatusInfo(ServiceStatus.NOT_CHECKED);
            }

            return monitor.getStatus();
        }
    }

    public Map<ServiceId, ServiceStatusInfo> getAllServiceStatuses() {
        synchronized (guard) {
            return monitors.entrySet().stream().collect(Collectors.toMap(
                    entry -> entry.getKey(),
                    entry -> entry.getValue().getStatus()));
        }
    }

    @Override
    public void close() {
        synchronized (guard) {
            monitors.values().forEach(HealthMonitor::close);
            monitors.clear();
        }
    }

}
