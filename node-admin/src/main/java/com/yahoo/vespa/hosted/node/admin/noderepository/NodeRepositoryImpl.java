// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.noderepository;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
import com.yahoo.vespa.hosted.node.admin.noderepository.bindings.GetNodesResponse;
import com.yahoo.vespa.hosted.node.admin.noderepository.bindings.NodeReadyResponse;
import com.yahoo.vespa.hosted.node.admin.noderepository.bindings.UpdateNodeAttributesRequestBody;
import com.yahoo.vespa.hosted.node.admin.noderepository.bindings.UpdateNodeAttributesResponse;
import com.yahoo.vespa.hosted.node.admin.util.ConfigServerHttpRequestExecutor;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author stiankri, dybis
 */
public class NodeRepositoryImpl implements NodeRepository {
    private static final PrefixLogger NODE_ADMIN_LOGGER = PrefixLogger.getNodeAdminLogger(NodeRepositoryImpl.class);
    private final String baseHostName;
    private final int port;
    private final ConfigServerHttpRequestExecutor requestExecutor;

    public NodeRepositoryImpl(Set<HostName> configServerHosts, int configPort, String baseHostName) {
        this.baseHostName = baseHostName;
        this.port = configPort;
        this.requestExecutor = ConfigServerHttpRequestExecutor.create(configServerHosts);
    }

    @Override
    public List<ContainerNodeSpec> getContainersToRun() throws IOException {

        try {
            final GetNodesResponse nodesForHost = requestExecutor.get(
                    "/nodes/v2/node/?parentHost=" + baseHostName + "&recursive=true",
                    port,
                    GetNodesResponse.class);

            if (nodesForHost.nodes == null) {
                throw new IOException("Response didn't contain nodes element");
            }
            List<ContainerNodeSpec> nodes = new ArrayList<>(nodesForHost.nodes.size());
            for (GetNodesResponse.Node node : nodesForHost.nodes) {
                ContainerNodeSpec nodeSpec;
                try {
                    nodeSpec = createContainerNodeSpec(node);
                } catch (IllegalArgumentException | NullPointerException e) {
                    NODE_ADMIN_LOGGER.warning("Bad node received from node repo when requesting children of the "
                            + baseHostName + " host: " + node, e);
                    continue;
                }
                nodes.add(nodeSpec);
            }
            return nodes;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public Optional<ContainerNodeSpec> getContainerNodeSpec(HostName hostName) throws IOException {
        final GetNodesResponse nodeResponse = requestExecutor.get(
                "/nodes/v2/node/?hostname=" + hostName + "&recursive=true",
                port,
                GetNodesResponse.class);


        if (nodeResponse.nodes.size() == 0) {
            return Optional.empty();
        }
        if (nodeResponse.nodes.size() != 1) {
            throw new RuntimeException("Did not get data for one node using hostname=" + hostName.toString() + "\n" + nodeResponse.toString());
        }
        return Optional.of(createContainerNodeSpec(nodeResponse.nodes.get(0)));
    }

    private static ContainerNodeSpec createContainerNodeSpec(GetNodesResponse.Node node)
            throws IllegalArgumentException, NullPointerException {
        Objects.requireNonNull(node.nodeState, "Unknown node state");
        NodeState nodeState = NodeState.valueOf(node.nodeState.toUpperCase());
        if (nodeState == NodeState.ACTIVE) {
            Objects.requireNonNull(node.wantedDockerImage, "Unknown docker image for active node");
            Objects.requireNonNull(node.wantedRestartGeneration, "Unknown wantedRestartGeneration for active node");
            Objects.requireNonNull(node.currentRestartGeneration, "Unknown currentRestartGeneration for active node");
        }

        String hostName = Objects.requireNonNull(node.hostname, "hostname is null");

        return new ContainerNodeSpec(
                new HostName(hostName),
                Optional.ofNullable(node.wantedDockerImage).map(DockerImage::new),
                containerNameFromHostName(hostName),
                nodeState,
                Optional.ofNullable(node.wantedRestartGeneration),
                Optional.ofNullable(node.currentRestartGeneration),
                Optional.ofNullable(node.minCpuCores),
                Optional.ofNullable(node.minMainMemoryAvailableGb),
                Optional.ofNullable(node.minDiskAvailableGb));
    }

    public static ContainerName containerNameFromHostName(final String hostName) {
        return new ContainerName(hostName.split("\\.")[0]);
    }

    @Override
    public void updateNodeAttributes(
            final HostName hostName,
            final long restartGeneration,
            final DockerImage dockerImage,
            final String currentVespaVersion)
            throws IOException {

        UpdateNodeAttributesResponse response = requestExecutor.patch(
                "/nodes/v2/node/" + hostName,
                port,
                new UpdateNodeAttributesRequestBody(
                        restartGeneration,
                        dockerImage.asString(),
                        currentVespaVersion),
                UpdateNodeAttributesResponse.class);

        if (response.errorCode == null || response.errorCode.isEmpty()) {
            return;
        }
        throw new RuntimeException("Unexcpected message " + response.message + " " + response.errorCode);
    }
    
    @Override
    public void markAsReady(final HostName hostName) throws IOException {
        NodeReadyResponse response = requestExecutor.put(
                "/nodes/v2/state/ready/" + hostName,
                port,
                Optional.empty(), /* body */
                NodeReadyResponse.class);
        NODE_ADMIN_LOGGER.info(response.message);
    }
}
