// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeList;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeMembership;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.OrchestratorStatus;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A minimal interface to the node repository, providing only the operations used by the controller.
 *
 * @author mpolden
 */
public interface NodeRepository {

    void addNodes(ZoneId zone, Collection<NodeRepositoryNode> nodes);

    void deleteNode(ZoneId zone, String hostname);

    void setState(ZoneId zone, NodeState nodeState, String nodename);

    NodeRepositoryNode getNode(ZoneId zone, String hostname);

    NodeList listNodes(ZoneId zone);

    NodeList listNodes(ZoneId zone, ApplicationId application);

    NodeList listNodes(ZoneId zone, List<HostName> hostnames);

    /** List all nodes in given zone */
    default List<Node> list(ZoneId zone) {
        return listNodes(zone).nodes().stream()
                              .map(NodeRepository::toNode)
                              .collect(Collectors.toUnmodifiableList());
    }

    /** List all nodes in zone having given hostnames */
    default List<Node> list(ZoneId zone, List<HostName> hostnames) {
        return listNodes(zone, hostnames).nodes().stream()
                                         .map(NodeRepository::toNode)
                                         .collect(Collectors.toUnmodifiableList());
    }

    /** List all nodes in zone owned by given application */
    default List<Node> list(ZoneId zone, ApplicationId application) {
        return listNodes(zone, application).nodes().stream()
                                           .map(NodeRepository::toNode)
                                           .collect(Collectors.toUnmodifiableList());
    }

    /** List all nodes in states, in zone owned by given application */
    default List<Node> list(ZoneId zone, ApplicationId application, Set<Node.State> states) {
        return list(zone, application).stream()
                                      .filter(node -> states.contains(node.state()))
                                      .collect(Collectors.toList());
    }

    Application getApplication(ZoneId zone, ApplicationId application);

    void patchApplication(ZoneId zone, ApplicationId application,
                          double currentReadShare, double maxReadShare);

    Map<TenantName, URI> getArchiveUris(ZoneId zone);

    void setArchiveUri(ZoneId zone, TenantName tenantName, URI archiveUri);

    void removeArchiveUri(ZoneId zone, TenantName tenantName);

    /** Upgrade all nodes of given type to a new version */
    void upgrade(ZoneId zone, NodeType type, Version version);

    /** Upgrade OS for all nodes of given type to a new version */
    void upgradeOs(ZoneId zone, NodeType type, Version version, Optional<Duration> upgradeBudget);

    /** Get target versions for upgrades in given zone */
    TargetVersions targetVersionsOf(ZoneId zone);

    /** Requests firmware checks on all hosts in the given zone. */
    void requestFirmwareCheck(ZoneId zone);

    /** Cancels firmware checks on all hosts in the given zone. */
    void cancelFirmwareCheck(ZoneId zone);

    void retireAndDeprovision(ZoneId zoneId, String hostName);

    void patchNode(ZoneId zoneId, String hostName, NodeRepositoryNode node);

    void reboot(ZoneId zoneId, String hostName);

    private static Node toNode(NodeRepositoryNode node) {
        var application = Optional.ofNullable(node.getOwner())
                                  .map(owner -> ApplicationId.from(owner.getTenant(), owner.getApplication(),
                                                                   owner.getInstance()));
        var parentHostname = Optional.ofNullable(node.getParentHostname()).map(HostName::from);
        var resources = new NodeResources(
                toDouble(node.getResources().getVcpu()),
                toDouble(node.getResources().getMemoryGb()),
                toDouble(node.getResources().getDiskGb()),
                toDouble(node.getResources().getBandwidthGbps()),
                diskSpeedFromString(node.getResources().getDiskSpeed()),
                storageTypeFromString(node.getResources().getStorageType()));
        return new Node(HostName.from(node.getHostname()),
                        parentHostname,
                        fromJacksonState(node.getState()),
                        fromJacksonType(node.getType()),
                        resources,
                        application,
                        versionFrom(node.getVespaVersion()),
                        versionFrom(node.getWantedVespaVersion()),
                        versionFrom(node.getCurrentOsVersion()),
                        versionFrom(node.getWantedOsVersion()),
                        Optional.ofNullable(node.getCurrentFirmwareCheck()).map(Instant::ofEpochMilli),
                        Optional.ofNullable(node.getWantedFirmwareCheck()).map(Instant::ofEpochMilli),
                        toServiceState(node.getOrchestratorStatus()),
                        Optional.ofNullable(node.suspendedSinceMillis()).map(Instant::ofEpochMilli),
                        toInt(node.getCurrentRestartGeneration()),
                        toInt(node.getRestartGeneration()),
                        toInt(node.getCurrentRebootGeneration()),
                        toInt(node.getRebootGeneration()),
                        toInt(node.getCost()),
                        node.getFlavor(),
                        clusterIdOf(node.getMembership()),
                        clusterTypeOf(node.getMembership()),
                        node.getWantToRetire(),
                        node.getWantToDeprovision(),
                        Optional.ofNullable(node.getReservedTo()).map(TenantName::from),
                        Optional.ofNullable(node.getExclusiveTo()).map(ApplicationId::fromSerializedForm),
                        dockerImageFrom(node.getWantedDockerImage()),
                        dockerImageFrom(node.getCurrentDockerImage()),
                        node.getReports(),
                        node.getHistory(),
                        node.getAdditionalIpAddresses(),
                        node.getOpenStackId(),
                        Optional.ofNullable(node.getSwitchHostname()));
    }

    private static String clusterIdOf(NodeMembership nodeMembership) {
        return nodeMembership == null ? "" : nodeMembership.clusterid;
    }

    private static Node.ClusterType clusterTypeOf(NodeMembership nodeMembership) {
        if (nodeMembership == null) return Node.ClusterType.unknown;
        switch (nodeMembership.clustertype) {
            case "admin": return Node.ClusterType.admin;
            case "content": return Node.ClusterType.content;
            case "container": return Node.ClusterType.container;
            case "combined": return Node.ClusterType.combined;
        }
        return Node.ClusterType.unknown;
    }

    // Convert Jackson type to config.provision type
    private static NodeType fromJacksonType(com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeType nodeType) {
        switch (nodeType) {
            case tenant: return NodeType.tenant;
            case host: return NodeType.host;
            case proxy: return NodeType.proxy;
            case proxyhost: return NodeType.proxyhost;
            case config: return NodeType.config;
            case confighost: return NodeType.confighost;
            default: throw new IllegalArgumentException("Unknown type: " + nodeType);
        }
    }

    private static com.yahoo.vespa.hosted.controller.api.integration.configserver.Node.State fromJacksonState(NodeState state) {
        switch (state) {
            case provisioned: return Node.State.provisioned;
            case ready: return Node.State.ready;
            case reserved: return Node.State.reserved;
            case active: return Node.State.active;
            case inactive: return Node.State.inactive;
            case dirty: return Node.State.dirty;
            case failed: return Node.State.failed;
            case parked: return Node.State.parked;
            case breakfixed: return Node.State.breakfixed;
        }
        return Node.State.unknown;
    }

    private static NodeResources.DiskSpeed diskSpeedFromString(String diskSpeed) {
        if (diskSpeed == null) return NodeResources.DiskSpeed.getDefault();
        switch (diskSpeed) {
            case "fast": return NodeResources.DiskSpeed.fast;
            case "slow": return NodeResources.DiskSpeed.slow;
            case "any": return NodeResources.DiskSpeed.any;
            default: throw new IllegalArgumentException("Unknown disk speed '" + diskSpeed + "'");
        }
    }

    private static NodeResources.StorageType storageTypeFromString(String storageType) {
        if (storageType == null) return NodeResources.StorageType.getDefault();
        switch (storageType) {
            case "remote": return NodeResources.StorageType.remote;
            case "local": return NodeResources.StorageType.local;
            case "any": return NodeResources.StorageType.any;
            default: throw new IllegalArgumentException("Unknown storage type '" + storageType + "'");
        }
    }

    private static Node.ServiceState toServiceState(OrchestratorStatus orchestratorStatus) {
        switch (orchestratorStatus) {
            case ALLOWED_TO_BE_DOWN: return Node.ServiceState.allowedDown;
            case PERMANENTLY_DOWN: return Node.ServiceState.permanentlyDown;
            case NO_REMARKS: return Node.ServiceState.expectedUp;
        }

        return Node.ServiceState.unknown;
    }

    private static double toDouble(Double d) {
        return d == null ? 0 : d;
    }

    private static int toInt(Integer i) {
        return i == null ? 0 : i;
    }

    private static Version versionFrom(String s) {
        return s == null ? Version.emptyVersion : Version.fromString(s);
    }

    private static DockerImage dockerImageFrom(String s) {
        return s == null ? DockerImage.EMPTY : DockerImage.fromString(s);
    }

}
