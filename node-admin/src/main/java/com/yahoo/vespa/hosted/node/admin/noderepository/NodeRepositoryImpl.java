// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.noderepository;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
import com.yahoo.vespa.hosted.node.admin.noderepository.bindings.GetNodesResponse;
import com.yahoo.vespa.hosted.node.admin.noderepository.bindings.NodeRepositoryApi;
import com.yahoo.vespa.hosted.node.admin.noderepository.bindings.UpdateNodeAttributesRequestBody;
import com.yahoo.vespa.hosted.node.admin.noderepository.bindings.UpdateNodeAttributesResponse;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import com.yahoo.vespa.jaxrs.client.JaxRsClientFactory;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategy;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategyFactory;
import com.yahoo.vespa.jaxrs.client.JerseyJaxRsClientFactory;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author stiankri
 */
public class NodeRepositoryImpl implements NodeRepository {
    private static final PrefixLogger NODE_ADMIN_LOGGER = PrefixLogger.getNodeAdminLogger(NodeRepositoryImpl.class);
    private static final String NODEREPOSITORY_PATH_PREFIX_NODES_API = "/";

    private JaxRsStrategy<NodeRepositoryApi> nodeRepositoryClient;
    private final String baseHostName;

    public NodeRepositoryImpl(Set<HostName> configServerHosts, int configPort, String baseHostName) {
        final JaxRsClientFactory jaxRsClientFactory = new JerseyJaxRsClientFactory();
        final JaxRsStrategyFactory jaxRsStrategyFactory = new JaxRsStrategyFactory(
                configServerHosts, configPort, jaxRsClientFactory);
        this.nodeRepositoryClient = jaxRsStrategyFactory.apiWithRetries(
                NodeRepositoryApi.class, NODEREPOSITORY_PATH_PREFIX_NODES_API);
        this.baseHostName = baseHostName;
    }

    @Override
    public List<ContainerNodeSpec> getContainersToRun() throws IOException {
        final GetNodesResponse nodesForHost = nodeRepositoryClient.apply(nodeRepositoryApi ->
                nodeRepositoryApi.getNodesWithParentHost(baseHostName, true));

        if (nodesForHost.nodes == null) {
            throw new IOException("Response didn't contain nodes element");
        }

        List<ContainerNodeSpec> nodes = new ArrayList<>(nodesForHost.nodes.size());
        for (GetNodesResponse.Node node : nodesForHost.nodes) {
            ContainerNodeSpec nodeSpec;
            try {
                nodeSpec = createContainerNodeSpec(node);
            } catch (IllegalArgumentException | NullPointerException e) {
                NODE_ADMIN_LOGGER.log(LogLevel.WARNING, "Bad node received from node repo when requesting children of the "
                        + baseHostName + " host: " + node, e);
                continue;
            }

            nodes.add(nodeSpec);
        }

        return nodes;
    }

    @Override
    public Optional<ContainerNodeSpec> getContainerNodeSpec(HostName hostName) throws IOException {
        final GetNodesResponse response = nodeRepositoryClient.apply(nodeRepositoryApi -> nodeRepositoryApi.getNode(hostName.toString(), true));
        if (response.nodes.size() == 0) {
            return Optional.empty();
        }
        if (response.nodes.size() != 1) {
            throw new RuntimeException("Did not get data for one node using hostname=" + hostName.toString() + "\n" + response.toString());
        }
        return Optional.of(createContainerNodeSpec(response.nodes.get(0)));
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
        // TODO: Filter out redundant (repeated) invocations with the same values.
        try {
            nodeRepositoryClient.apply(nodeRepositoryApi ->
                                               nodeRepositoryApi.updateNodeAttributes(
                                                       hostName.s(),
                                                       new UpdateNodeAttributesRequestBody(
                                                               restartGeneration,
                                                               dockerImage.asString(),
                                                               currentVespaVersion)));
        } catch (javax.ws.rs.WebApplicationException e) {
            final Response response = e.getResponse();
            UpdateNodeAttributesResponse updateResponse = response.readEntity(UpdateNodeAttributesResponse.class);
            PrefixLogger logger = PrefixLogger.getNodeAgentLogger(NodeRepositoryImpl.class,
                    containerNameFromHostName(hostName.toString()));
            logger.log(LogLevel.ERROR, "Response code " + response.getStatus() + ": " + updateResponse.message);
            throw new RuntimeException("Failed to update node attributes for " + hostName.s() + ":" + updateResponse.message);
        }
    }

    @Override
    public void markAsReady(final HostName hostName) throws IOException {
        nodeRepositoryClient.apply(nodeRepositoryApi -> nodeRepositoryApi.setReady(hostName.s(), ""));
    }
}
