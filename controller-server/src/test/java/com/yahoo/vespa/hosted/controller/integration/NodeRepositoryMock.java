// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.TargetVersions;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeList;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * @author mpolden
 * @author jonmv
 */
public class NodeRepositoryMock implements NodeRepository {

    private final Map<ZoneId, Map<HostName, Node>> nodeRepository = new HashMap<>();
    private final Map<ZoneId, TargetVersions> targetVersions = new HashMap<>();

    public void putByHostname(ZoneId zone, List<Node> nodes) {
        nodeRepository.putIfAbsent(zone, new HashMap<>());
        nodeRepository.get(zone).putAll(nodes.stream().collect(Collectors.toMap(Node::hostname,
                                                                                Function.identity())));
    }

    public void putByHostname(ZoneId zone, Node node) {
        putByHostname(zone, Collections.singletonList(node));
    }

    public void removeByHostname(ZoneId zone, List<Node> nodes) {
        nodes.forEach(node -> nodeRepository.get(zone).remove(node.hostname()));
    }

    public void clear() {
        nodeRepository.clear();
    }

    public Node require(HostName hostName) {
        return nodeRepository.values().stream()
                             .map(zoneNodes -> zoneNodes.get(hostName))
                             .filter(Objects::nonNull)
                             .findFirst()
                             .orElseThrow(() -> new NoSuchElementException("No node with the hostname " + hostName + " is known."));
    }

    public void addNodes(ZoneId zone, List<Node> nodes) {
        nodeRepository.put(zone, nodes.stream().collect(Collectors.toMap(Node::hostname, Function.identity())));
    }

    public void addFixedNodes(ZoneId zone) {
        var nodeA = new Node(HostName.from("hostA"),
                             Optional.of(HostName.from("parentHostA")),
                             Node.State.active,
                             NodeType.tenant,
                             Optional.of(ApplicationId.from("tenant1", "app1", "default")),
                             Version.fromString("7.42"),
                             Version.fromString("7.42"),
                             Version.fromString("7.6"),
                             Version.fromString("7.6"),
                             Node.ServiceState.expectedUp,
                             0,
                             0,
                             0,
                             0,
                             24,
                             24,
                             500,
                             1000,
                             false,
                             10,
                             "C-2B/24/500",
                             "clusterA",
                             Node.ClusterType.container);
        var nodeB = new Node(HostName.from("hostB"),
                             Optional.of(HostName.from("parentHostB")),
                             Node.State.active,
                             NodeType.tenant,
                             Optional.of(ApplicationId.from("tenant2", "app2", "default")),
                             Version.fromString("7.42"),
                             Version.fromString("7.42"),
                             Version.fromString("7.6"),
                             Version.fromString("7.6"),
                             Node.ServiceState.expectedUp,
                             0,
                             0,
                             0,
                             0,
                             40,
                             24,
                             500,
                             1000,
                             false,
                             20,
                             "C-2C/24/500",
                             "clusterB",
                             Node.ClusterType.container);
        addNodes(zone, List.of(nodeA, nodeB));
    }

    @Override
    public void addNodes(ZoneId zone, Collection<NodeRepositoryNode> nodes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteNode(ZoneId zone, String hostname) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setState(ZoneId zone, NodeState nodeState, String nodename) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeRepositoryNode getNode(ZoneId zone, String hostname) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeList listNodes(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeList listNodes(ZoneId zone, ApplicationId application) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Node> list(ZoneId zone) {
        return List.copyOf(nodeRepository.getOrDefault(zone, Map.of()).values());
    }

    @Override
    public List<Node> list(ZoneId zone, ApplicationId application) {
        return nodeRepository.getOrDefault(zone, Collections.emptyMap()).values().stream()
                             .filter(node -> node.owner().map(application::equals).orElse(false))
                             .collect(Collectors.toList());
    }

    @Override
    public void upgrade(ZoneId zone, NodeType type, Version version) {
        nodeRepository.getOrDefault(zone, Collections.emptyMap()).values()
                      .stream()
                      .filter(node -> node.type() == type)
                      .map(node -> new Node(node.hostname(), node.parentHostname(), node.state(), node.type(), node.owner(),
                                            node.currentVersion(), version))
                      .forEach(node -> putByHostname(zone, node));
    }

    @Override
    public void upgradeOs(ZoneId zone, NodeType type, Version version) {
        this.targetVersions.compute(zone, (ignored, targetVersions) -> {
            if (targetVersions == null) {
                targetVersions = TargetVersions.EMPTY;
            }
            return targetVersions.withOsVersion(type, version);
        });
    }

    @Override
    public TargetVersions targetVersionsOf(ZoneId zone) {
        return targetVersions.getOrDefault(zone, TargetVersions.EMPTY);
    }

    @Override
    public void requestFirmwareCheck(ZoneId zone) {
    }

    @Override
    public void cancelFirmwareCheck(ZoneId zone) {
    }

    public void doUpgrade(DeploymentId deployment, Optional<HostName> hostName, Version version) {
        modifyNodes(deployment, hostName, node -> {
            assert node.wantedVersion().equals(version);
            return new Node(node.hostname(), node.parentHostname(), node.state(), node.type(), node.owner(), version, version);
        });
    }

    private void modifyNodes(DeploymentId deployment, Optional<HostName> hostname, UnaryOperator<Node> modification) {
        List<Node> nodes = hostname.map(this::require)
                                   .map(Collections::singletonList)
                                   .orElse(list(deployment.zoneId(), deployment.applicationId()));
        putByHostname(deployment.zoneId(),
                      nodes.stream().map(modification).collect(Collectors.toList()));
    }

    public void requestRestart(DeploymentId deployment, Optional<HostName> hostname) {
        modifyNodes(deployment, hostname, node -> new Node(node.hostname(),
                                                           node.parentHostname(),
                                                           node.state(),
                                                           node.type(),
                                                           node.owner(),
                                                           node.currentVersion(),
                                                           node.wantedVersion(),
                                                           node.currentOsVersion(),
                                                           node.wantedOsVersion(),
                                                           node.serviceState(),
                                                           node.restartGeneration(),
                                                           node.wantedRestartGeneration() + 1,
                                                           node.rebootGeneration(),
                                                           node.wantedRebootGeneration(),
                                                           node.vcpu(),
                                                           node.memoryGb(),
                                                           node.diskGb(),
                                                           node.bandwidthGbps(),
                                                           node.fastDisk(),
                                                           node.cost(),
                                                           node.canonicalFlavor(),
                                                           node.clusterId(),
                                                           node.clusterType()));
    }

    public void doRestart(DeploymentId deployment, Optional<HostName> hostname) {
        modifyNodes(deployment, hostname, node -> new Node(node.hostname(),
                                                           node.parentHostname(),
                                                           node.state(),
                                                           node.type(),
                                                           node.owner(),
                                                           node.currentVersion(),
                                                           node.wantedVersion(),
                                                           node.currentOsVersion(),
                                                           node.wantedOsVersion(),
                                                           node.serviceState(),
                                                           node.restartGeneration() + 1,
                                                           node.wantedRestartGeneration(),
                                                           node.rebootGeneration(),
                                                           node.wantedRebootGeneration(),
                                                           node.vcpu(),
                                                           node.memoryGb(),
                                                           node.diskGb(),
                                                           node.bandwidthGbps(),
                                                           node.fastDisk(),
                                                           node.cost(),
                                                           node.canonicalFlavor(),
                                                           node.clusterId(),
                                                           node.clusterType()));
    }

    public void requestReboot(DeploymentId deployment, Optional<HostName> hostname) {
        modifyNodes(deployment, hostname, node -> new Node(node.hostname(),
                                                           node.parentHostname(),
                                                           node.state(),
                                                           node.type(),
                                                           node.owner(),
                                                           node.currentVersion(),
                                                           node.wantedVersion(),
                                                           node.currentOsVersion(),
                                                           node.wantedOsVersion(),
                                                           node.serviceState(),
                                                           node.restartGeneration(),
                                                           node.wantedRestartGeneration(),
                                                           node.rebootGeneration(),
                                                           node.wantedRebootGeneration() + 1,
                                                           node.vcpu(),
                                                           node.memoryGb(),
                                                           node.diskGb(),
                                                           node.bandwidthGbps(),
                                                           node.fastDisk(),
                                                           node.cost(),
                                                           node.canonicalFlavor(),
                                                           node.clusterId(),
                                                           node.clusterType()));
    }

    public void doReboot(DeploymentId deployment, Optional<HostName> hostname) {
        modifyNodes(deployment, hostname, node -> new Node(node.hostname(),
                                                           node.parentHostname(),
                                                           node.state(),
                                                           node.type(),
                                                           node.owner(),
                                                           node.currentVersion(),
                                                           node.wantedVersion(),
                                                           node.currentOsVersion(),
                                                           node.wantedOsVersion(),
                                                           node.serviceState(),
                                                           node.restartGeneration(),
                                                           node.wantedRestartGeneration(),
                                                           node.rebootGeneration() + 1,
                                                           node.wantedRebootGeneration(),
                                                           node.vcpu(),
                                                           node.memoryGb(),
                                                           node.diskGb(),
                                                           node.bandwidthGbps(),
                                                           node.fastDisk(),
                                                           node.cost(),
                                                           node.canonicalFlavor(),
                                                           node.clusterId(),
                                                           node.clusterType()));
    }

}
