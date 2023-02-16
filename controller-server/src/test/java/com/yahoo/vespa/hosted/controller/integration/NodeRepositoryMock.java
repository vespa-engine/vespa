// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.collections.Pair;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Application;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationStats;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Load;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepoStats;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.TargetVersions;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.ApplicationPatch;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * @author mpolden
 * @author jonmv
 */
public class NodeRepositoryMock implements NodeRepository {

    private final Map<ZoneId, Map<HostName, Node>> nodeRepository = new ConcurrentHashMap<>();
    private final Map<ZoneId, Map<ApplicationId, Application>> applications = new ConcurrentHashMap<>();
    private final Map<ZoneId, TargetVersions> targetVersions = new ConcurrentHashMap<>();
    private final Map<DeploymentId, Pair<Double, Double>> trafficFractions = new ConcurrentHashMap<>();
    private final Map<DeploymentClusterId, BcpGroupInfo> bcpGroupInfos = new ConcurrentHashMap<>();
    private final Map<ZoneId, Map<TenantName, URI>> archiveUris = new ConcurrentHashMap<>();

    private boolean allowPatching = true;
    private boolean hasSpareCapacity = false;

    @Override
    public void addNodes(ZoneId zone, List<Node> nodes) {
        Map<HostName, Node> existingNodes = nodeRepository.getOrDefault(zone, Map.of());
        for (var node : nodes) {
            if (existingNodes.containsKey(node.hostname())) {
                throw new IllegalArgumentException("Node " + node.hostname() + " already added in zone " + zone);
            }
        }
        putNodes(zone, nodes);
    }

    @Override
    public void deleteNode(ZoneId zone, String hostname) {
        require(zone, hostname);
        nodeRepository.get(zone).remove(HostName.of(hostname));
    }

    @Override
    public void setState(ZoneId zone, Node.State state, String hostname) {
        Node node = Node.builder(require(zone, hostname))
                        .state(Node.State.valueOf(state.name()))
                        .build();
        putNodes(zone, node);
    }

    @Override
    public Node getNode(ZoneId zone, String hostname) {
        return require(zone, hostname);
    }

    @Override
    public List<Node> list(ZoneId zone, NodeFilter filter) {
        return nodeRepository.getOrDefault(zone, Map.of()).values().stream()
                             .filter(node -> filter.includeDeprovisioned() || node.state() != Node.State.deprovisioned)
                             .filter(node -> filter.applications().isEmpty() ||
                                             (node.owner().isPresent() && filter.applications().contains(node.owner().get())))
                             .filter(node -> filter.hostnames().isEmpty() || filter.hostnames().contains(node.hostname()))
                             .filter(node -> filter.states().isEmpty() || filter.states().contains(node.state()))
                             .toList();
    }

    @Override
    public Application getApplication(ZoneId zone, ApplicationId applicationId) {
        return applications.get(zone).get(applicationId);
    }

    @Override
    public void patchApplication(ZoneId zone, ApplicationId application, ApplicationPatch applicationPatch) {
        trafficFractions.put(new DeploymentId(application, zone),
                             new Pair<>(applicationPatch.currentReadShare, applicationPatch.maxReadShare));
        if (applicationPatch.clusters != null) {
            for (var cluster : applicationPatch.clusters.entrySet())
                bcpGroupInfos.put(new DeploymentClusterId(new DeploymentId(application, zone), new ClusterSpec.Id(cluster.getKey())),
                                  new BcpGroupInfo(cluster.getValue().bcpGroupInfo.queryRate,
                                                   cluster.getValue().bcpGroupInfo.growthRateHeadroom,
                                                   cluster.getValue().bcpGroupInfo.cpuCostPerQuery));
        }
    }

    @Override
    public NodeRepoStats getStats(ZoneId zone) {
        List<ApplicationStats> applicationStats =
                applications.containsKey(zone)
                        ? applications.get(zone).keySet().stream()
                                      .map(id -> new ApplicationStats(id, Load.zero(), 0, 0))
                                      .toList()
                        : List.of();

        return new NodeRepoStats(0.0, 0.0, Load.zero(), Load.zero(), applicationStats);
    }

    @Override
    public Map<TenantName, URI> getArchiveUris(ZoneId zone) {
        return Map.copyOf(archiveUris.getOrDefault(zone, Map.of()));
    }

    @Override
    public void setArchiveUri(ZoneId zone, TenantName tenantName, URI archiveUri) {
        archiveUris.computeIfAbsent(zone, z -> new ConcurrentHashMap<>()).put(tenantName, archiveUri);
    }

    @Override
    public void removeArchiveUri(ZoneId zone, TenantName tenantName) {
        Optional.ofNullable(archiveUris.get(zone)).ifPresent(map -> map.remove(tenantName));
    }

    @Override
    public void upgrade(ZoneId zone, NodeType type, Version version, boolean allowDowngrade) {
        this.targetVersions.compute(zone, (ignored, targetVersions) -> {
            if (targetVersions == null) {
                targetVersions = TargetVersions.EMPTY;
            }
            Optional<Version> current = targetVersions.vespaVersion(type);
            if (current.isPresent() && version.isBefore(current.get()) && !allowDowngrade) {
                throw new IllegalArgumentException("Changing wanted version for " + type + " in " + zone + " from " +
                                                   current.get() + " to " + version +
                                                   ", but downgrade is not allowed");
            }
            return targetVersions.withVespaVersion(type, version);
        });
        // Bump wanted version of each node. This is done by InfrastructureProvisioner in a real node repository.
        nodeRepository.getOrDefault(zone, Map.of()).values()
                      .stream()
                      .filter(node -> node.type() == type)
                      .map(node -> Node.builder(node).wantedVersion(version).build())
                      .forEach(node -> putNodes(zone, node));
    }

    @Override
    public void upgradeOs(ZoneId zone, NodeType type, Version version) {
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
                      .map(node -> Node.builder(node).wantedOsVersion(version).build())
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
    public void retire(ZoneId zone, String hostname, boolean wantToRetire, boolean wantToDeprovision) {
        patchNodes(zone, hostname, (node) -> Node.builder(node).wantToRetire(wantToRetire).wantToDeprovision(wantToDeprovision).build());
    }

    @Override
    public void updateReports(ZoneId zone, String hostname, Map<String, String> reports) {
        Map<String, String> trimmedReports = reports.entrySet().stream()
                                                    // Null value clears a report
                                                    .filter(kv -> kv.getValue() != null)
                                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        patchNodes(zone, hostname, (node) -> Node.builder(node).reports(trimmedReports).build());
    }

    @Override
    public void updateModel(ZoneId zone, String hostname, String modelName) {
        patchNodes(zone, hostname, (node) -> Node.builder(node).modelName(modelName).build());
    }

    @Override
    public void updateSwitchHostname(ZoneId zone, String hostname, String switchHostname) {
        patchNodes(zone, hostname, (node) -> Node.builder(node).switchHostname(switchHostname).build());
    }

    @Override
    public void reboot(ZoneId zone, String hostname) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isReplaceable(ZoneId zone, List<HostName> hostnames) {
        return hasSpareCapacity;
    }

    /** Add or update given nodes in zone */
    public void putNodes(ZoneId zone, List<Node> nodes) {
        Map<HostName, Node> zoneNodes = nodeRepository.computeIfAbsent(zone, (k) -> new ConcurrentHashMap<>());
        for (var node : nodes) {
            zoneNodes.put(node.hostname(), node);
        }
    }

    /** Add or update given node in zone */
    public void putNodes(ZoneId zone, Node node) {
        putNodes(zone, List.of(node));
    }

    public void putApplication(ZoneId zone, Application application) {
        applications.computeIfAbsent(zone, (k) -> new TreeMap<>())
                    .put(application.id(), application);
    }

    public Pair<Double, Double> getTrafficFraction(ApplicationId application, ZoneId zone) {
        return trafficFractions.get(new DeploymentId(application, zone));
    }

    public BcpGroupInfo getBcpGroupInfo(ApplicationId application, ZoneId zone, ClusterSpec.Id cluster) {
        return bcpGroupInfos.get(new DeploymentClusterId(new DeploymentId(application, zone), cluster));
    }

    /** Remove given nodes from zone */
    public void removeNodes(ZoneId zone, List<Node> nodes) {
        nodes.forEach(node -> nodeRepository.get(zone).remove(node.hostname()));
    }

    /** Remove all nodes in all zones */
    public void clear() {
        nodeRepository.clear();
    }

    /** Add a fixed set of nodes to given zone */
    public void addFixedNodes(ZoneId zone) {
        var nodeA = Node.builder()
                        .hostname(HostName.of("hostA"))
                        .parentHostname(HostName.of("parentHostA"))
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
        var nodeB = Node.builder()
                        .hostname(HostName.of("hostB"))
                        .parentHostname(HostName.of("parentHostB"))
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
        putNodes(zone, List.of(nodeA, nodeB));
    }

    public void doUpgrade(DeploymentId deployment, Optional<HostName> hostName, Version version) {
        patchNodes(deployment, hostName, node -> {
            return Node.builder(node)
                       .currentVersion(version)
                       .currentDockerImage(node.wantedDockerImage())
                       .build();
        });
    }

    public void requestRestart(DeploymentId deployment, Optional<HostName> hostname) {
        patchNodes(deployment, hostname, node -> Node.builder(node).wantedRestartGeneration(node.wantedRestartGeneration() + 1).build());
    }

    public void doRestart(DeploymentId deployment, Optional<HostName> hostname) {
        patchNodes(deployment, hostname, node -> Node.builder(node).restartGeneration(node.restartGeneration() + 1).build());
    }

    public void requestReboot(DeploymentId deployment, Optional<HostName> hostname) {
        patchNodes(deployment, hostname, node -> Node.builder(node).wantedRebootGeneration(node.wantedRebootGeneration() + 1).build());
    }

    public void doReboot(DeploymentId deployment, Optional<HostName> hostname) {
        patchNodes(deployment, hostname, node -> Node.builder(node).rebootGeneration(node.rebootGeneration() + 1).build());
    }

    public NodeRepositoryMock allowPatching(boolean allowPatching) {
        this.allowPatching = allowPatching;
        return this;
    }

    public void hasSpareCapacity(boolean hasSpareCapacity) {
        this.hasSpareCapacity = hasSpareCapacity;
    }

    private Node require(ZoneId zone, String hostname) {
        return require(zone, HostName.of(hostname));
    }

    private Node require(ZoneId zone, HostName hostname) {
        Node node = nodeRepository.getOrDefault(zone, Map.of()).get(hostname);
        if (node == null) throw new IllegalArgumentException("Node not found in " + zone + ": " + hostname);
        return node;
    }

    private void patchNodes(ZoneId zone, String hostname, UnaryOperator<Node> patcher) {
        patchNodes(zone, Optional.of(HostName.of(hostname)), patcher);
    }

    private void patchNodes(DeploymentId deployment, Optional<HostName> hostname, UnaryOperator<Node> patcher) {
        patchNodes(deployment.zoneId(), hostname, patcher);
    }

    private void patchNodes(ZoneId zone, Optional<HostName> hostname, UnaryOperator<Node> patcher) {
        if (!allowPatching) throw new UnsupportedOperationException("Patching is disabled in this mock");
        List<Node> nodes;
        if (hostname.isPresent()) {
            nodes = List.of(require(zone, hostname.get()));
        } else {
            nodes = list(zone, NodeFilter.all());
        }
        putNodes(zone, nodes.stream().map(patcher).toList());
    }

    public record DeploymentClusterId(DeploymentId deploymentId, ClusterSpec.Id clusterId) {}

    public record BcpGroupInfo(double queryRate, double growthRateHeadroom, double cpuCostPerQuery) {}

}
