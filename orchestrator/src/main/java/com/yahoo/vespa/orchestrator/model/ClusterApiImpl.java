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
import java.util.stream.Stream;

/**
 * @author hakonhall
 */
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

    /*
     * There are two sources for the number of config servers in a cluster. The config server config and the node
     * repository.
     *
     * The actual number of config servers in the zone-config-servers application/cluster may be less than
     * the configured number.
     *
     * For example: If only 2/3 have been provisioned so far, or 1 is being reprovisioned. In these cases it is
     * important for the Orchestrator to count that third config server as down.
     */
    private final int missingServices;
    private final String descriptionOfMissingServices;

    public ClusterApiImpl(ApplicationApi applicationApi,
                          ServiceCluster serviceCluster,
                          NodeGroup nodeGroup,
                          Map<HostName, HostStatus> hostStatusMap,
                          ClusterControllerClientFactory clusterControllerClientFactory,
                          int numberOfConfigServers) {
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

        int serviceInstances = serviceCluster.serviceInstances().size();
        if (serviceCluster.isConfigServerCluster() && serviceInstances < numberOfConfigServers) {
            missingServices = numberOfConfigServers - serviceInstances;
            descriptionOfMissingServices = missingServices + " missing config server" + (missingServices > 1 ? "s" : "");
        } else {
            missingServices = 0;
            descriptionOfMissingServices = "NA";
        }
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

    int missingServices() { return missingServices; }

    @Override
    public boolean noServicesOutsideGroupIsDown() {
        return servicesDownAndNotInGroup.size() + missingServices == 0;
    }

    @Override
    public int percentageOfServicesDown() {
        int numberOfServicesDown = servicesDownAndNotInGroup.size() + missingServices + servicesDownInGroup.size();
        return numberOfServicesDown * 100 / (serviceCluster.serviceInstances().size() + missingServices);
    }

    @Override
    public int percentageOfServicesDownIfGroupIsAllowedToBeDown() {
        int numberOfServicesDown = servicesDownAndNotInGroup.size() + missingServices + servicesInGroup.size();
        return numberOfServicesDown * 100 / (serviceCluster.serviceInstances().size() + missingServices);
    }

    @Override
    public String servicesDownAndNotInGroupDescription() {
        // Sort these for readability and testing stability
        return Stream
                .concat(servicesDownAndNotInGroup.stream().map(ServiceInstance::toString).sorted(),
                        missingServices > 0 ? Stream.of(descriptionOfMissingServices) : Stream.of())
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

    private Optional<StorageNode> storageNodeInGroup(Predicate<ServiceInstance> storageServicePredicate) {
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
