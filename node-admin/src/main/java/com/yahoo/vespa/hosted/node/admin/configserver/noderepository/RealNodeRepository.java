// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.WireguardKey;
import com.yahoo.config.provision.host.FlavorOverrides;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.configserver.HttpException;
import com.yahoo.vespa.hosted.node.admin.configserver.StandardConfigServerResponse;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings.GetAclResponse;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings.GetNodesResponse;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings.GetWireguardResponse;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings.NodeRepositoryNode;
import com.yahoo.vespa.hosted.node.admin.task.util.network.VersionedIpAddress;
import com.yahoo.vespa.hosted.node.admin.wireguard.WireguardPeer;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author stiankri
 * @author dybis
 */
public class RealNodeRepository implements NodeRepository {
    private static final Logger logger = Logger.getLogger(RealNodeRepository.class.getName());

    private final ConfigServerApi configServerApi;

    public RealNodeRepository(ConfigServerApi configServerApi) {
        this.configServerApi = configServerApi;
    }

    @Override
    public void addNodes(List<AddNode> nodes) {
        List<NodeRepositoryNode> nodesToPost = nodes.stream()
                .map(RealNodeRepository::nodeRepositoryNodeFromAddNode)
                .toList();

        configServerApi.post("/nodes/v2/node", nodesToPost, StandardConfigServerResponse.class)
                       .throwOnError("Failed to add nodes");
    }

    @Override
    public List<NodeSpec> getNodes(String baseHostName) {
        String path = "/nodes/v2/node/?recursive=true&parentHost=" + baseHostName;
        final GetNodesResponse nodesForHost = configServerApi.get(path, GetNodesResponse.class);

        return nodesForHost.nodes.stream()
                .map(RealNodeRepository::createNodeSpec)
                .toList();
    }

    @Override
    public Optional<NodeSpec> getOptionalNode(String hostName) {
        try {
            NodeRepositoryNode nodeResponse = configServerApi.get("/nodes/v2/node/" + hostName,
                                                                  NodeRepositoryNode.class);

            return Optional.ofNullable(nodeResponse).map(RealNodeRepository::createNodeSpec);
        } catch (HttpException.NotFoundException | HttpException.ForbiddenException e) {
            // Return empty on 403 in addition to 404 as it likely means we're trying to access a node that
            // has been deleted. When a node is deleted, the parent-child relationship no longer exists and
            // authorization cannot be granted.
            return Optional.empty();
        }
    }

    /**
     * Get all ACLs that belongs to a hostname. Usually this is a parent host and all
     * ACLs for child nodes are returned.
     */
    @Override
    public Map<String, Acl> getAcls(String hostName) {
        String path = String.format("/nodes/v2/acl/%s?children=true", hostName);
        GetAclResponse response = configServerApi.get(path, GetAclResponse.class);

        // Group ports by container hostname that trusts them
        Map<String, Set<Integer>> trustedPorts = response.trustedPorts.stream()
                .collect(Collectors.groupingBy(
                        GetAclResponse.Port::getTrustedBy,
                        Collectors.mapping(port -> port.port, Collectors.toSet())));

        // Group UDP ports by container hostname that trusts them
        Map<String, Set<Integer>> trustedUdpPorts = response.trustedUdpPorts.stream()
                .collect(Collectors.groupingBy(
                        GetAclResponse.Port::getTrustedBy,
                        Collectors.mapping(port -> port.port, Collectors.toSet())));

        // Group node ip-addresses by container hostname that trusts them
        Map<String, Set<Acl.Node>> trustedNodes = response.trustedNodes.stream()
                .collect(Collectors.groupingBy(
                        GetAclResponse.Node::getTrustedBy,
                        Collectors.mapping(
                                node -> new Acl.Node(node.hostname, node.ipAddress, Set.copyOf(node.ports)),
                                Collectors.toSet())));

        // Group trusted networks by container hostname that trusts them
        Map<String, Set<String>> trustedNetworks = response.trustedNetworks.stream()
                 .collect(Collectors.groupingBy(GetAclResponse.Network::getTrustedBy,
                                                Collectors.mapping(node -> node.network, Collectors.toSet())));


        // For each hostname create an ACL
        return Stream.of(trustedNodes.keySet(), trustedPorts.keySet(), trustedUdpPorts.keySet(), trustedNetworks.keySet())
                     .flatMap(Set::stream)
                     .distinct()
                     .collect(Collectors.toMap(
                             Function.identity(),
                             hostname -> new Acl(trustedPorts.get(hostname),
                                                 trustedUdpPorts.get(hostname),
                                                 trustedNodes.get(hostname),
                                                 trustedNetworks.get(hostname))));
    }

    @Override
    public List<WireguardPeer> getExclavePeers() {
        String path = "/nodes/v2/node/?recursive=true&enclave=true";
        final GetNodesResponse response = configServerApi.get(path, GetNodesResponse.class);

        return response.nodes.stream()
                .filter(node -> node.wireguardPubkey != null && ! node.wireguardPubkey.isEmpty())
                .map(RealNodeRepository::createTenantPeer)
                .sorted()
                .toList();
    }

    @Override
    public List<WireguardPeer> getConfigserverPeers() {
        GetWireguardResponse response = configServerApi.get("/nodes/v2/wireguard", GetWireguardResponse.class);
        return response.configservers.stream()
                .map(RealNodeRepository::createConfigserverPeer)
                .sorted(Comparator.comparing(WireguardPeer::hostname))
                .toList();
    }

    @Override
    public void updateNodeAttributes(String hostName, NodeAttributes nodeAttributes) {
        configServerApi.patch("/nodes/v2/node/" + hostName,
                              nodeRepositoryNodeFromNodeAttributes(nodeAttributes),
                              StandardConfigServerResponse.class)
                       .throwOnError("Failed to update node attributes");
    }

    @Override
    public void setNodeState(String hostName, NodeState nodeState) {
        String state = nodeState.name();
        StandardConfigServerResponse response = configServerApi.put("/nodes/v2/state/" + state + "/" + hostName,
                                                                    Optional.empty(), /* body */
                                                                    StandardConfigServerResponse.class);
        logger.info(response.message);
        response.throwOnError("Failed to set node state");
    }

    @Override
    public void reboot(String hostname) {
        String uri = "/nodes/v2/command/reboot?hostname=" + hostname;
        StandardConfigServerResponse response = configServerApi.post(uri, Optional.empty(), StandardConfigServerResponse.class);
        logger.info(response.message);
        response.throwOnError("Failed to reboot " + hostname);
    }

    private static NodeSpec createNodeSpec(NodeRepositoryNode node) {
        Objects.requireNonNull(node.type, "Unknown node type");
        NodeType nodeType = NodeType.valueOf(node.type);

        Objects.requireNonNull(node.state, "Unknown node state");
        NodeState nodeState = NodeState.valueOf(node.state);

        Optional<NodeMembership> membership = Optional.ofNullable(node.membership)
                .map(m -> new NodeMembership(m.clusterType, m.clusterId, m.group, m.index, m.retired));
        NodeReports reports = NodeReports.fromMap(Optional.ofNullable(node.reports).orElseGet(Map::of));
        List<Event> events = node.history.stream()
                .map(event -> new Event(event.agent, event.event, Optional.ofNullable(event.at).map(Instant::ofEpochMilli).orElse(Instant.EPOCH)))
                .toList();

        List<TrustStoreItem> trustStore = Optional.ofNullable(node.trustStore).orElse(List.of()).stream()
                .map(item -> new TrustStoreItem(item.fingerprint, Instant.ofEpochMilli(item.expiry)))
                .toList();


        return new NodeSpec(
                node.hostname,
                node.id,
                Optional.ofNullable(node.wantedDockerImage).map(DockerImage::fromString),
                Optional.ofNullable(node.currentDockerImage).map(DockerImage::fromString),
                nodeState,
                nodeType,
                Optional.ofNullable(node.cloudAccount).map(CloudAccount::from).orElse(CloudAccount.empty),
                node.flavor,
                Optional.ofNullable(node.wantedVespaVersion).map(Version::fromString),
                Optional.ofNullable(node.vespaVersion).map(Version::fromString),
                Optional.ofNullable(node.wantedOsVersion).map(Version::fromString),
                Optional.ofNullable(node.currentOsVersion).map(Version::fromString),
                Optional.ofNullable(node.orchestratorStatus).map(OrchestratorStatus::fromString).orElse(OrchestratorStatus.NO_REMARKS),
                Optional.ofNullable(node.owner).map(o -> ApplicationId.from(o.tenant, o.application, o.instance)),
                membership,
                Optional.ofNullable(node.restartGeneration),
                Optional.ofNullable(node.currentRestartGeneration),
                node.rebootGeneration,
                node.currentRebootGeneration,
                Optional.ofNullable(node.wantedFirmwareCheck).map(Instant::ofEpochMilli),
                Optional.ofNullable(node.currentFirmwareCheck).map(Instant::ofEpochMilli),
                Optional.ofNullable(node.modelName),
                nodeResources(node.resources),
                nodeResources(node.realResources),
                node.ipAddresses,
                node.additionalIpAddresses,
                reports,
                events,
                Optional.ofNullable(node.parentHostname),
                Optional.ofNullable(node.archiveUri).map(URI::create),
                Optional.ofNullable(node.exclusiveTo).map(ApplicationId::fromSerializedForm),
                trustStore,
                Optional.ofNullable(node.wireguardPubkey).map(WireguardKey::from),
                node.wantToRebuild);
    }

    private static NodeResources nodeResources(NodeRepositoryNode.NodeResources nodeResources) {
        return new NodeResources(
                nodeResources.vcpu,
                nodeResources.memoryGb,
                nodeResources.diskGb,
                nodeResources.bandwidthGbps,
                diskSpeedFromString(nodeResources.diskSpeed),
                storageTypeFromString(nodeResources.storageType),
                architectureFromString(nodeResources.architecture),
                gpuResourcesFrom(nodeResources));
    }

    private static NodeResources.GpuResources gpuResourcesFrom(NodeRepositoryNode.NodeResources nodeResources) {
        if (nodeResources.gpuCount == null || nodeResources.gpuMemoryGb == null) return NodeResources.GpuResources.zero();
        return new NodeResources.GpuResources(nodeResources.gpuCount, nodeResources.gpuMemoryGb);
    }

    private static NodeResources.DiskSpeed diskSpeedFromString(String diskSpeed) {
        if (diskSpeed == null) return NodeResources.DiskSpeed.getDefault();
        return switch (diskSpeed) {
            case "fast" -> NodeResources.DiskSpeed.fast;
            case "slow" -> NodeResources.DiskSpeed.slow;
            case "any" -> NodeResources.DiskSpeed.any;
            default -> throw new IllegalArgumentException("Unknown disk speed '" + diskSpeed + "'");
        };
    }

    private static NodeResources.StorageType storageTypeFromString(String storageType) {
        if (storageType == null) return NodeResources.StorageType.getDefault();
        return switch (storageType) {
            case "remote" -> NodeResources.StorageType.remote;
            case "local" -> NodeResources.StorageType.local;
            case "any" -> NodeResources.StorageType.any;
            default -> throw new IllegalArgumentException("Unknown storage type '" + storageType + "'");
        };
    }

    private static NodeResources.Architecture architectureFromString(String architecture) {
        if (architecture == null) return NodeResources.Architecture.getDefault();
        return switch (architecture) {
            case "arm64" -> NodeResources.Architecture.arm64;
            case "x86_64" -> NodeResources.Architecture.x86_64;
            case "any" -> NodeResources.Architecture.any;
            default -> throw new IllegalArgumentException("Unknown architecture '" + architecture + "'");
        };
    }

    private static String toString(NodeResources.DiskSpeed diskSpeed) {
        return switch (diskSpeed) {
            case fast -> "fast";
            case slow -> "slow";
            case any -> "any";
        };
    }

    private static String toString(NodeResources.StorageType storageType) {
        return switch (storageType) {
            case remote -> "remote";
            case local -> "local";
            case any -> "any";
        };
    }

    private static String toString(NodeResources.Architecture architecture) {
        return switch (architecture) {
            case arm64 -> "arm64";
            case x86_64 -> "x86_64";
            case any -> "any";
        };
    }

    private static NodeRepositoryNode nodeRepositoryNodeFromAddNode(AddNode addNode) {
        NodeRepositoryNode node = new NodeRepositoryNode();
        node.id = addNode.id;
        node.hostname = addNode.hostname;
        node.parentHostname = addNode.parentHostname.orElse(null);
        addNode.nodeFlavor.ifPresent(f -> node.flavor = f);
        addNode.flavorOverrides.flatMap(FlavorOverrides::diskGb).ifPresent(d -> {
            node.resources = new NodeRepositoryNode.NodeResources();
            node.resources.diskGb = d;
        });
        addNode.nodeResources.ifPresent(resources -> {
            node.resources = new NodeRepositoryNode.NodeResources();
            node.resources.vcpu = resources.vcpu();
            node.resources.memoryGb = resources.memoryGb();
            node.resources.diskGb = resources.diskGb();
            node.resources.bandwidthGbps = resources.bandwidthGbps();
            node.resources.diskSpeed = toString(resources.diskSpeed());
            node.resources.storageType = toString(resources.storageType());
            node.resources.architecture = toString(resources.architecture());
            if (!resources.gpuResources().isZero()) {
                node.resources.gpuCount = resources.gpuResources().count();
                node.resources.gpuMemoryGb = resources.gpuResources().memoryGb();
            }
        });
        node.type = addNode.nodeType.name();
        node.ipAddresses = addNode.ipAddresses;
        node.additionalIpAddresses = addNode.additionalIpAddresses;
        return node;
    }

    public static NodeRepositoryNode nodeRepositoryNodeFromNodeAttributes(NodeAttributes nodeAttributes) {
        NodeRepositoryNode node = new NodeRepositoryNode();
        node.id = nodeAttributes.getHostId().orElse(null);
        node.currentDockerImage = nodeAttributes.getDockerImage().map(DockerImage::asString).orElse(null);
        node.currentRestartGeneration = nodeAttributes.getRestartGeneration().orElse(null);
        node.currentRebootGeneration = nodeAttributes.getRebootGeneration().orElse(null);
        node.vespaVersion = nodeAttributes.getVespaVersion().map(Version::toFullString).orElse(null);
        node.currentOsVersion = nodeAttributes.getCurrentOsVersion().map(Version::toFullString).orElse(null);
        node.currentFirmwareCheck = nodeAttributes.getCurrentFirmwareCheck().map(Instant::toEpochMilli).orElse(null);
        node.trustStore = nodeAttributes.getTrustStore().stream()
                .map(item -> new NodeRepositoryNode.TrustStoreItem(item.fingerprint(), item.expiry().toEpochMilli()))
                .toList();
        node.wireguardPubkey = nodeAttributes.getWireguardPubkey().map(WireguardKey::value).orElse(null);
        Map<String, JsonNode> reports = nodeAttributes.getReports();
        node.reports = reports == null || reports.isEmpty() ? null : new TreeMap<>(reports);

        return node;
    }

    private static WireguardPeer createTenantPeer(NodeRepositoryNode node) {
        return new WireguardPeer(HostName.of(node.hostname),
                                 node.ipAddresses.stream().map(VersionedIpAddress::from).toList(),
                                 WireguardKey.from(node.wireguardPubkey));
    }

    private static WireguardPeer createConfigserverPeer(GetWireguardResponse.Configserver configServer) {
        return new WireguardPeer(HostName.of(configServer.hostname),
                                 configServer.ipAddresses.stream().map(VersionedIpAddress::from).toList(),
                                 WireguardKey.from(configServer.wireguardPubkey));
    }

}
