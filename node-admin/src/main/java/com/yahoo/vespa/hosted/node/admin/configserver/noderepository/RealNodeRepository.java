// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.google.common.base.Strings;
import com.google.common.net.InetAddresses;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.configserver.HttpException;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings.*;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.Acl;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import com.yahoo.vespa.hosted.provision.Node;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author stiankri, dybis
 */
public class RealNodeRepository implements NodeRepository {
    private static final PrefixLogger NODE_ADMIN_LOGGER = PrefixLogger.getNodeAdminLogger(RealNodeRepository.class);

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
        if (!Strings.isNullOrEmpty(response.errorCode)) {
            throw new RuntimeException("Failed to add nodes to node-repo: " + response.message + " " + response.errorCode);
        }
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
    public Optional<NodeSpec> getNode(String hostName) {
        try {
            NodeRepositoryNode nodeResponse = configServerApi.get("/nodes/v2/node/" + hostName,
                    NodeRepositoryNode.class);
            if (nodeResponse == null) {
                return Optional.empty();
            }
            return Optional.of(createNodeSpec(nodeResponse));
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
        Map<String, Acl> acls = new HashMap<>();
        try {
            final String path = String.format("/nodes/v2/acl/%s?children=true", hostName);
            final GetAclResponse response = configServerApi.get(path, GetAclResponse.class);

            // Group ports by container hostname that trusts them
            Map<String, List<GetAclResponse.Port>> trustedPorts = response.trustedPorts.stream()
                    .collect(Collectors.groupingBy(GetAclResponse.Port::getTrustedBy));

            // Group nodes by container hostname that trusts them
            Map<String, List<GetAclResponse.Node>> trustedNodes = response.trustedNodes.stream()
                    .collect(Collectors.groupingBy(GetAclResponse.Node::getTrustedBy));

            // For each hostname create an ACL
            Stream.of(trustedNodes.keySet(), trustedPorts.keySet())
                    .flatMap(Set::stream)
                    .distinct()
                    .forEach(hostname -> acls.put(hostname,
                            new Acl(
                                    trustedPorts.getOrDefault(hostname, new ArrayList<>())
                                            .stream().map(port -> port.port)
                                            .collect(Collectors.toList()),

                                    trustedNodes.getOrDefault(hostname, new ArrayList<>())
                                            .stream().map(node -> InetAddresses.forString(node.ipAddress))
                                            .collect(Collectors.toList()))));

        } catch (HttpException.NotFoundException e) {
            NODE_ADMIN_LOGGER.warning("Failed to fetch ACLs for " + hostName + " No ACL will be applied");
        }

        return acls;
    }

    @Override
    public void updateNodeAttributes(String hostName, NodeAttributes nodeAttributes) {
        NodeMessageResponse response = configServerApi.patch(
                "/nodes/v2/node/" + hostName,
                nodeRepositoryNodeFromNodeAttributes(nodeAttributes),
                NodeMessageResponse.class);

        if (!Strings.isNullOrEmpty(response.errorCode)) {
            throw new RuntimeException("Unexpected message " + response.message + " " + response.errorCode);
        }
    }

    @Override
    public void setNodeState(String hostName, Node.State nodeState) {
        String state = nodeState.name();
        NodeMessageResponse response = configServerApi.put(
                "/nodes/v2/state/" + state + "/" + hostName,
                Optional.empty(), /* body */
                NodeMessageResponse.class);
        NODE_ADMIN_LOGGER.info(response.message);

        if (response.errorCode == null || response.errorCode.isEmpty()) {
            return;
        }
        throw new RuntimeException("Unexpected message " + response.message + " " + response.errorCode);
    }


    private static NodeSpec createNodeSpec(NodeRepositoryNode node)
            throws IllegalArgumentException, NullPointerException {
        Objects.requireNonNull(node.type, "Unknown node type");
        NodeType nodeType = NodeType.valueOf(node.type);

        Objects.requireNonNull(node.state, "Unknown node state");
        Node.State nodeState = Node.State.valueOf(node.state);
        if (nodeState == Node.State.active) {
            Objects.requireNonNull(node.wantedVespaVersion, "Unknown vespa version for active node");
            Objects.requireNonNull(node.wantedDockerImage, "Unknown docker image for active node");
            Objects.requireNonNull(node.restartGeneration, "Unknown restartGeneration for active node");
            Objects.requireNonNull(node.currentRestartGeneration, "Unknown currentRestartGeneration for active node");
        }

        String hostName = Objects.requireNonNull(node.hostname, "hostname is null");

        NodeSpec.Owner owner = null;
        if (node.owner != null) {
            owner = new NodeSpec.Owner(node.owner.tenant, node.owner.application, node.owner.instance);
        }

        NodeSpec.Membership membership = null;
        if (node.membership != null) {
            membership = new NodeSpec.Membership(node.membership.clusterType, node.membership.clusterId,
                    node.membership.group, node.membership.index, node.membership.retired);
        }

        return new NodeSpec(
                hostName,
                Optional.ofNullable(node.wantedDockerImage).map(DockerImage::new),
                Optional.ofNullable(node.currentDockerImage).map(DockerImage::new),
                nodeState,
                nodeType,
                node.flavor,
                node.canonicalFlavor,
                Optional.ofNullable(node.wantedVespaVersion),
                Optional.ofNullable(node.vespaVersion),
                Optional.ofNullable(node.allowedToBeDown),
                Optional.ofNullable(owner),
                Optional.ofNullable(membership),
                Optional.ofNullable(node.restartGeneration),
                Optional.ofNullable(node.currentRestartGeneration),
                node.rebootGeneration,
                node.currentRebootGeneration,
                node.minCpuCores,
                node.minMainMemoryAvailableGb,
                node.minDiskAvailableGb,
                node.fastDisk,
                node.ipAddresses,
                Optional.ofNullable(node.hardwareDivergence),
                Optional.ofNullable(node.parentHostname));
    }

    private static NodeRepositoryNode nodeRepositoryNodeFromAddNode(AddNode addNode) {
        NodeRepositoryNode node = new NodeRepositoryNode();
        node.openStackId = "fake-" + addNode.hostname;
        node.hostname = addNode.hostname;
        node.parentHostname = addNode.parentHostname.orElse(null);
        node.flavor = addNode.nodeFlavor;
        node.type = addNode.nodeType.name();
        node.ipAddresses = addNode.ipAddresses;
        node.additionalIpAddresses = addNode.additionalIpAddresses;
        return node;
    }

    private static NodeRepositoryNode nodeRepositoryNodeFromNodeAttributes(NodeAttributes nodeAttributes) {
        NodeRepositoryNode node = new NodeRepositoryNode();
        node.currentDockerImage = Optional.ofNullable(nodeAttributes.getDockerImage()).map(DockerImage::asString).orElse(null);
        node.currentRestartGeneration = nodeAttributes.getRestartGeneration();
        node.currentRebootGeneration = nodeAttributes.getRebootGeneration();
        node.hardwareDivergence = nodeAttributes.getHardwareDivergence();
        return node;
    }
}
