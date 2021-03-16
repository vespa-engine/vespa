// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.collections.Pair;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Application;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.TargetVersions;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeList;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * @author mpolden
 * @author jonmv
 */
public class NodeRepositoryMock implements NodeRepository {

    private final Map<ZoneId, Map<HostName, Node>> nodeRepository = new HashMap<>();
    private final Map<ZoneId, Map<ApplicationId, Application>> applications = new HashMap<>();
    private final Map<ZoneId, TargetVersions> targetVersions = new HashMap<>();
    private final Map<Integer, Duration> osUpgradeBudgets = new HashMap<>();
    private final Map<DeploymentId, Pair<Double, Double>> trafficFractions = new HashMap<>();
    private final Map<ZoneId, Map<TenantName, URI>> archiveUris = new HashMap<>();

    // A separate/alternative list of NodeRepositoryNode nodes.
    // Methods operating with Node and NodeRepositoryNode lives separate lives.
    private final Map<ZoneId, List<NodeRepositoryNode>> nodeRepoNodes = new HashMap<>();

    private boolean allowPatching = false;

    /** Add or update given nodes in zone */
    public void putNodes(ZoneId zone, List<Node> nodes) {
        nodeRepository.putIfAbsent(zone, new HashMap<>());
        nodeRepository.get(zone).putAll(nodes.stream().collect(Collectors.toMap(Node::hostname,
                                                                                Function.identity())));
    }

    public void putApplication(ZoneId zone, Application application) {
        applications.putIfAbsent(zone, new HashMap<>());
        applications.get(zone).put(application.id(), application);
    }

    public Pair<Double, Double> getTrafficFraction(ApplicationId application, ZoneId zone) {
        return trafficFractions.get(new DeploymentId(application, zone));
    }

    /** Add or update given node in zone */
    public void putNodes(ZoneId zone, Node node) {
        putNodes(zone, Collections.singletonList(node));
    }

    /** Remove given nodes from zone */
    public void removeNodes(ZoneId zone, List<Node> nodes) {
        nodes.forEach(node -> nodeRepository.get(zone).remove(node.hostname()));
    }

    /** Remove all nodes in all zones */
    public void clear() {
        nodeRepository.clear();
        nodeRepoNodes.clear();
    }

    /** Replace nodes in zone with given nodes */
    public void setNodes(ZoneId zone, List<Node> nodes) {
        nodeRepository.put(zone, nodes.stream().collect(Collectors.toMap(Node::hostname, Function.identity())));
    }

    public Node require(HostName hostName) {
        return nodeRepository.values().stream()
                             .map(zoneNodes -> zoneNodes.get(hostName))
                             .filter(Objects::nonNull)
                             .findFirst()
                             .orElseThrow(() -> new NoSuchElementException("No node with the hostname " + hostName + " is known."));
    }

    /** Replace nodes in zone with a fixed set of nodes */
    public void setFixedNodes(ZoneId zone) {
        var nodeA = new Node.Builder()
                .hostname(HostName.from("hostA"))
                .parentHostname(HostName.from("parentHostA"))
                .state(Node.State.active)
                .type(NodeType.tenant)
                .owner(ApplicationId.from("tenant1", "app1", "default"))
                .currentVersion(Version.fromString("7.42"))
                .wantedVersion(Version.fromString("7.42"))
                .currentOsVersion(Version.fromString("7.6"))
                .wantedOsVersion(Version.fromString("7.6"))
                .serviceState(Node.ServiceState.expectedUp)
                .resources(new NodeResources(24, 24, 500, 1))
                .clusterId("clusterA")
                .clusterType(Node.ClusterType.container)
                .exclusiveTo(ApplicationId.from("t1", "a1", "i1"))
                .build();
        var nodeB = new Node.Builder()
                .hostname(HostName.from("hostB"))
                .parentHostname(HostName.from("parentHostB"))
                .state(Node.State.active)
                .type(NodeType.tenant)
                .owner(ApplicationId.from("tenant2", "app2", "default"))
                .currentVersion(Version.fromString("7.42"))
                .wantedVersion(Version.fromString("7.42"))
                .currentOsVersion(Version.fromString("7.6"))
                .wantedOsVersion(Version.fromString("7.6"))
                .serviceState(Node.ServiceState.expectedUp)
                .resources(new NodeResources(40, 24, 500, 1))
                .cost(20)
                .clusterId("clusterB")
                .clusterType(Node.ClusterType.container)
                .build();
        setNodes(zone, List.of(nodeA, nodeB));
    }

    @Override
    public void addNodes(ZoneId zone, Collection<NodeRepositoryNode> nodes) {
        nodeRepoNodes.put(zone, new ArrayList<>(nodes));
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
        return new NodeList(nodeRepoNodes.get(zone));
    }

    @Override
    public NodeList listNodes(ZoneId zone, ApplicationId application) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeList listNodes(ZoneId zone, List<HostName> hostnames) {
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
    public List<Node> list(ZoneId zone, List<HostName> hostnames) {
        return nodeRepository.getOrDefault(zone, Collections.emptyMap()).values().stream()
                             .filter(node -> hostnames.contains(node.hostname()))
                             .collect(Collectors.toList());
    }

    @Override
    public Application getApplication(ZoneId zone, ApplicationId applicationId) {
        return applications.get(zone).get(applicationId);
    }

    @Override
    public void patchApplication(ZoneId zone, ApplicationId application,
                                 double currentReadShare, double maxReadShare) {
        trafficFractions.put(new DeploymentId(application, zone), new Pair<>(currentReadShare, maxReadShare));
    }

    @Override
    public Map<TenantName, URI> getArchiveUris(ZoneId zone) {
        return Map.copyOf(archiveUris.getOrDefault(zone, Map.of()));
    }

    @Override
    public void setArchiveUri(ZoneId zone, TenantName tenantName, URI archiveUri) {
        archiveUris.computeIfAbsent(zone, z -> new HashMap<>()).put(tenantName, archiveUri);
    }

    @Override
    public void removeArchiveUri(ZoneId zone, TenantName tenantName) {
        Optional.ofNullable(archiveUris.get(zone)).ifPresent(map -> map.remove(tenantName));
    }

    @Override
    public void upgrade(ZoneId zone, NodeType type, Version version) {
        this.targetVersions.compute(zone, (ignored, targetVersions) -> {
            if (targetVersions == null) {
                targetVersions = TargetVersions.EMPTY;
            }
            return targetVersions.withVespaVersion(type, version);
        });
        // Bump wanted version of each node. This is done by InfrastructureProvisioner in a real node repository.
        nodeRepository.getOrDefault(zone, Map.of()).values()
                      .stream()
                      .filter(node -> node.type() == type)
                      .map(node -> new Node.Builder(node).wantedVersion(version).build())
                      .forEach(node -> putNodes(zone, node));
    }

    @Override
    public void upgradeOs(ZoneId zone, NodeType type, Version version, Optional<Duration> upgradeBudget) {
        upgradeBudget.ifPresent(d -> this.osUpgradeBudgets.put(Objects.hash(zone, type, version), d));
        this.targetVersions.compute(zone, (ignored, targetVersions) -> {
            if (targetVersions == null) {
                targetVersions = TargetVersions.EMPTY;
            }
            return targetVersions.withOsVersion(type, version);
        });
        // Bump wanted version of each node. This is done by OsUpgradeActivator in a real node repository.
        nodeRepository.getOrDefault(zone, Map.of()).values()
                      .stream()
                      .filter(node -> node.type() == type)
                      .map(node -> new Node.Builder(node).wantedOsVersion(version).build())
                      .forEach(node -> putNodes(zone, node));
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

    @Override
    public void retireAndDeprovision(ZoneId zoneId, String hostName) {
        nodeRepository.get(zoneId).remove(HostName.from(hostName));
    }

    @Override
    public void patchNode(ZoneId zoneId, String hostName, NodeRepositoryNode node) {
        if (!allowPatching) throw new UnsupportedOperationException();
        List<Node> existing = list(zoneId, List.of(HostName.from(hostName)));
        if (existing.size() != 1) throw new IllegalArgumentException("Node " + hostName + " not found in " + zoneId);

        // Note: Only supports switchHostname
        Node newNode = new Node.Builder(existing.get(0)).switchHostname(node.getSwitchHostname())
                                                        .build();
        putNodes(zoneId, newNode);
    }

    @Override
    public void reboot(ZoneId zoneId, String hostName) {
        throw new UnsupportedOperationException();
    }

    public Optional<Duration> osUpgradeBudget(ZoneId zone, NodeType type, Version version) {
        return Optional.ofNullable(osUpgradeBudgets.get(Objects.hash(zone, type, version)));
    }

    public void doUpgrade(DeploymentId deployment, Optional<HostName> hostName, Version version) {
        modifyNodes(deployment, hostName, node -> {
            assert node.wantedVersion().equals(version);
            return new Node.Builder(node)
                    .currentVersion(version)
                    .currentDockerImage(node.wantedDockerImage())
                    .build();
        });
    }

    private void modifyNodes(DeploymentId deployment, Optional<HostName> hostname, UnaryOperator<Node> modification) {
        List<Node> nodes = hostname.map(this::require)
                                   .map(Collections::singletonList)
                                   .orElse(list(deployment.zoneId(), deployment.applicationId()));
        putNodes(deployment.zoneId(),
                 nodes.stream().map(modification).collect(Collectors.toList()));
    }

    public void requestRestart(DeploymentId deployment, Optional<HostName> hostname) {
        modifyNodes(deployment, hostname, node -> new Node.Builder(node).wantedRestartGeneration(node.wantedRestartGeneration() + 1).build());
    }

    public void doRestart(DeploymentId deployment, Optional<HostName> hostname) {
        modifyNodes(deployment, hostname, node -> new Node.Builder(node).restartGeneration(node.restartGeneration() + 1).build());
    }

    public void requestReboot(DeploymentId deployment, Optional<HostName> hostname) {
        modifyNodes(deployment, hostname, node -> new Node.Builder(node).wantedRebootGeneration(node.wantedRebootGeneration() + 1).build());
    }

    public void doReboot(DeploymentId deployment, Optional<HostName> hostname) {
        modifyNodes(deployment, hostname, node -> new Node.Builder(node).rebootGeneration(node.rebootGeneration() + 1).build());
    }

    public void addReport(ZoneId zoneId, HostName hostName, String reportId, JsonNode report) {
        nodeRepository.get(zoneId).get(hostName).reports().put(reportId, report);
    }

    public NodeRepositoryMock allowPatching(boolean allowPatching) {
        this.allowPatching = allowPatching;
        return this;
    }

}
