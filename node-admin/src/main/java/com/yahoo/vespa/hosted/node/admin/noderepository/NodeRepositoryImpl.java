// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.noderepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
import com.yahoo.vespa.hosted.node.admin.noderepository.bindings.GetNodesResponse;
import com.yahoo.vespa.hosted.node.admin.noderepository.bindings.UpdateNodeAttributesRequestBody;
import com.yahoo.vespa.hosted.node.admin.noderepository.bindings.UpdateNodeAttributesResponse;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
    private final Set<HostName> configservers;
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClientBuilder.create().build();

    public NodeRepositoryImpl(Set<HostName> configServerHosts, int configPort, String baseHostName) {
        this.baseHostName = baseHostName;
        this.configservers = configServerHosts;
        this.port = configPort;
    }

    interface CreateRequest {
        HttpUriRequest createRequest(HostName configserver);
    }

    private <T extends  Object> T tryAllConfigServers(CreateRequest requestFactory, Class<T> wantedReturnType) throws IOException {
        for (HostName configServer : configservers) {
            final HttpResponse response;
            try {

                response = client.execute(requestFactory.createRequest(configServer));
            } catch (Exception e) {
                NODE_ADMIN_LOGGER.info("Exception while talking to " + configServer + "(will try all config servers)", e);
                continue;
            }
            try {
                return mapper.readValue(response.getEntity().getContent(), wantedReturnType);
            } catch (IOException e) {
                throw new RuntimeException("Response didn't contain nodes element, failed parsing?", e);
            }
        }
        throw new RuntimeException("Did not get any positive answer");
    }

    @Override
    public List<ContainerNodeSpec> getContainersToRun() throws IOException {

        final GetNodesResponse nodesForHost = tryAllConfigServers(configserver -> {
            String url = "http://" + configserver + ":" + port + "/nodes/v2/node/?parentHost=" + baseHostName + "&recursive=true";
            return new HttpGet(url);
        }, GetNodesResponse.class);

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
    }

    @Override
    public Optional<ContainerNodeSpec> getContainerNodeSpec(HostName hostName) throws IOException {
        final GetNodesResponse nodeResponse = tryAllConfigServers(configserver -> {
            String url = "http://" + configserver + ":" + port + "/nodes/v2/node/?hostname=" + hostName + "&recursive=true";
            return new HttpGet(url);
        }, GetNodesResponse.class);

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

        UpdateNodeAttributesResponse response = tryAllConfigServers(configserver -> {
            String url = "http://" + configserver + ":" + port  + "/nodes/v2/node/" + hostName;
            HttpPatch request = new HttpPatch(url);
            UpdateNodeAttributesRequestBody body = new UpdateNodeAttributesRequestBody(
                    restartGeneration,
                    dockerImage.asString(),
                    currentVespaVersion);
            try {
                request.setEntity(new StringEntity(mapper.writeValueAsString(body)));
            } catch (UnsupportedEncodingException|JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            return request;
        }, UpdateNodeAttributesResponse.class);
        if (response.errorCode == null || response.errorCode.isEmpty()) {
            return;
        }
        throw new RuntimeException("Unexcpected message " + response.message + " " + response.errorCode);
    }


    @Override
    public void markAsReady(final HostName hostName) throws IOException {
        final HttpResponse nodeResponse = tryAllConfigServers(configserver -> {
            String url = "http://" + configserver + ":" + port + "/nodes/v2/ready/" + hostName;
            return new HttpPut(url);
        }, HttpResponse.class);


        if (nodeResponse.getStatusLine().getStatusCode() == 200) {
            return;
        }
        throw new RuntimeException("Could not mark as ready" + hostName);
    }
}
