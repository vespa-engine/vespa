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
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.jaxrs.client.JaxRsClientFactory;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategy;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategyFactory;
import com.yahoo.vespa.jaxrs.client.JerseyJaxRsClientFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author stiankri
 */
public class NodeRepositoryImpl implements NodeRepository {
    private static final Logger logger = Logger.getLogger(NodeRepositoryImpl.class.getName());
    private static final int HARDCODED_NODEREPOSITORY_PORT = 19071;
    private static final String NODEREPOSITORY_PATH_PREFIX_NODES_API = "/";
    private static final String ENV_HOSTNAME = "HOSTNAME";

    private JaxRsStrategy<NodeRepositoryApi> nodeRepositoryClient;
    private final String baseHostName;

    public NodeRepositoryImpl() {
        baseHostName = Optional.ofNullable(System.getenv(ENV_HOSTNAME))
                .orElseThrow(() -> new IllegalStateException("Environment variable " + ENV_HOSTNAME + " unset"));
        nodeRepositoryClient = getApi();
    }

    // For testing
    NodeRepositoryImpl(String baseHostName, String configserver, int configport) {
        this.baseHostName = baseHostName;
        final Set<HostName> configServerHosts = new HashSet<>();
        configServerHosts.add(new HostName(configserver));

        final JaxRsClientFactory jaxRsClientFactory = new JerseyJaxRsClientFactory();
        final JaxRsStrategyFactory jaxRsStrategyFactory = new JaxRsStrategyFactory(
                configServerHosts, configport, jaxRsClientFactory);
        nodeRepositoryClient =  jaxRsStrategyFactory.apiWithRetries(NodeRepositoryApi.class, NODEREPOSITORY_PATH_PREFIX_NODES_API);
    }

    private static JaxRsStrategy<NodeRepositoryApi> getApi() {
        final Set<HostName> configServerHosts = Environment.getConfigServerHostsFromYinstSetting();
        if (configServerHosts.isEmpty()) {
            throw new IllegalStateException("Environment setting for config servers missing or empty.");
        }
        final JaxRsClientFactory jaxRsClientFactory = new JerseyJaxRsClientFactory();
        final JaxRsStrategyFactory jaxRsStrategyFactory = new JaxRsStrategyFactory(
                configServerHosts, HARDCODED_NODEREPOSITORY_PORT, jaxRsClientFactory);
        return jaxRsStrategyFactory.apiWithRetries(NodeRepositoryApi.class, NODEREPOSITORY_PATH_PREFIX_NODES_API);
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
                logger.log(LogLevel.WARNING, "Bad node received from node repo when requesting children of the "
                        + baseHostName + " host: " + node, e);
                continue;
            }

            nodes.add(nodeSpec);
        }

        return nodes;
    }

    @Override
    public Optional<ContainerNodeSpec> getContainer(HostName hostname) throws IOException {
        // TODO Use proper call to node repository
        return getContainersToRun().stream()
                .filter(cns -> Objects.equals(hostname, cns.hostname))
                .findFirst();
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

    private static ContainerName containerNameFromHostName(final String hostName) {
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
        // TODO: Error handling.
        nodeRepositoryClient.apply(nodeRepositoryApi ->
                nodeRepositoryApi.updateNodeAttributes(
                        hostName.s(),
                        new UpdateNodeAttributesRequestBody(
                                restartGeneration, dockerImage.asString(), currentVespaVersion)));
    }

    @Override
    public void markAsReady(final HostName hostName) throws IOException {
        nodeRepositoryClient.apply(nodeRepositoryApi -> nodeRepositoryApi.setReady(hostName.s(), ""));
    }
}
