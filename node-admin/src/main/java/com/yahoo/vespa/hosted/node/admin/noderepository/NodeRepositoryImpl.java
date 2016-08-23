// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.noderepository;

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
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.ws.rs.core.Response;
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
    private final Set<HostName> configservers;
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper();


    public NodeRepositoryImpl(Set<HostName> configServerHosts, int configPort, String baseHostName) {
        this.baseHostName = baseHostName;
        this.configservers = configServerHosts;
        this.port = configPort;
    }

    @Override
    public List<ContainerNodeSpec> getContainersToRun() throws IOException {
        for (HostName configServer : configservers) {
            final HttpResponse response;
            try {
                String url = "http://" + configServer + ":" + port
                        + "/nodes/v2/node/?parentHost=" + baseHostName + "&recursive=true";
                HttpClient client = HttpClientBuilder.create().build();
                HttpGet request = new HttpGet(url);
                response = client.execute(request);
            } catch (Exception e) {
                continue;
            }
            System.out.println("Response Code : "
                    + response.getStatusLine().getStatusCode());

            if (response.getStatusLine().getStatusCode() != 200) {
                continue;
            }
            final GetNodesResponse nodesForHost = mapper.readValue(response.getEntity().getContent(), GetNodesResponse.class);
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
        throw new IOException("No answers from config servers");

    }
    @Override
    public Optional<ContainerNodeSpec> getContainerNodeSpec(HostName hostName) throws IOException {
        for (HostName configServer : configservers) {
            final HttpResponse response;
            try {
                String url = "http://" + configServer + ":" + port
                        + "/nodes/v2/node/?hostname=" + hostName + "&recursive=true";
                HttpClient client = HttpClientBuilder.create().build();
                HttpGet request = new HttpGet(url);
                response = client.execute(request);
            } catch (Exception e) {
                continue;
            }
            System.out.println("Response Code : "
                    + response.getStatusLine().getStatusCode());

            if (response.getStatusLine().getStatusCode() != 200) {
                continue;
            }
            final GetNodesResponse nodeResponse = mapper.readValue(response.getEntity().getContent(), GetNodesResponse.class);


            if (nodeResponse.nodes.size() == 0) {
                return Optional.empty();
            }
            if (nodeResponse.nodes.size() != 1) {
                throw new RuntimeException("Did not get data for one node using hostname=" + hostName.toString() + "\n" + response.toString());
            }
            return Optional.of(createContainerNodeSpec(nodeResponse.nodes.get(0)));
        }
        throw new RuntimeException("Did not get any results.");
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

        for (HostName configServer : configservers) {
            final HttpResponse response;
            try {
                String url = "http://" + configServer + ":" + port
                        + "/nodes/v2/node/" + hostName;
                HttpClient client = HttpClientBuilder.create().build();
                HttpPatch request = new HttpPatch(url);
                UpdateNodeAttributesRequestBody body = new UpdateNodeAttributesRequestBody(
                        restartGeneration,
                        dockerImage.asString(),
                        currentVespaVersion);
                request.setEntity(new StringEntity(mapper.writeValueAsString(body)));
                response = client.execute(request);
            } catch (Exception e) {
                continue;
            }
            if (response.getStatusLine().getStatusCode() != 200) {
                System.out.println("Response Code : "
                        + response.getStatusLine().getStatusCode());
                continue;
            }
            return;
        }

        throw new RuntimeException("Failed to update node attributes for " + hostName.s() + ":" + /*updateResponse.message*/"");
    }


    @Override
    public void markAsReady(final HostName hostName) throws IOException {
        for (HostName configServer : configservers) {
            final HttpResponse response;
            try {
                String url = "http://" + configServer + ":" + port
                        + "/nodes/v2/ready/" + hostName;
                HttpClient client = HttpClientBuilder.create().build();
                HttpPut request = new HttpPut(url);
                response = client.execute(request);
            } catch (Exception e) {
                continue;
            }
            if (response.getStatusLine().getStatusCode() != 200) {
                System.out.println("Response Code : "
                        + response.getStatusLine().getStatusCode());
                continue;
            }
            return;
        }
        throw new RuntimeException("Could not mark as ready" + hostName);
    }
}
