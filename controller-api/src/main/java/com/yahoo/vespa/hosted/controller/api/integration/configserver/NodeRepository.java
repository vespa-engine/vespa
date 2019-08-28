// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeList;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;

import java.util.Collection;
import java.util.List;
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

    /** List all nodes in zone owned by given application */
    default List<Node> list(ZoneId zone, ApplicationId application) {
        return listNodes(zone, application).nodes().stream()
                .map(n -> new Node(com.yahoo.config.provision.HostName.from(n.getHostname()),
                        fromJacksonState(n.getState()),
                        fromJacksonType(n.getType()), Optional.of(application),
                        Version.fromString(n.getVespaVersion()),
                        Version.fromString(n.getWantedVespaVersion()),
                        Version.fromString(n.getCurrentOsVersion()),
                        Version.fromString(n.getWantedOsVersion()),
                        fromBoolean(n.getAllowedToBeDown()),
                        n.getCurrentRestartGeneration(),
                        n.getRestartGeneration(),
                        n.getCurrentRebootGeneration(),
                        n.getRebootGeneration(),
                        n.getMinCpuCores(),
                        n.getMinMainMemoryAvailableGb(),
                        n.getMinDiskAvailableGb(),
                        n.getBandwidth() / 1000,
                        n.getFastDisk(),
                        n.getCanonicalFlavor(),
                        n.getMembership().clusterid,
                        clusterTypeOf(n.getMembership().clustertype)))
                .collect(Collectors.toUnmodifiableList());
    }

    /** List all nodes in states, in zone owned by given application */
    default List<Node> list(ZoneId zone, ApplicationId application, Set<Node.State> states) {
        return list(zone, application).stream()
                                      .filter(node -> states.contains(node.state()))
                                      .collect(Collectors.toList());
    }

    /** Upgrade all nodes of given type to a new version */
    void upgrade(ZoneId zone, NodeType type, Version version);

    /** Upgrade OS for all nodes of given type to a new version */
    void upgradeOs(ZoneId zone, NodeType type, Version version);

    /** Requests firmware checks on all hosts in the given zone. */
    void requestFirmwareCheck(ZoneId zone);

    /** Cancels firmware checks on all hosts in the given zone. */
    void cancelFirmwareCheck(ZoneId zone);



    private Node.ClusterType clusterTypeOf(String type) {
        switch (type) {
            case "admin": return Node.ClusterType.admin;
            case "content": return Node.ClusterType.content;
            case "container": return Node.ClusterType.container;
            default: throw new IllegalArgumentException("Unknown cluster type '" + type + "'.");
        }
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
            default: throw new IllegalArgumentException("Unknown state: " + state);
        }
    }

    private static Node.ServiceState fromBoolean(Boolean allowedDown) {
        return (allowedDown == null)
                ? Node.ServiceState.unorchestrated
                : allowedDown ? Node.ServiceState.allowedDown : Node.ServiceState.expectedUp;
    }
}
