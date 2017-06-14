// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.MutableStatusRegistry;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModelTestUtils {
    private final MutableStatusRegistry statusRegistry = mock(MutableStatusRegistry.class);
    private final ClusterControllerClientFactory clusterControllerClientFactory = mock(ClusterControllerClientFactory.class);
    private final Map<HostName, HostStatus> hostStatusMap = new HashMap<>();

    ModelTestUtils() {
        when(statusRegistry.getHostStatus(any())).thenReturn(HostStatus.NO_REMARKS);
    }

    Map<HostName, HostStatus> getHostStatusMap() {
        return hostStatusMap;
    }

    HostName createNode(String name, HostStatus hostStatus) {
        HostName hostName = new HostName(name);
        hostStatusMap.put(hostName, hostStatus);
        when(statusRegistry.getHostStatus(hostName)).thenReturn(hostStatus);
        return hostName;
    }

    ApplicationApiImpl createApplicationApiImpl(
            ApplicationInstance<ServiceMonitorStatus> applicationInstance,
            HostName... hostnames) {
        NodeGroup nodeGroup = new NodeGroup(applicationInstance, hostnames);
        return new ApplicationApiImpl(nodeGroup, statusRegistry, clusterControllerClientFactory);
    }

    ApplicationInstance<ServiceMonitorStatus> createApplicationInstance(
            List<ServiceCluster<ServiceMonitorStatus>> serviceClusters) {
        Set<ServiceCluster<ServiceMonitorStatus>> serviceClusterSet = serviceClusters.stream()
                .collect(Collectors.toSet());

        return new ApplicationInstance<>(
                new TenantId("tenant"),
                new ApplicationInstanceId("application-name:foo:bar:default"),
                serviceClusterSet);
    }

    ServiceCluster<ServiceMonitorStatus> createServiceCluster(
            String clusterId,
            ServiceType serviceType,
            List<ServiceInstance<ServiceMonitorStatus>> serviceInstances) {
        Set<ServiceInstance<ServiceMonitorStatus>> serviceInstanceSet = serviceInstances.stream()
                .collect(Collectors.toSet());

        return new ServiceCluster<>(
                new ClusterId(clusterId),
                serviceType,
                serviceInstanceSet);
    }

    ServiceInstance<ServiceMonitorStatus> createServiceInstance(
            String configId,
            HostName hostName,
            ServiceMonitorStatus status) {
        return new ServiceInstance<>(
                new ConfigId(configId),
                hostName,
                status);
    }

    public ClusterControllerClientFactory getClusterControllerClientFactory() {
        return clusterControllerClientFactory;
    }
}
