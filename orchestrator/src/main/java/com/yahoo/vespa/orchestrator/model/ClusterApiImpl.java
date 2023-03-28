// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.policy.ClusterParams;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostedVespaPolicy;
import com.yahoo.vespa.orchestrator.policy.SuspensionReasons;
import com.yahoo.vespa.orchestrator.status.HostInfos;
import com.yahoo.vespa.orchestrator.status.HostStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
    static final Duration downMoratorium = Duration.ofSeconds(30);

    private final ApplicationApi applicationApi;
    private final ServiceCluster serviceCluster;
    private final NodeGroup nodeGroup;
    private final HostInfos hostInfos;
    private final ClusterControllerClientFactory clusterControllerClientFactory;
    private final Clock clock;
    private final Set<ServiceInstance> servicesInGroup;
    private final Set<ServiceInstance> servicesNotInGroup;

    /** Lazily initialized in servicesDownAndNotInGroup(), do not access directly. */
    private Set<ServiceInstance> servicesDownAndNotInGroup = null;

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
                          HostInfos hostInfos,
                          ClusterControllerClientFactory clusterControllerClientFactory,
                          ClusterParams clusterParams,
                          Clock clock) {
        this.applicationApi = applicationApi;
        this.serviceCluster = serviceCluster;
        this.nodeGroup = nodeGroup;
        this.hostInfos = hostInfos;
        this.clusterControllerClientFactory = clusterControllerClientFactory;
        this.clock = clock;

        Map<Boolean, Set<ServiceInstance>> serviceInstancesByLocality =
                serviceCluster.serviceInstances().stream()
                        .collect(
                                Collectors.groupingBy(
                                        instance -> nodeGroup.contains(instance.hostName()),
                                        Collectors.toSet()));
        servicesInGroup = serviceInstancesByLocality.getOrDefault(true, Collections.emptySet());
        servicesNotInGroup = serviceInstancesByLocality.getOrDefault(false, Collections.emptySet());

        int serviceInstances = serviceCluster.serviceInstances().size();
        if (clusterParams.size().isPresent() && serviceInstances < clusterParams.size().getAsInt()) {
            missingServices = clusterParams.size().getAsInt() - serviceInstances;
            descriptionOfMissingServices = missingServices + " missing " + serviceCluster.nodeDescription(missingServices > 1);
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
    public String serviceDescription(boolean plural) {
        return serviceCluster.serviceDescription(plural);
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
    public boolean isConfigServerLike() {
        return serviceCluster.isConfigServerLike();
    }

    @Override
    public Optional<SuspensionReasons> allServicesDown() {
        SuspensionReasons reasons = new SuspensionReasons();

        for (ServiceInstance service : servicesInGroup) {
            if (hostStatus(service.hostName()).isSuspended()) {
                reasons.mergeWith(SuspensionReasons.nothingNoteworthy());
                continue;
            }

            if (service.serviceStatus() == ServiceStatus.DOWN) {
                Optional<Instant> since = service.serviceStatusInfo().since();
                if (since.isEmpty()) {
                    reasons.mergeWith(SuspensionReasons.isDown(service));
                    continue;
                }

                // Make sure services truly are down for some period of time before we allow suspension.
                // On the other hand, a service coming down and up repeatedly should probably
                // also be allowed... difficult without keeping track of history in a better way.
                final Duration downDuration = Duration.between(since.get(), clock.instant());
                if (downDuration.compareTo(downMoratorium) > 0) {
                    reasons.mergeWith(SuspensionReasons.downSince(service, since.get(), downDuration));
                    continue;
                }
            }

            return Optional.empty();
        }

        return Optional.of(reasons);
    }

    int missingServices() { return missingServices; }

    @Override
    public boolean noServicesOutsideGroupIsDown() throws HostStateChangeDeniedException {
        return servicesDownAndNotInGroup().size() + missingServices == 0;
    }

    @Override
    public int percentageOfServicesDownOutsideGroup() {
        int numberOfServicesDown = servicesDownAndNotInGroup().size() + missingServices;
        return numberOfServicesDown * 100 / (serviceCluster.serviceInstances().size() + missingServices);
    }

    @Override
    public int percentageOfServicesDownIfGroupIsAllowedToBeDown() {
        int numberOfServicesDown = servicesDownAndNotInGroup().size() + missingServices + servicesInGroup.size();
        return numberOfServicesDown * 100 / (serviceCluster.serviceInstances().size() + missingServices);
    }

    /**
     * A description of the hosts outside the group that are allowed to be down,
     * and a description of the services outside the group and outside of the allowed services
     * that are down.
     */
    @Override
    public String downDescription() {
        StringBuilder description = new StringBuilder();

        Set<HostName> suspended = servicesNotInGroup.stream()
                .map(ServiceInstance::hostName)
                .filter(hostName -> hostStatus(hostName).isSuspended())
                .collect(Collectors.toSet());

        if (suspended.size() > 0) {
            description.append(" ");

            final int nodeLimit = 3;
            description.append(suspended.stream().sorted().distinct().limit(nodeLimit).toList().toString());
            if (suspended.size() > nodeLimit) {
                description.append(" and " + (suspended.size() - nodeLimit) + " others");
            }
            description.append(" are suspended.");
        }

        Set<ServiceInstance> downElsewhere = servicesDownAndNotInGroup().stream()
                .filter(serviceInstance -> !suspended.contains(serviceInstance.hostName()))
                .collect(Collectors.toSet());

        final int downElsewhereTotal = downElsewhere.size() + missingServices;
        if (downElsewhereTotal > 0) {
            description.append(" ");

            final int serviceLimit = 2; // services info is verbose
            description.append(Stream.concat(
                    downElsewhere.stream().map(ServiceInstance::toString).sorted(),
                    missingServices > 0 ? Stream.of(descriptionOfMissingServices) : Stream.of())
                    .limit(serviceLimit)
                    .toList()
                    .toString());

            if (downElsewhereTotal > serviceLimit) {
                description.append(" and " + (downElsewhereTotal - serviceLimit) + " others");
            }
            description.append(" are down.");
        }

        return description.toString();
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
                if (storageNodes.stream().anyMatch(s -> s.hostName().equals(hostName))) {
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
    public String clusterInfo() {
        return "{ clusterId=" + clusterId() + ", serviceType=" + serviceType() + " }";
    }

    private Set<ServiceInstance> servicesDownAndNotInGroup() {
        if (servicesDownAndNotInGroup == null) {
            servicesDownAndNotInGroup = servicesNotInGroup.stream().filter(this::serviceEffectivelyDown).collect(Collectors.toSet());
        }
        return servicesDownAndNotInGroup;
    }

    private HostStatus hostStatus(HostName hostName) {
        return hostInfos.getOrNoRemarks(hostName).status();
    }

    private boolean serviceEffectivelyDown(ServiceInstance service) throws HostStateChangeDeniedException {
        if (hostStatus(service.hostName()).isSuspended()) return true;

        return switch (service.serviceStatus()) {
            case DOWN -> true;
            case UNKNOWN -> throw new HostStateChangeDeniedException(
                    nodeGroup,
                    HostedVespaPolicy.UNKNOWN_SERVICE_STATUS,
                    "Service status of " + service.descriptiveName() + " is not yet known");
            default -> false;
        };
    }
}
