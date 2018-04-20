// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.provision.ApplicationId;
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
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;
import com.yahoo.vespa.service.monitor.application.ConfigServerApplication;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Util to convert SuperModel to ServiceModel and application model classes
 */
public class ModelGenerator {
    public static final String CLUSTER_ID_PROPERTY_NAME = "clustername";

    /**
     * Create service model based primarily on super model.
     *
     * If the configServerhosts is non-empty, a config server application is added.
     */
    ServiceModel toServiceModel(
            SuperModel superModel,
            Zone zone,
            List<String> configServerHosts,
            ServiceStatusProvider serviceStatusProvider) {
        Map<ApplicationInstanceReference, ApplicationInstance> applicationInstances = new HashMap<>();

        for (ApplicationInfo applicationInfo : superModel.getAllApplicationInfos()) {

            ApplicationInstance applicationInstance = toApplicationInstance(
                    applicationInfo,
                    zone,
                    serviceStatusProvider);
            applicationInstances.put(applicationInstance.reference(), applicationInstance);
        }

        // The config server is part of the service model (but not super model)
        if (!configServerHosts.isEmpty()) {
            ConfigServerApplication configServerApplication = ConfigServerApplication.CONFIG_SERVER_APPLICATION;
            ApplicationInstance configServerApplicationInstance =
                    configServerApplication.toApplicationInstance(configServerHosts);
            applicationInstances.put(configServerApplicationInstance.reference(), configServerApplicationInstance);
        }

        return new ServiceModel(applicationInstances);
    }

    ApplicationInstance toApplicationInstance(
            ApplicationInfo applicationInfo,
            Zone zone,
            ServiceStatusProvider serviceStatusProvider) {
        Map<ServiceClusterKey, Set<ServiceInstance>> groupedServiceInstances = new HashMap<>();

        for (HostInfo host : applicationInfo.getModel().getHosts()) {
            HostName hostName = new HostName(host.getHostname());
            for (ServiceInfo serviceInfo : host.getServices()) {
                ServiceClusterKey serviceClusterKey = toServiceClusterKey(serviceInfo);
                ServiceInstance serviceInstance =
                        toServiceInstance(
                                applicationInfo.getApplicationId(),
                                serviceClusterKey.clusterId(),
                                serviceInfo,
                                hostName,
                                serviceStatusProvider);

                if (!groupedServiceInstances.containsKey(serviceClusterKey)) {
                    groupedServiceInstances.put(serviceClusterKey, new HashSet<>());
                }
                groupedServiceInstances.get(serviceClusterKey).add(serviceInstance);
            }
        }

        Set<ServiceCluster> serviceClusters = groupedServiceInstances.entrySet().stream()
                .map(entry -> new ServiceCluster(
                        entry.getKey().clusterId(),
                        entry.getKey().serviceType(),
                        entry.getValue()))
                .collect(Collectors.toSet());

        ApplicationInstance applicationInstance = new ApplicationInstance(
                new TenantId(applicationInfo.getApplicationId().tenant().toString()),
                toApplicationInstanceId(applicationInfo, zone),
                serviceClusters);

        // Fill back-references
        for (ServiceCluster serviceCluster : applicationInstance.serviceClusters()) {
            serviceCluster.setApplicationInstance(applicationInstance);
            for (ServiceInstance serviceInstance : serviceCluster.serviceInstances()) {
                serviceInstance.setServiceCluster(serviceCluster);
            }
        }

        return applicationInstance;
    }

    static ClusterId getClusterId(ServiceInfo serviceInfo) {
        return new ClusterId(serviceInfo.getProperty(CLUSTER_ID_PROPERTY_NAME).orElse(""));
    }

    private ServiceClusterKey toServiceClusterKey(ServiceInfo serviceInfo) {
        ClusterId clusterId = getClusterId(serviceInfo);
        ServiceType serviceType = toServiceType(serviceInfo);
        return new ServiceClusterKey(clusterId, serviceType);
    }

    private ServiceInstance toServiceInstance(
            ApplicationId applicationId,
            ClusterId clusterId,
            ServiceInfo serviceInfo,
            HostName hostName,
            ServiceStatusProvider serviceStatusProvider) {
        ConfigId configId = new ConfigId(serviceInfo.getConfigId());

        ServiceStatus status = serviceStatusProvider.getStatus(
                applicationId,
                clusterId,
                toServiceType(serviceInfo), configId);

        return new ServiceInstance(configId, hostName, status);
    }

    private ApplicationInstanceId toApplicationInstanceId(ApplicationInfo applicationInfo, Zone zone) {
        return new ApplicationInstanceId(String.format("%s:%s:%s:%s",
                applicationInfo.getApplicationId().application().value(),
                zone.environment().value(),
                zone.region().value(),
                applicationInfo.getApplicationId().instance().value()));
    }

    private ServiceType toServiceType(ServiceInfo serviceInfo) {
        return new ServiceType(serviceInfo.getServiceType());
    }
}
