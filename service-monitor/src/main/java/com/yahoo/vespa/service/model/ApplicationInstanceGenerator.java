// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.model;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceClusterKey;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.service.duper.ConfigServerApplication;
import com.yahoo.vespa.service.monitor.ServiceId;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Class to generate an ApplicationInstance given service status for a standard (deployed) application.
 *
 * @author hakon
 */
public class ApplicationInstanceGenerator {
    public static final String CLUSTER_ID_PROPERTY_NAME = "clustername";

    private final ApplicationInfo applicationInfo;
    private final Zone zone;

    // This is cheating a bit, but we don't expect DuperModel's config server application ID to be different.
    // We do this to avoid passing through the ID through multiple levels.
    private static ApplicationId configServerApplicationId = new ConfigServerApplication().getApplicationId();

    public ApplicationInstanceGenerator(ApplicationInfo applicationInfo, Zone zone) {
        this.applicationInfo = applicationInfo;
        this.zone = zone;
    }

    public ApplicationInstance makeApplicationInstance(ServiceStatusProvider serviceStatusProvider) {
        return makeApplicationInstanceLimitedToHosts(serviceStatusProvider, hostname -> true);
    }

    public ApplicationInstanceReference toApplicationInstanceReference() {
        TenantId tenantId = new TenantId(applicationInfo.getApplicationId().tenant().toString());
        ApplicationInstanceId applicationInstanceId = toApplicationInstanceId(applicationInfo.getApplicationId(), zone);
        return new ApplicationInstanceReference(tenantId, applicationInstanceId);
    }

    public boolean containsHostname(HostName hostname) {
        return applicationInfo.getModel().getHosts().stream()
                .map(HostInfo::getHostname)
                .anyMatch(hostnameString -> Objects.equals(hostnameString, hostname.s()));
    }

    public ApplicationInstance makeApplicationInstanceLimitedTo(
            HostName hostname, ServiceStatusProvider serviceStatusProvider) {
        return makeApplicationInstanceLimitedToHosts(
                serviceStatusProvider, candidateHostname -> candidateHostname.equals(hostname));
    }

    /** Reverse of toApplicationInstanceId, put in this file because it its inverse is. */
    public static ApplicationId toApplicationId(ApplicationInstanceReference reference) {

        String appNameStr = reference.asString();
        String[] appNameParts = appNameStr.split(":");

        // Env, region and instance seems to be optional due to the hardcoded config server app
        // Assume here that first two are tenant and application name.
        if (appNameParts.length == 2) {
            return ApplicationId.from(TenantName.from(appNameParts[0]),
                    ApplicationName.from(appNameParts[1]),
                    InstanceName.defaultName());
        }

        // Other normal application should have 5 parts.
        if (appNameParts.length != 5)  {
            throw new IllegalArgumentException("Application reference not valid (not 5 parts): " + reference);
        }

        return ApplicationId.from(TenantName.from(appNameParts[0]),
                ApplicationName.from(appNameParts[1]),
                InstanceName.from(appNameParts[4]));
    }

    private ApplicationInstance makeApplicationInstanceLimitedToHosts(ServiceStatusProvider serviceStatusProvider,
                                                                      Predicate<HostName> includeHostPredicate) {
        Map<ServiceClusterKey, Set<ServiceInstance>> groupedServiceInstances = new HashMap<>();

        for (HostInfo host : applicationInfo.getModel().getHosts()) {
            HostName hostName = new HostName(host.getHostname());

            if (!includeHostPredicate.test(hostName)) {
                continue;
            }

            for (ServiceInfo serviceInfo : host.getServices()) {
                ServiceClusterKey serviceClusterKey = toServiceClusterKey(serviceInfo);

                ServiceInstance serviceInstance =
                        toServiceInstance(
                                applicationInfo.getApplicationId(),
                                serviceClusterKey.clusterId(),
                                serviceInfo,
                                hostName,
                                serviceStatusProvider);

                groupedServiceInstances.putIfAbsent(serviceClusterKey, new HashSet<>());
                groupedServiceInstances.get(serviceClusterKey).add(serviceInstance);
            }
        }

        Set<ServiceCluster> serviceClusters = groupedServiceInstances.entrySet().stream()
                .map(entry -> new ServiceCluster(
                        entry.getKey().clusterId(),
                        entry.getKey().serviceType(),
                        entry.getValue()))
                .collect(Collectors.toSet());

        ApplicationInstanceReference reference = toApplicationInstanceReference();
        ApplicationInstance applicationInstance = new ApplicationInstance(reference, serviceClusters);

        // Fill back-references
        for (ServiceCluster serviceCluster : applicationInstance.serviceClusters()) {
            serviceCluster.setApplicationInstance(applicationInstance);
            for (ServiceInstance serviceInstance : serviceCluster.serviceInstances()) {
                serviceInstance.setServiceCluster(serviceCluster);
            }
        }

        return applicationInstance;
    }

    private ServiceInstance toServiceInstance(
            ApplicationId applicationId,
            ClusterId clusterId,
            ServiceInfo serviceInfo,
            HostName hostName,
            ServiceStatusProvider serviceStatusProvider) {
        ConfigId configId = toConfigId(serviceInfo);
        ServiceType serviceType = toServiceType(serviceInfo);
        ServiceStatusInfo status = serviceStatusProvider.getStatus(applicationId, clusterId, serviceType, configId);

        return new ServiceInstance(configId, hostName, status);
    }

    private static ApplicationInstanceId toApplicationInstanceId(ApplicationId applicationId, Zone zone) {
        if (applicationId.equals(configServerApplicationId)) {
            // Removing this historical discrepancy would break orchestration during rollout.
            // An alternative may be to use a feature flag and flip it between releases,
            // once that's available.
            return new ApplicationInstanceId(applicationId.application().value());
        } else {
            return new ApplicationInstanceId(String.format("%s:%s:%s:%s",
                    applicationId.application().value(),
                    zone.environment().value(),
                    zone.region().value(),
                    applicationId.instance().value()));
        }
    }

    public static ServiceId getServiceId(ApplicationInfo applicationInfo, ServiceInfo serviceInfo) {
        return new ServiceId(
                applicationInfo.getApplicationId(),
                getClusterId(serviceInfo),
                toServiceType(serviceInfo),
                toConfigId(serviceInfo));
    }

    private static ClusterId getClusterId(ServiceInfo serviceInfo) {
        return new ClusterId(serviceInfo.getProperty(CLUSTER_ID_PROPERTY_NAME).orElse(""));
    }

    private static ServiceClusterKey toServiceClusterKey(ServiceInfo serviceInfo) {
        ClusterId clusterId = getClusterId(serviceInfo);
        ServiceType serviceType = toServiceType(serviceInfo);
        return new ServiceClusterKey(clusterId, serviceType);
    }

    public static ServiceType toServiceType(ServiceInfo serviceInfo) {
        return new ServiceType(serviceInfo.getServiceType());
    }

    private static ConfigId toConfigId(ServiceInfo serviceInfo) {
        return new ConfigId(serviceInfo.getConfigId());
    }
}
