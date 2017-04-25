// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.orchestrator.NodeGroup;
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
    private final Map<HostName, HostStatus> hostStatusMap;

    public ApplicationApiImpl(ApplicationInstance<ServiceMonitorStatus> applicationInstance,
                              NodeGroup nodeGroup,
                              MutableStatusRegistry hostStatusService) {
        this.applicationInstance = applicationInstance;
        this.nodeGroup = nodeGroup;
        this.hostStatusService = hostStatusService;
        this.hostStatusMap = createHostStatusMap(
                getHostsUsedByApplicationInstance(applicationInstance),
                hostStatusService);
        this.clusterInOrder = makeClustersInOrder(nodeGroup, hostStatusMap);
    }

    @Override
    public String applicationInfo() {
        return applicationInstance.reference().toString();
    }

    @Override
    public List<HostName> getNodesInGroupWithNoRemarks() {
        return nodeGroup.getHostNames().stream()
                .filter(hostName -> getHostStatus(hostName) != HostStatus.NO_REMARKS)
                .collect(Collectors.toList());
    }

    private static Map<HostName, HostStatus> createHostStatusMap(Collection<HostName> hosts,
                                                                 ReadOnlyStatusRegistry hostStatusService) {
        return hosts.stream()
                .collect(Collectors.toMap(
                        hostName -> hostName,
                        hostName -> hostStatusService.getHostStatus(hostName)));
    }

    @Override
    public HostStatus getHostStatus(HostName hostName) {
        return hostStatusMap.getOrDefault(hostName, HostStatus.NO_REMARKS);
    }

    @Override
    public List<ClusterApi> getClustersThatAreOnAtLeastOneNodeInGroup() {
        return clusterInOrder;
    }

    @Override
    public List<HostName> getStorageNodesWithNoRemarksInGroupInReverseOrder() {
        return clusterInOrder.stream()
                .map(ClusterApi::storageNodeInGroup)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(hostName -> getHostStatus(hostName) != HostStatus.NO_REMARKS)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    @Override
    public List<HostName> getUpStorageNodesInGroupInClusterOrder() {
        return clusterInOrder.stream()
                .map(ClusterApi::upStorageNodeInGroup)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public List<HostName> getNodesInGroupNotAllowedToBeDown() {
        return nodeGroup.getHostNames().stream()
                .filter(hostName -> getHostStatus(hostName) != HostStatus.ALLOWED_TO_BE_DOWN)
                .collect(Collectors.toList());
    }

    @Override
    public void setHostState(HostName hostName, HostStatus status) {
        hostStatusService.setHostState(hostName, status);
    }

    // TODO: Remove
    @Override
    public ApplicationInstance<?> getApplicationInstance() {
        return applicationInstance;
    }

    private static List<ClusterApi> makeClustersInOrder(NodeGroup nodeGroup, Map<HostName, HostStatus> hostStatusMap) {
        Set<ServiceCluster<ServiceMonitorStatus>> clustersInGroup = getClustersInGroup(nodeGroup);
        return clustersInGroup.stream()
                .map(serviceCluster -> new ClusterApiImpl(serviceCluster, nodeGroup, hostStatusMap))
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

    private static Set<ServiceCluster<ServiceMonitorStatus>> getClustersInGroup(NodeGroup nodeGroup) {
        ApplicationInstance<ServiceMonitorStatus> applicationInstance = nodeGroup.getApplication();

        Set<ServiceCluster<ServiceMonitorStatus>> serviceClustersOnHost = new HashSet<>();
        for (ServiceCluster<ServiceMonitorStatus> cluster : applicationInstance.serviceClusters()) {
            for (ServiceInstance<ServiceMonitorStatus> instance : cluster.serviceInstances()) {
                if (nodeGroup.contains(instance.hostName())) {
                    serviceClustersOnHost.add(cluster);
                    break;
                }
            }
        }

        return serviceClustersOnHost;
    }
}
