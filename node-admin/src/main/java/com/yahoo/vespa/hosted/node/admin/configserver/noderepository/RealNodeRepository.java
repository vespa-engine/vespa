// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.host.FlavorOverrides;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.configserver.HttpException;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings.GetAclResponse;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings.GetNodesResponse;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings.NodeMessageResponse;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings.NodeRepositoryNode;

import java.time.Instant;
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
                .collect(Collectors.toList());

        NodeMessageResponse response = configServerApi.post("/nodes/v2/node", nodesToPost, NodeMessageResponse.class);
        if (Strings.isNullOrEmpty(response.errorCode)) return;
        throw new NodeRepositoryException("Failed to add nodes: " + response.message + " " + response.errorCode);
    }

    @Override
    public List<NodeSpec> getNodes(String baseHostName) {
        String path = "/nodes/v2/node/?recursive=true&parentHost=" + baseHostName;
        final GetNodesResponse nodesForHost = configServerApi.get(path, GetNodesResponse.class);

        return nodesForHost.nodes.stream()
                .map(RealNodeRepository::createNodeSpec)
                .collect(Collectors.toList());
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

        // Group node ip-addresses by container hostname that trusts them
        Map<String, Set<Acl.Node>> trustedNodes = response.trustedNodes.stream()
                .collect(Collectors.groupingBy(
                        GetAclResponse.Node::getTrustedBy,
                        Collectors.mapping(
                                node -> new Acl.Node(node.hostname, node.ipAddress),
                                Collectors.toSet())));

        // Group trusted networks by container hostname that trusts them
        Map<String, Set<String>> trustedNetworks = response.trustedNetworks.stream()
                 .collect(Collectors.groupingBy(GetAclResponse.Network::getTrustedBy,
                                                Collectors.mapping(node -> node.network, Collectors.toSet())));


        // For each hostname create an ACL
        return Stream.of(trustedNodes.keySet(), trustedPorts.keySet(), trustedNetworks.keySet())
                     .flatMap(Set::stream)
                     .distinct()
                     .collect(Collectors.toMap(
                             Function.identity(),
                             hostname -> new Acl(trustedPorts.get(hostname), trustedNodes.get(hostname),
                                                 trustedNetworks.get(hostname))));
    }

    @Override
    public void updateNodeAttributes(String hostName, NodeAttributes nodeAttributes) {
        NodeMessageResponse response = configServerApi.patch(
                "/nodes/v2/node/" + hostName,
                nodeRepositoryNodeFromNodeAttributes(nodeAttributes),
                NodeMessageResponse.class);

        if (Strings.isNullOrEmpty(response.errorCode)) return;
        throw new NodeRepositoryException("Failed to update node attributes: " + response.message + " " + response.errorCode);
    }

    @Override
    public void setNodeState(String hostName, NodeState nodeState) {
        String state = nodeState.name();
        NodeMessageResponse response = configServerApi.put(
                "/nodes/v2/state/" + state + "/" + hostName,
                Optional.empty(), /* body */
                NodeMessageResponse.class);
        logger.info(response.message);

        if (Strings.isNullOrEmpty(response.errorCode)) return;
        throw new NodeRepositoryException("Failed to set node state: " + response.message + " " + response.errorCode);
    }

    private static NodeSpec createNodeSpec(NodeRepositoryNode node) {
        Objects.requireNonNull(node.type, "Unknown node type");
        NodeType nodeType = NodeType.valueOf(node.type);

        Objects.requireNonNull(node.state, "Unknown node state");
        NodeState nodeState = NodeState.valueOf(node.state);

        Optional<NodeMembership> membership = Optional.ofNullable(node.membership)
                .map(m -> new NodeMembership(m.clusterType, m.clusterId, m.group, m.index, m.retired));
        NodeReports reports = NodeReports.fromMap(Optional.ofNullable(node.reports).orElseGet(Map::of));
        return new NodeSpec(
                node.hostname,
                Optional.ofNullable(node.wantedDockerImage).map(DockerImage::fromString),
                Optional.ofNullable(node.currentDockerImage).map(DockerImage::fromString),
                nodeState,
                nodeType,
                node.flavor,
                Optional.ofNullable(node.wantedVespaVersion).map(Version::fromString),
                Optional.ofNullable(node.vespaVersion).map(Version::fromString),
                Optional.ofNullable(node.wantedOsVersion).map(Version::fromString),
                Optional.ofNullable(node.currentOsVersion).map(Version::fromString),
                Optional.ofNullable(node.allowedToBeDown),
                Optional.ofNullable(node.wantToDeprovision),
                Optional.ofNullable(node.owner).map(o -> ApplicationId.from(o.tenant, o.application, o.instance)),
                membership,
                Optional.ofNullable(node.restartGeneration),
                Optional.ofNullable(node.currentRestartGeneration),
                node.rebootGeneration,
                node.currentRebootGeneration,
                Optional.ofNullable(node.wantedFirmwareCheck).map(Instant::ofEpochMilli),
                Optional.ofNullable(node.currentFirmwareCheck).map(Instant::ofEpochMilli),
                Optional.ofNullable(node.modelName),
                new NodeResources(
                        node.minCpuCores,
                        node.minMainMemoryAvailableGb,
                        node.minDiskAvailableGb,
                        node.bandwidthGbps,
                        toDiskSpeed(node.fastDisk),
                        toStorageType(node.remoteStorage)),
                node.ipAddresses,
                node.additionalIpAddresses,
                reports,
                Optional.ofNullable(node.parentHostname));
    }

    private static NodeResources.DiskSpeed toDiskSpeed(Boolean fastDisk) {
        if (fastDisk == null) return NodeResources.DiskSpeed.any;
        if (fastDisk) return NodeResources.DiskSpeed.fast;
        else return NodeResources.DiskSpeed.slow;
    }

    private static NodeResources.StorageType toStorageType(Boolean remoteStorage) {
        if (remoteStorage == null) return NodeResources.StorageType.any;
        if (remoteStorage) return NodeResources.StorageType.remote;
        else return NodeResources.StorageType.local;
    }

    private static NodeRepositoryNode nodeRepositoryNodeFromAddNode(AddNode addNode) {
        NodeRepositoryNode node = new NodeRepositoryNode();
        node.openStackId = "fake-" + addNode.hostname;
        node.hostname = addNode.hostname;
        node.parentHostname = addNode.parentHostname.orElse(null);
        addNode.nodeFlavor.ifPresent(f -> node.flavor = f);
        addNode.flavorOverrides.flatMap(FlavorOverrides::diskGb).ifPresent(d -> node.minDiskAvailableGb = d);
        addNode.nodeResources.ifPresent(resources -> {
            node.minCpuCores = resources.vcpu();
            node.minMainMemoryAvailableGb = resources.memoryGb();
            node.minDiskAvailableGb = resources.diskGb();
            node.bandwidthGbps = resources.bandwidthGbps();
            node.fastDisk = resources.diskSpeed() == NodeResources.DiskSpeed.fast;
            node.remoteStorage = resources.storageType() == NodeResources.StorageType.remote;
        });
        node.type = addNode.nodeType.name();
        node.ipAddresses = addNode.ipAddresses;
        node.additionalIpAddresses = addNode.additionalIpAddresses;
        return node;
    }

    public static NodeRepositoryNode nodeRepositoryNodeFromNodeAttributes(NodeAttributes nodeAttributes) {
        NodeRepositoryNode node = new NodeRepositoryNode();
        node.currentDockerImage = nodeAttributes.getDockerImage().map(DockerImage::asString).orElse(null);
        node.currentRestartGeneration = nodeAttributes.getRestartGeneration().orElse(null);
        node.currentRebootGeneration = nodeAttributes.getRebootGeneration().orElse(null);
        node.vespaVersion = nodeAttributes.getVespaVersion().map(Version::toFullString).orElse(null);
        node.currentOsVersion = nodeAttributes.getCurrentOsVersion().map(Version::toFullString).orElse(null);
        node.currentFirmwareCheck = nodeAttributes.getCurrentFirmwareCheck().map(Instant::toEpochMilli).orElse(null);
        node.wantToDeprovision = nodeAttributes.getWantToDeprovision().orElse(null);

        Map<String, JsonNode> reports = nodeAttributes.getReports();
        node.reports = reports == null || reports.isEmpty() ? null : new TreeMap<>(reports);

        return node;
    }
}
