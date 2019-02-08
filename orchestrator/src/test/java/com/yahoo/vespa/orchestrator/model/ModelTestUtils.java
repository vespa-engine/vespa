// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.orchestrator.OrchestrationException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.orchestrator.ServiceMonitorInstanceLookupService;
import com.yahoo.vespa.orchestrator.config.OrchestratorConfig;
import com.yahoo.vespa.orchestrator.config.OrchestratorConfig.Builder;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactoryMock;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.MutableStatusRegistry;
import com.yahoo.vespa.orchestrator.status.StatusService;
import com.yahoo.vespa.orchestrator.status.ZookeeperStatusService;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.yolean.Exceptions;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ModelTestUtils {

    private final Map<ApplicationInstanceReference, ApplicationInstance> applications = new HashMap<>();
    private final ClusterControllerClientFactory clusterControllerClientFactory = new ClusterControllerClientFactoryMock();
    private final Map<HostName, HostStatus> hostStatusMap = new HashMap<>();
    private final StatusService statusService = new ZookeeperStatusService(new MockCurator());
    private final Orchestrator orchestrator = new OrchestratorImpl(clusterControllerClientFactory,
                                                                   statusService,
                                                                   new OrchestratorConfig(new Builder()),
                                                                   new ServiceMonitorInstanceLookupService(() -> new ServiceModel(applications)));

    Map<HostName, HostStatus> getHostStatusMap() {
        return hostStatusMap;
    }

    HostName createNode(String name, HostStatus hostStatus) {
        return createNode(new HostName(name), hostStatus);
    }

    HostName createNode(HostName hostName, HostStatus hostStatus) {
        hostStatusMap.put(hostName, hostStatus);
        try {
            orchestrator.setNodeStatus(hostName, hostStatus);
        }
        catch (OrchestrationException e) {
            throw new AssertionError("Host '" + hostName + "' not owned by any application — please assign it first: " +
                                             Exceptions.toMessageString(e));
        }
        return hostName;
    }

    ApplicationApiImpl createApplicationApiImpl(
            ApplicationInstance applicationInstance,
            HostName... hostnames) {
        NodeGroup nodeGroup = new NodeGroup(applicationInstance, hostnames);
        MutableStatusRegistry registry = statusService.lockApplicationInstance_forCurrentThreadOnly(
                OrchestratorContext.createContextForSingleAppOp(Clock.systemUTC()),
                applicationInstance.reference());
        return new ApplicationApiImpl(nodeGroup, registry, statusService, clusterControllerClientFactory);
    }

    ApplicationInstance createApplicationInstance(
            List<ServiceCluster> serviceClusters) {
        Set<ServiceCluster> serviceClusterSet = new HashSet<>(serviceClusters);

        ApplicationInstance application = new ApplicationInstance(
                new TenantId("tenant"),
                new ApplicationInstanceId("application-name:foo:bar:default"),
                serviceClusterSet);
        applications.put(application.reference(), application);
        return application;
    }

    ServiceCluster createServiceCluster(
            String clusterId,
            ServiceType serviceType,
            List<ServiceInstance> serviceInstances) {
        Set<ServiceInstance> serviceInstanceSet = new HashSet<>(serviceInstances);

        return new ServiceCluster(
                new ClusterId(clusterId),
                serviceType,
                serviceInstanceSet);
    }

    ServiceInstance createServiceInstance(
            String configId,
            HostName hostName,
            ServiceStatus status) {
        return new ServiceInstance(
                new ConfigId(configId),
                hostName,
                status);
    }

    ClusterControllerClientFactory getClusterControllerClientFactory() {
        return clusterControllerClientFactory;
    }

}
