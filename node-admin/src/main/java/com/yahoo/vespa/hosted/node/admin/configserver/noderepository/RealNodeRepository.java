// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.google.common.net.InetAddresses;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.configserver.HttpException;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings.*;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.Acl;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import com.yahoo.vespa.hosted.provision.Node;
import org.apache.commons.lang.StringUtils;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
    public List<NodeSpec> getNodes(String baseHostName) {
        return getNodes(Optional.of(baseHostName), Collections.emptyList());
    }

    private List<NodeSpec> getNodes(Optional<String> baseHostName, List<NodeType> nodeTypeList) {
        Optional<String> nodeTypes = Optional
                .of(nodeTypeList.stream().map(NodeType::name).collect(Collectors.joining(",")))
                .filter(StringUtils::isNotEmpty);

        String path = "/nodes/v2/node/?recursive=true" +
                baseHostName.map(base -> "&parentHost=" + base).orElse("") +
                nodeTypes.map(types -> "&type=" + types).orElse("");
        final GetNodesResponse nodesForHost = configServerApi.get(path, GetNodesResponse.class);

        return nodesForHost.nodes.stream()
                .map(RealNodeRepository::createNodeRepositoryNode)
                .collect(Collectors.toList());
    }


    @Override
    public List<NodeSpec> getNodes(NodeType... nodeTypes) {
        if (nodeTypes.length == 0)
            throw new IllegalArgumentException("Must specify at least 1 node type");

        return getNodes(Optional.empty(), Arrays.asList(nodeTypes));
    }

    @Override
    public Optional<NodeSpec> getNode(String hostName) {
        try {
            GetNodesResponse.Node nodeResponse = configServerApi.get("/nodes/v2/node/" + hostName,
                    GetNodesResponse.Node.class);
            if (nodeResponse == null) {
                return Optional.empty();
            }
            return Optional.of(createNodeRepositoryNode(nodeResponse));
        } catch (HttpException.NotFoundException | HttpException.ForbiddenException e) {
            // Return empty on 403 in addition to 404 as it likely means we're trying to access a node that
            // has been deleted. When a node is deleted, the parent-child relationship no longer exists and
            // authorization cannot be granted.
            return Optional.empty();
        }
    }

    @Override
    public Map<Container, Acl> getAcl(String hostName, List<Container> containers) {
        Map<Container, Acl> acls = new HashMap<>();
        try {
            final String path = String.format("/nodes/v2/acl/%s?children=true", hostName);
            final GetAclResponse response = configServerApi.get(path, GetAclResponse.class);

            // Group ports by container hostname that trusts them
            Map<String, List<GetAclResponse.Port>> trustedPorts = response.trustedPorts.stream()
                    .collect(Collectors.groupingBy(GetAclResponse.Port::getTrustedBy));

            // Group nodes by container hostname that trusts them
            Map<String, List<GetAclResponse.Node>> trustedNodes = response.trustedNodes.stream()
                    .collect(Collectors.groupingBy(GetAclResponse.Node::getTrustedBy));

            // For each container create an ACL - use empty lists if no trusted ports or nodes are found
            containers.forEach(container -> acls.put(container,
                    new Acl(trustedPorts.getOrDefault(container.hostname, new ArrayList<>())
                            .stream().map(port -> port.port)
                            .collect(Collectors.toList()), trustedNodes.getOrDefault(container.hostname, new ArrayList<>())
                            .stream().map(node -> InetAddresses.forString(node.ipAddress))
                            .collect(Collectors.toList()))));

        } catch (HttpException.NotFoundException e) {
            NODE_ADMIN_LOGGER.warning("Failed to fetch ACLs for " + hostName + " No ACL will be applied");
        }

        return acls;
    }

    private static NodeSpec createNodeRepositoryNode(GetNodesResponse.Node node)
            throws IllegalArgumentException, NullPointerException {
        Objects.requireNonNull(node.nodeType, "Unknown node type");
        NodeType nodeType = NodeType.valueOf(node.nodeType);

        Objects.requireNonNull(node.nodeState, "Unknown node state");
        Node.State nodeState = Node.State.valueOf(node.nodeState);
        if (nodeState == Node.State.active) {
            Objects.requireNonNull(node.wantedVespaVersion, "Unknown vespa version for active node");
            Objects.requireNonNull(node.wantedDockerImage, "Unknown docker image for active node");
            Objects.requireNonNull(node.wantedRestartGeneration, "Unknown wantedRestartGeneration for active node");
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
                node.nodeFlavor,
                node.nodeCanonicalFlavor,
                Optional.ofNullable(node.wantedVespaVersion),
                Optional.ofNullable(node.vespaVersion),
                Optional.ofNullable(node.allowedToBeDown),
                Optional.ofNullable(owner),
                Optional.ofNullable(membership),
                Optional.ofNullable(node.wantedRestartGeneration),
                Optional.ofNullable(node.currentRestartGeneration),
                node.wantedRebootGeneration,
                node.currentRebootGeneration,
                node.minCpuCores,
                node.minMainMemoryAvailableGb,
                node.minDiskAvailableGb,
                node.fastDisk,
                node.ipAddresses,
                Optional.ofNullable(node.hardwareDivergence),
                Optional.ofNullable(node.parentHostname));
    }

    @Override
    public void updateNodeAttributes(final String hostName, final NodeAttributes nodeAttributes) {
        UpdateNodeAttributesResponse response = configServerApi.patch(
                "/nodes/v2/node/" + hostName,
                new UpdateNodeAttributesRequestBody(nodeAttributes),
                UpdateNodeAttributesResponse.class);

        if (response.errorCode == null || response.errorCode.isEmpty()) {
            return;
        }
        throw new RuntimeException("Unexpected message " + response.message + " " + response.errorCode);
    }

    @Override
    public void setNodeState(String hostName, Node.State nodeState) {
        String state = nodeState == Node.State.ready ? "availablefornewallocations" : nodeState.name();
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
}
