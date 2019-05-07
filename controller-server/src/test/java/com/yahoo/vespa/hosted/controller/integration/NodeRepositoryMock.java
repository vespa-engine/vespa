// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.config.provision.zone.ZoneId;

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
                      .map(node -> new Node(node.hostname(), node.state(), node.type(), node.owner(),
                                            node.currentVersion(), version))
                      .forEach(node -> putByHostname(zone, node));
    }

    @Override
    public void upgradeOs(ZoneId zone, NodeType type, Version version) {
        nodeRepository.getOrDefault(zone, Collections.emptyMap()).values()
                      .stream()
                      .filter(node -> node.type() == type)
                      .map(node -> new Node(node.hostname(),
                                            node.state(),
                                            node.type(),
                                            node.owner(),
                                            node.currentVersion(),
                                            node.wantedVersion(),
                                            node.currentOsVersion(),
                                            version,
                                            node.serviceState(),
                                            node.restartGeneration(),
                                            node.wantedRestartGeneration(),
                                            node.rebootGeneration(),
                                            node.wantedRebootGeneration(),
                                            node.canonicalFlavor(),
                                            node.clusterId(),
                                            node.clusterType()))
                      .forEach(node -> putByHostname(zone, node));
    }

    @Override
    public void requestFirmwareCheck(ZoneId zone) {
        ;
    }

    @Override
    public void cancelFirmwareCheck(ZoneId zone) {
        ;
    }

    public void doUpgrade(DeploymentId deployment, Optional<HostName> hostName, Version version) {
        modifyNodes(deployment, hostName, node -> {
            assert node.wantedVersion().equals(version);
            return new Node(node.hostname(), node.state(), node.type(), node.owner(), version, version);
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
                                                           node.canonicalFlavor(),
                                                           node.clusterId(),
                                                           node.clusterType()));
    }

    public void doRestart(DeploymentId deployment, Optional<HostName> hostname) {
        modifyNodes(deployment, hostname, node -> new Node(node.hostname(),
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
                                                           node.canonicalFlavor(),
                                                           node.clusterId(),
                                                           node.clusterType()));
    }

    public void requestReboot(DeploymentId deployment, Optional<HostName> hostname) {
        modifyNodes(deployment, hostname, node -> new Node(node.hostname(),
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
                                                           node.canonicalFlavor(),
                                                           node.clusterId(),
                                                           node.clusterType()));
    }

    public void doReboot(DeploymentId deployment, Optional<HostName> hostname) {
        modifyNodes(deployment, hostname, node -> new Node(node.hostname(),
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
                                                           node.canonicalFlavor(),
                                                           node.clusterId(),
                                                           node.clusterType()));
    }

}
