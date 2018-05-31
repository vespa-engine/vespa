// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.health;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;
import com.yahoo.vespa.service.monitor.application.ApplicationInstanceGenerator;
import com.yahoo.vespa.service.monitor.internal.ServiceId;

import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Responsible for monitoring a whole application using /state/v1/health.
 *
 * @author hakon
 */
public class ApplicationHealthMonitor implements ServiceStatusProvider, AutoCloseable {
    private final Map<ServiceId, HealthMonitor> healthMonitors;

    public static ApplicationHealthMonitor startMonitoring(
            ApplicationInfo application,
            ServiceIdentityProvider identityProvider) {
        return new ApplicationHealthMonitor(makeHealthMonitors(application, identityProvider));
    }

    private ApplicationHealthMonitor(Map<ServiceId, HealthMonitor> healthMonitors) {
        this.healthMonitors = healthMonitors;
    }

    @Override
    public ServiceStatus getStatus(ApplicationId applicationId,
                                   ClusterId clusterId,
                                   ServiceType serviceType,
                                   ConfigId configId) {
        ServiceId serviceId = new ServiceId(applicationId, clusterId, serviceType, configId);
        HealthMonitor monitor = healthMonitors.get(serviceId);
        if (monitor == null) {
            return ServiceStatus.NOT_CHECKED;
        }

        return monitor.getStatus();
    }

    @Override
    public void close() {
        healthMonitors.values().forEach(HealthMonitor::close);
        healthMonitors.clear();
    }

    private static Map<ServiceId, HealthMonitor> makeHealthMonitors(
            ApplicationInfo application,
            ServiceIdentityProvider identityProvider) {
        Map<ServiceId, HealthMonitor> healthMonitors = new HashMap<>();
        for (HostInfo hostInfo : application.getModel().getHosts()) {
            for (ServiceInfo serviceInfo : hostInfo.getServices()) {
                for (PortInfo portInfo : serviceInfo.getPorts()) {
                    maybeCreateHealthMonitor(
                            application,
                            hostInfo,
                            serviceInfo,
                            portInfo,
                            identityProvider)
                            .ifPresent(healthMonitor -> healthMonitors.put(
                                    ApplicationInstanceGenerator.getServiceId(application, serviceInfo),
                                    healthMonitor));
                }
            }
        }
        return healthMonitors;
    }

    private static Optional<HealthMonitor> maybeCreateHealthMonitor(
            ApplicationInfo applicationInfo,
            HostInfo hostInfo,
            ServiceInfo serviceInfo,
            PortInfo portInfo,
            ServiceIdentityProvider identityProvider) {
        Collection<String> portTags = portInfo.getTags();
        if (portTags.contains("STATE")) {
            if (portTags.contains("HTTPS")) {
                URL url = uncheck(() -> new URL(
                        "https",
                        hostInfo.getHostname(),
                        portInfo.getPort(),
                        "/state/v1/health"));
                // todo: get hostname verifier
                // "vespa.vespa[.cd].provider_%s_%s" from AthenzProviderServiceConfig
                // new AthenzIdentityVerifier(Collections.singleton("vespa.vespa[.cd].provider_%s_%s"));
                // HealthEndpoint healthEndpoint = HealthEndpoint.forHttps(...);
                // HealthMonitor healthMonitor = new HealthMonitor(url, identityProvider, hostnameVerifier);
                // healthMonitor.startMonitoring()
                return Optional.empty();
            }
        }

        return Optional.empty();
    }
}
