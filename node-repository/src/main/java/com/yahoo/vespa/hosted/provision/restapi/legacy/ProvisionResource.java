// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.legacy;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.Node.State;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Configuration;
import com.yahoo.vespa.hosted.provision.node.NodeFlavors;
import com.yahoo.vespa.hosted.provision.restapi.NodeStateSerializer;
import com.yahoo.vespa.hosted.provision.restapi.legacy.ContainersForHost.DockerContainer;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The provisioning web service used by the provisioning controller to provide nodes to a node repository.
 *
 * @author mortent
 */
@Path("/provision")
@Produces(MediaType.APPLICATION_JSON)
public class ProvisionResource {
    private static final Logger log = Logger.getLogger(ProvisionResource.class.getName());

    private final NodeRepository nodeRepository;

    private final NodeFlavors nodeFlavors;

    public ProvisionResource(@Component NodeRepository nodeRepository, @Component NodeFlavors nodeFlavors) {
        super();
        this.nodeRepository = nodeRepository;
        this.nodeFlavors = nodeFlavors;
    }


    @POST
    @Path("/node")
    @Consumes(MediaType.APPLICATION_JSON)
    public void addNodes(List<HostInfo> hostInfoList) {
        List<Node> nodes = new ArrayList<>();
        for (HostInfo hostInfo : hostInfoList)
            nodes.add(nodeRepository.createNode(hostInfo.openStackId, hostInfo.hostname, Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow(hostInfo.flavor))));
        nodeRepository.addNodes(nodes);
    }

    @GET
    @Path("/node/required")
    public ProvisionStatus getStatus() {
        ProvisionStatus provisionStatus = new ProvisionStatus();
        provisionStatus.requiredNodes = 0; // This concept has no meaning any more ...
        provisionStatus.decomissionNodes = toHostInfo(nodeRepository.getInactive());
        provisionStatus.failedNodes = toHostInfo(nodeRepository.getFailed());

        return provisionStatus;
    }

    private List<HostInfo> toHostInfo(List<Node> nodes) {
        List<HostInfo> hostInfoList = new ArrayList<>(nodes.size());
        for (Node node : nodes)
            hostInfoList.add(HostInfo.createHostInfo(node.hostname(), node.openStackId(), "medium"));
        return hostInfoList;
    }


    @PUT
    @Path("/node/ready")
    public void setReady(String hostName) {
        if ( nodeRepository.getNode(Node.State.ready, hostName).isPresent()) return; // node already 'ready'

        Optional<Node> node = nodeRepository.getNode(Node.State.provisioned, hostName);
        if ( ! node.isPresent())
            node = nodeRepository.getNode(Node.State.dirty, hostName);
        if ( ! node.isPresent())
            throw new IllegalArgumentException("Could not set " + hostName + " ready: Not registered as provisioned or dirty");

        nodeRepository.setReady(Collections.singletonList(node.get()));
    }

    @GET
    @Path("/node/usage/{tenantId}")
    public TenantStatus getTenantUsage(@PathParam("tenantId") String tenantId) {
        TenantStatus ts = new TenantStatus();
        ts.tenantId = tenantId;
        ts.allocated = nodeRepository.getNodeCount(tenantId, Node.State.active);
        ts.reserved = nodeRepository.getNodeCount(tenantId, Node.State.reserved);

        Map<String, TenantStatus.ApplicationUsage> appinstanceUsageMap = new HashMap<>();

        nodeRepository.getNodes(Node.State.active).stream()
                .filter(node -> {
                    return node.allocation().get().owner().tenant().value().equals(tenantId);
                })
                .forEach(node -> {
                    ApplicationId owner = node.allocation().get().owner();
                    appinstanceUsageMap.merge(
                            String.format("%s:%s", owner.application().value(), owner.instance().value()),
                            TenantStatus.ApplicationUsage.create(owner.application().value(), owner.instance().value(), 1),
                            (a, b) -> {
                                a.usage += b.usage;
                                return a;
                            }
                    );
                });

        ts.applications = new ArrayList<>(appinstanceUsageMap.values());
        return ts;
    }

    //TODO: move this to nodes/v2/ when the spec for this has been nailed.
    @GET
    @Path("/dockerhost/{hostname}")
    public ContainersForHost getContainersForHost(@PathParam("hostname") String hostname) {
        List<DockerContainer> dockerContainersForHost =
                nodeRepository.getNodes(State.active, State.inactive).stream()
                        .filter(runsOnDockerHost(hostname))
                        .flatMap(ProvisionResource::toDockerContainer)
                        .collect(Collectors.toList());

        return new ContainersForHost(dockerContainersForHost);
    }

    //returns stream since there is no conversion from optional to stream in java.
    private static Stream<DockerContainer> toDockerContainer(Node node) {
        try {
            String dockerImage = node.allocation().get().membership().cluster().dockerImage().orElseThrow(() ->
                    new Exception("Docker image not set for node " + node));

            return Stream.of(new DockerContainer(
                    node.hostname(),
                    dockerImage,
                    NodeStateSerializer.wireNameOf(node.state()),
                    node.allocation().get().restartGeneration().wanted(),
                    node.allocation().get().restartGeneration().current()));
        } catch (Exception e) {
            log.log(LogLevel.ERROR, "Ignoring docker container.", e);
            return Stream.empty();
        }
    }

    private static Predicate<Node> runsOnDockerHost(String hostname) {
        return node -> node.parentHostname().map(hostname::equals).orElse(false);
    }
}
