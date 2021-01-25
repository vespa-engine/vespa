// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.test.TestTimer;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.orchestrator.DummyAntiServiceMonitor;
import com.yahoo.vespa.orchestrator.OrchestrationException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactoryMock;
import com.yahoo.vespa.orchestrator.policy.HostedVespaClusterPolicy;
import com.yahoo.vespa.orchestrator.policy.HostedVespaPolicy;
import com.yahoo.vespa.orchestrator.status.ApplicationLock;
import com.yahoo.vespa.orchestrator.status.HostInfo;
import com.yahoo.vespa.orchestrator.status.HostInfos;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.StatusService;
import com.yahoo.vespa.orchestrator.status.ZkStatusService;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import com.yahoo.yolean.Exceptions;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;

/**
 * @author hakonhall
 */
class ModelTestUtils {

    public static final int NUMBER_OF_CONFIG_SERVERS = 3;

    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();
    private final Map<ApplicationInstanceReference, ApplicationInstance> applications = new HashMap<>();
    private final ClusterControllerClientFactory clusterControllerClientFactory = new ClusterControllerClientFactoryMock();
    private final Map<HostName, HostStatus> hostStatusMap = new HashMap<>();
    private final ServiceMonitor serviceMonitor = () -> new ServiceModel(applications);
    private final StatusService statusService = new ZkStatusService(
            new MockCurator(),
            mock(Metric.class),
            new TestTimer(),
            new DummyAntiServiceMonitor());
    private final Orchestrator orchestrator = new OrchestratorImpl(new HostedVespaPolicy(new HostedVespaClusterPolicy(flagSource), clusterControllerClientFactory, applicationApiFactory()),
                                                                   clusterControllerClientFactory,
                                                                   statusService,
                                                                   serviceMonitor,
                                                                   0,
                                                                   new ManualClock(),
                                                                   applicationApiFactory(),
                                                                   flagSource);
    private final ManualClock clock = new ManualClock();

    ApplicationApiFactory applicationApiFactory() {
        return new ApplicationApiFactory(NUMBER_OF_CONFIG_SERVERS, clock);
    }

    HostInfos getHostInfos() {
        Instant now = Instant.now();

        Map<HostName, HostInfo> hostInfosMap = hostStatusMap.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey(),
                        entry -> {
                            HostStatus status = entry.getValue();
                            if (status == HostStatus.NO_REMARKS) {
                                return HostInfo.createNoRemarks();
                            } else {
                                return HostInfo.createSuspended(status, now);
                            }
                        }
                ));

        return new HostInfos(hostInfosMap);
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

    ScopedApplicationApi createScopedApplicationApi(
            ApplicationInstance applicationInstance,
            HostName... hostnames) {
        NodeGroup nodeGroup = new NodeGroup(applicationInstance, hostnames);
        ApplicationLock lock = statusService.lockApplication(
                OrchestratorContext.createContextForSingleAppOp(Clock.systemUTC()),
                applicationInstance.reference());
        return new ScopedApplicationApi(
                applicationApiFactory().create(nodeGroup, lock, clusterControllerClientFactory),
                lock);
    }

    ApplicationInstance createApplicationInstance(
            List<ServiceCluster> serviceClusters) {
        Set<ServiceCluster> serviceClusterSet = new HashSet<>(serviceClusters);

        ApplicationInstance application = new ApplicationInstance(
                new TenantId("tenant"),
                new ApplicationInstanceId("application-name:foo:bar:default"),
                serviceClusterSet);
        applications.put(application.reference(), application);

        serviceClusters.forEach(cluster -> cluster.setApplicationInstance(application));

        return application;
    }

    ServiceCluster createServiceCluster(
            String clusterId,
            ServiceType serviceType,
            List<ServiceInstance> serviceInstances) {
        Set<ServiceInstance> serviceInstanceSet = new HashSet<>(serviceInstances);
        var cluster = new ServiceCluster(new ClusterId(clusterId), serviceType, serviceInstanceSet);
        for (var service : serviceInstanceSet) {
            service.setServiceCluster(cluster);
        }
        return cluster;
    }

    ServiceInstance createServiceInstance(String configId, HostName hostName, ServiceStatus status) {
        return new ServiceInstance(new ConfigId(configId), hostName, status);
    }

    ServiceInstance createServiceInstance(String configId, HostName hostName, ServiceStatusInfo statusInfo) {
        return new ServiceInstance(new ConfigId(configId), hostName, statusInfo);
    }

    ClusterControllerClientFactory getClusterControllerClientFactory() {
        return clusterControllerClientFactory;
    }

}
