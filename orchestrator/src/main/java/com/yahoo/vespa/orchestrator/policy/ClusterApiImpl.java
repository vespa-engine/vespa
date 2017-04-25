// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.NodeGroup;
import com.yahoo.vespa.orchestrator.VespaModelUtil;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class ClusterApiImpl implements ClusterApi {
    private final ServiceCluster<ServiceMonitorStatus> serviceCluster;
    private final NodeGroup nodeGroup;
    private final Map<HostName, HostStatus> hostStatusMap;
    private final Set<ServiceInstance<ServiceMonitorStatus>> servicesInGroup;
    private final Set<ServiceInstance<ServiceMonitorStatus>> servicesDownInGroup;
    private final Set<ServiceInstance<ServiceMonitorStatus>> servicesNotInGroup;
    private final Set<ServiceInstance<ServiceMonitorStatus>> servicesDownAndNotInGroup;

    public ClusterApiImpl(ServiceCluster<ServiceMonitorStatus> serviceCluster,
                          NodeGroup nodeGroup,
                          Map<HostName, HostStatus> hostStatusMap) {
        this.serviceCluster = serviceCluster;
        this.nodeGroup = nodeGroup;
        this.hostStatusMap = hostStatusMap;

        Map<Boolean, Set<ServiceInstance<ServiceMonitorStatus>>> serviceInstancesByLocality =
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
    public Set<ServiceInstance<ServiceMonitorStatus>> servicesDownAndNotInGroup() {
        return servicesDownAndNotInGroup;
    }

    @Override
    public List<HostName> nodesAllowedToBeDownNotInGroup() {
        return servicesNotInGroup.stream()
                .map(ServiceInstance::hostName)
                .filter(hostName -> hostStatus(hostName) == HostStatus.ALLOWED_TO_BE_DOWN)
                .sorted()
                .distinct()
                .collect(Collectors.toList());
    }

    private Optional<HostName> storageNodeInGroup(
            Predicate<ServiceInstance<ServiceMonitorStatus>> storageServicePredicate) {
        if (!VespaModelUtil.isStorage(serviceCluster)) {
            return Optional.empty();
        }

        Set<HostName> storageNodes = new HashSet<>();

        for (ServiceInstance<ServiceMonitorStatus> serviceInstance : servicesInGroup) {
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

                storageNodes.add(hostName);
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
    public Optional<HostName> storageNodeInGroup() {
        return storageNodeInGroup(serviceInstance-> true);
    }

    @Override
    public Optional<HostName> upStorageNodeInGroup() {
        return storageNodeInGroup(serviceInstance-> !serviceEffectivelyDown(serviceInstance));
    }

    @Override
    public String clusterInfo() {
        return "{ clusterId=" + clusterId() + ", serviceType=" + serviceType() + " }";
    }

    private HostStatus hostStatus(HostName hostName) {
        return hostStatusMap.getOrDefault(hostName, HostStatus.NO_REMARKS);
    }

    private boolean serviceEffectivelyDown(ServiceInstance<ServiceMonitorStatus> service) {
        if (hostStatus(service.hostName()) == HostStatus.ALLOWED_TO_BE_DOWN) {
            return true;
        }

        if (service.serviceStatus() == ServiceMonitorStatus.DOWN) {
            return true;
        }

        return false;
    }
}
