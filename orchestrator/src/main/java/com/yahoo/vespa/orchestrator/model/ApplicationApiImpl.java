// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.OrchestratorUtil;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.vespa.orchestrator.status.HostInfos;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.ApplicationLock;

import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.yahoo.vespa.orchestrator.OrchestratorUtil.getHostsUsedByApplicationInstance;

/**
 * @author hakonhall
 */
public class ApplicationApiImpl implements ApplicationApi {

    private final ApplicationInstance applicationInstance;
    private final NodeGroup nodeGroup;
    private final ApplicationLock lock;
    private final Clock clock;
    private final List<ClusterApi> clusterInOrder;
    private final HostInfos hostInfos;

    public ApplicationApiImpl(NodeGroup nodeGroup,
                              ApplicationLock lock,
                              ClusterControllerClientFactory clusterControllerClientFactory,
                              int numberOfConfigServers, Clock clock) {
        this.applicationInstance = nodeGroup.getApplication();
        this.nodeGroup = nodeGroup;
        this.lock = lock;
        this.clock = clock;
        this.hostInfos = lock.getHostInfos();
        this.clusterInOrder = makeClustersInOrder(nodeGroup, hostInfos, clusterControllerClientFactory, numberOfConfigServers);
    }

    @Override
    public ApplicationId applicationId() {
        return OrchestratorUtil.toApplicationId(applicationInstance.reference());
    }

    private HostStatus getHostStatus(HostName hostName) {
        return hostInfos.getOrNoRemarks(hostName).status();
    }

    @Override
    public List<ClusterApi> getClusters() {
        return clusterInOrder;
    }

    @Override
    public List<StorageNode> getSuspendedStorageNodesInGroupInReverseClusterOrder() {
        return getStorageNodesInGroupInClusterOrder().stream()
                .filter(storageNode -> getHostStatus(storageNode.hostName()).isSuspended())
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
        return lock.getApplicationInstanceStatus();
    }

    @Override
    public void setHostState(OrchestratorContext context, HostName hostName, HostStatus status) {
        lock.setHostState(hostName, status);
    }

    @Override
    public List<HostName> getNodesInGroupWith(Predicate<HostStatus> statusPredicate) {
        return nodeGroup.getHostNames().stream()
                .filter(hostName -> statusPredicate.test(getHostStatus(hostName)))
                .collect(Collectors.toList());
    }

    private List<ClusterApi> makeClustersInOrder(NodeGroup nodeGroup,
                                                 HostInfos hostInfos,
                                                 ClusterControllerClientFactory clusterControllerClientFactory,
                                                 int numberOfConfigServers) {
        Set<ServiceCluster> clustersInGroup = getServiceClustersInGroup(nodeGroup);
        return clustersInGroup.stream()
                .map(serviceCluster -> new ClusterApiImpl(
                        this,
                        serviceCluster,
                        nodeGroup,
                        hostInfos,
                        clusterControllerClientFactory,
                        numberOfConfigServers,
                        clock))
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

    private static Set<ServiceCluster> getServiceClustersInGroup(NodeGroup nodeGroup) {
        ApplicationInstance applicationInstance = nodeGroup.getApplication();

        Set<ServiceCluster> serviceClustersInGroup = new HashSet<>();
        for (ServiceCluster cluster : applicationInstance.serviceClusters()) {
            for (ServiceInstance instance : cluster.serviceInstances()) {
                if (nodeGroup.contains(instance.hostName())) {
                    serviceClustersInGroup.add(cluster);
                    break;
                }
            }
        }

        return serviceClustersInGroup;
    }

}
