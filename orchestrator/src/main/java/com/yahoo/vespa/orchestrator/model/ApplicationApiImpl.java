// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.MutableStatusRegistry;
import com.yahoo.vespa.orchestrator.status.ReadOnlyStatusRegistry;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.vespa.orchestrator.OrchestratorUtil.getHostsUsedByApplicationInstance;

public class ApplicationApiImpl implements ApplicationApi {
    private final ApplicationInstance<ServiceMonitorStatus> applicationInstance;
    private final NodeGroup nodeGroup;
    private final MutableStatusRegistry hostStatusService;
    private final List<ClusterApi> clusterInOrder;
    private final ClusterControllerClientFactory clusterControllerClientFactory;
    private final Map<HostName, HostStatus> hostStatusMap;

    public ApplicationApiImpl(NodeGroup nodeGroup,
                              MutableStatusRegistry hostStatusService,
                              ClusterControllerClientFactory clusterControllerClientFactory) {
        this.applicationInstance = nodeGroup.getApplication();
        this.nodeGroup = nodeGroup;
        this.hostStatusService = hostStatusService;
        this.hostStatusMap = createHostStatusMap(
                getHostsUsedByApplicationInstance(applicationInstance),
                hostStatusService);
        this.clusterInOrder = makeClustersInOrder(nodeGroup, hostStatusMap, clusterControllerClientFactory);
        this.clusterControllerClientFactory = clusterControllerClientFactory;
    }

    @Override
    public String applicationInfo() {
        return applicationInstance.reference().toString();
    }

    private static Map<HostName, HostStatus> createHostStatusMap(Collection<HostName> hosts,
                                                                 ReadOnlyStatusRegistry hostStatusService) {
        return hosts.stream()
                .collect(Collectors.toMap(
                        hostName -> hostName,
                        hostName -> hostStatusService.getHostStatus(hostName)));
    }

    private HostStatus getHostStatus(HostName hostName) {
        return hostStatusMap.getOrDefault(hostName, HostStatus.NO_REMARKS);
    }

    @Override
    public List<ClusterApi> getClusters() {
        return clusterInOrder;
    }

    @Override
    public List<StorageNode> getStorageNodesAllowedToBeDownInGroupInReverseClusterOrder() {
        return getStorageNodesInGroupInClusterOrder().stream()
                .filter(storageNode -> getHostStatus(storageNode.hostName()) == HostStatus.ALLOWED_TO_BE_DOWN)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    @Override
    public NodeGroup getNodeGroup() {
        return nodeGroup;
    }

    @Override
    public List<StorageNode> getStorageNodesInGroupInClusterOrder() {
        return clusterInOrder.stream()
                .map(ClusterApi::storageNodeInGroup)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public List<StorageNode> getUpStorageNodesInGroupInClusterOrder() {
        return clusterInOrder.stream()
                .map(ClusterApi::upStorageNodeInGroup)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public ApplicationInstanceStatus getApplicationStatus() {
        return hostStatusService.getApplicationInstanceStatus();
    }

    @Override
    public void setHostState(HostName hostName, HostStatus status) {
        hostStatusService.setHostState(hostName, status);
    }

    @Override
    public List<HostName> getNodesInGroupWithStatus(HostStatus status) {
        return nodeGroup.getHostNames().stream()
                .filter(hostName -> getHostStatus(hostName) == status)
                .collect(Collectors.toList());
    }

    private static List<ClusterApi> makeClustersInOrder
            (NodeGroup nodeGroup,
             Map<HostName, HostStatus> hostStatusMap,
             ClusterControllerClientFactory clusterControllerClientFactory) {
        Set<ServiceCluster<ServiceMonitorStatus>> clustersInGroup = getServiceClustersInGroup(nodeGroup);
        return clustersInGroup.stream()
                .map(serviceCluster -> new ClusterApiImpl(
                        serviceCluster,
                        nodeGroup,
                        hostStatusMap,
                        clusterControllerClientFactory))
                .sorted(ApplicationApiImpl::compareClusters)
                .collect(Collectors.toList());
    }

    private static int compareClusters(ClusterApi lhs, ClusterApi rhs) {
        int diff = lhs.serviceType().toString().compareTo(rhs.serviceType().toString());
        if (diff != 0) {
            return diff;
        }

        return lhs.clusterId().toString().compareTo(rhs.clusterId().toString());
    }

    private static Set<ServiceCluster<ServiceMonitorStatus>> getServiceClustersInGroup(NodeGroup nodeGroup) {
        ApplicationInstance<ServiceMonitorStatus> applicationInstance = nodeGroup.getApplication();

        Set<ServiceCluster<ServiceMonitorStatus>> serviceClustersInGroup = new HashSet<>();
        for (ServiceCluster<ServiceMonitorStatus> cluster : applicationInstance.serviceClusters()) {
            for (ServiceInstance<ServiceMonitorStatus> instance : cluster.serviceInstances()) {
                if (nodeGroup.contains(instance.hostName())) {
                    serviceClustersInGroup.add(cluster);
                    break;
                }
            }
        }

        return serviceClustersInGroup;
    }
}
