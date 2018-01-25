// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.status.HostStatus;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class ClusterApiImpl implements ClusterApi {
    private final ApplicationApi applicationApi;
    private final ServiceCluster serviceCluster;
    private final NodeGroup nodeGroup;
    private final Map<HostName, HostStatus> hostStatusMap;
    private final ClusterControllerClientFactory clusterControllerClientFactory;
    private final Set<ServiceInstance> servicesInGroup;
    private final Set<ServiceInstance> servicesDownInGroup;
    private final Set<ServiceInstance> servicesNotInGroup;
    private final Set<ServiceInstance> servicesDownAndNotInGroup;

    public ClusterApiImpl(ApplicationApi applicationApi,
                          ServiceCluster serviceCluster,
                          NodeGroup nodeGroup,
                          Map<HostName, HostStatus> hostStatusMap,
                          ClusterControllerClientFactory clusterControllerClientFactory) {
        this.applicationApi = applicationApi;
        this.serviceCluster = serviceCluster;
        this.nodeGroup = nodeGroup;
        this.hostStatusMap = hostStatusMap;
        this.clusterControllerClientFactory = clusterControllerClientFactory;

        Map<Boolean, Set<ServiceInstance>> serviceInstancesByLocality =
                serviceCluster.serviceInstances().stream()
                        .collect(
                                Collectors.groupingBy(
                                        instance -> nodeGroup.contains(instance.hostName()),
                                        Collectors.toSet()));
        servicesInGroup = serviceInstancesByLocality.getOrDefault(true, Collections.emptySet());
        servicesNotInGroup = serviceInstancesByLocality.getOrDefault(false, Collections.emptySet());

        servicesDownInGroup = servicesInGroup.stream().filter(this::serviceEffectivelyDown).collect(Collectors.toSet());
        servicesDownAndNotInGroup = servicesNotInGroup.stream().filter(this::serviceEffectivelyDown).collect(Collectors.toSet());
    }

    @Override
    public NodeGroup getNodeGroup() {
        return nodeGroup;
    }

    @Override
    public ClusterId clusterId() {
        return serviceCluster.clusterId();
    }

    @Override
    public ServiceType serviceType() {
        return serviceCluster.serviceType();
    }

    @Override
    public boolean isStorageCluster() {
        return VespaModelUtil.isStorage(serviceCluster);
    }

    @Override
    public ApplicationApi getApplication() {
        return applicationApi;
    }

    @Override
    public boolean noServicesInGroupIsUp() {
        return servicesDownInGroup.size() == servicesInGroup.size();
    }

    @Override
    public boolean noServicesOutsideGroupIsDown() {
        return servicesDownAndNotInGroup.size() == 0;
    }

    @Override
    public int percentageOfServicesDown() {
        int numberOfServicesDown = servicesDownAndNotInGroup.size() + servicesDownInGroup.size();
        return numberOfServicesDown * 100 / serviceCluster.serviceInstances().size();
    }

    @Override
    public int percentageOfServicesDownIfGroupIsAllowedToBeDown() {
        int numberOfServicesDown = servicesDownAndNotInGroup.size() + servicesInGroup.size();
        return numberOfServicesDown * 100 / serviceCluster.serviceInstances().size();
    }

    @Override
    public String servicesDownAndNotInGroupDescription() {
        // Sort these for readability and testing stability
        return servicesDownAndNotInGroup.stream()
                .map(service -> service.toString())
                .sorted()
                .collect(Collectors.toList())
                .toString();
    }

    @Override
    public String nodesAllowedToBeDownNotInGroupDescription() {
        return servicesNotInGroup.stream()
                .map(ServiceInstance::hostName)
                .filter(hostName -> hostStatus(hostName) == HostStatus.ALLOWED_TO_BE_DOWN)
                .sorted()
                .distinct()
                .collect(Collectors.toList())
                .toString();
    }

    private Optional<StorageNode> storageNodeInGroup(
            Predicate<ServiceInstance> storageServicePredicate) {
        if (!VespaModelUtil.isStorage(serviceCluster)) {
            return Optional.empty();
        }

        Set<StorageNode> storageNodes = new HashSet<>();

        for (ServiceInstance serviceInstance : servicesInGroup) {
            if (!storageServicePredicate.test(serviceInstance)) {
                continue;
            }

            HostName hostName = serviceInstance.hostName();
            if (nodeGroup.contains(hostName)) {
                if (storageNodes.contains(hostName)) {
                    throw new IllegalStateException("Found more than 1 storage service instance on " + hostName
                            + ": last service instance is " + serviceInstance.configId()
                            + " in storage cluster " + clusterInfo());
                }

                StorageNode storageNode = new StorageNodeImpl(
                        nodeGroup.getApplication(),
                        clusterId(),
                        serviceInstance,
                        clusterControllerClientFactory);
                storageNodes.add(storageNode);
            }
        }

        if (storageNodes.size() > 1) {
            throw new IllegalStateException("Found more than 1 storage node (" + storageNodes
                    + ") in the same cluster (" + clusterInfo() + ") in the same node group ("
                    + getNodeGroup().toCommaSeparatedString() + "): E.g. suspension of such a setup is not supported "
                    + " by the Cluster Controller and is dangerous w.r.t. data redundancy.");
        }

        return storageNodes.stream().findFirst();
    }

    @Override
    public Optional<StorageNode> storageNodeInGroup() {
        return storageNodeInGroup(serviceInstance-> true);
    }

    @Override
    public Optional<StorageNode> upStorageNodeInGroup() {
        return storageNodeInGroup(serviceInstance-> !serviceEffectivelyDown(serviceInstance));
    }

    @Override
    public String clusterInfo() {
        return "{ clusterId=" + clusterId() + ", serviceType=" + serviceType() + " }";
    }

    private HostStatus hostStatus(HostName hostName) {
        return hostStatusMap.getOrDefault(hostName, HostStatus.NO_REMARKS);
    }

    private boolean serviceEffectivelyDown(ServiceInstance service) {
        if (hostStatus(service.hostName()) == HostStatus.ALLOWED_TO_BE_DOWN) {
            return true;
        }

        if (service.serviceStatus() == ServiceStatus.DOWN) {
            return true;
        }

        return false;
    }
}
