// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class for generating an ApplicationInstance for the synthesized config server application.
 *
 * @author hakon
 */
public class ConfigServerAppGenerator implements ApplicationInstanceGenerator {
    private final List<String> hostnames;

    public ConfigServerAppGenerator(List<String> hostnames) {
        this.hostnames = hostnames;
    }

    @Override
    public ApplicationInstance makeApplicationInstance(ServiceStatusProvider statusProvider) {
        Set<ServiceInstance> serviceInstances = hostnames.stream()
                .map(hostname -> makeServiceInstance(hostname, statusProvider))
                .collect(Collectors.toSet());

        ServiceCluster serviceCluster = new ServiceCluster(
                ConfigServerApplication.CLUSTER_ID,
                ConfigServerApplication.SERVICE_TYPE,
                serviceInstances);

        Set<ServiceCluster> serviceClusters = new HashSet<>();
        serviceClusters.add(serviceCluster);

        ApplicationInstance applicationInstance = new ApplicationInstance(
                ConfigServerApplication.TENANT_ID,
                ConfigServerApplication.APPLICATION_INSTANCE_ID,
                serviceClusters);

        // Fill back-references
        serviceCluster.setApplicationInstance(applicationInstance);
        for (ServiceInstance serviceInstance : serviceCluster.serviceInstances()) {
            serviceInstance.setServiceCluster(serviceCluster);
        }

        return applicationInstance;
    }

    private ServiceInstance makeServiceInstance(String hostname, ServiceStatusProvider statusProvider) {
        ConfigId configId = new ConfigId(ConfigServerApplication.CONFIG_ID_PREFIX + hostname);
        ServiceStatus status = statusProvider.getStatus(
                ConfigServerApplication.CONFIG_SERVER_APPLICATION.getApplicationId(),
                ConfigServerApplication.CLUSTER_ID,
                ConfigServerApplication.SERVICE_TYPE,
                configId);

        return new ServiceInstance(configId, new HostName(hostname), status);
    }
}
