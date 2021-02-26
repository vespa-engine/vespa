// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.serialization.NetworkPortsSerializer;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Address;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.filter.NodeFilter;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.status.HostInfo;
import com.yahoo.vespa.orchestrator.status.HostStatus;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
* @author bratseth
*/
class NodesResponse extends SlimeJsonResponse {

    /** The responses this can create */
    public enum ResponseType { nodeList, stateList, nodesInStateList, singleNode }

    /** The request url minus parameters, with a trailing slash added if missing */
    private final String parentUrl;

    /** The parent url of nodes */
    private final String nodeParentUrl;

    private final NodeFilter filter;
    private final boolean recursive;
    private final Function<HostName, Optional<HostInfo>> orchestrator;
    private final NodeRepository nodeRepository;

    public NodesResponse(ResponseType responseType, HttpRequest request,
                         Orchestrator orchestrator, NodeRepository nodeRepository) {
        this.parentUrl = toParentUrl(request);
        this.nodeParentUrl = toNodeParentUrl(request);
        this.filter = NodesV2ApiHandler.toNodeFilter(request);
        this.recursive = request.getBooleanProperty("recursive");
        this.orchestrator = orchestrator.getHostResolver();
        this.nodeRepository = nodeRepository;

        Cursor root = slime.setObject();
        switch (responseType) {
            case nodeList: nodesToSlime(root); break;
            case stateList : statesToSlime(root); break;
            case nodesInStateList: nodesToSlime(NodeSerializer.stateFrom(lastElement(parentUrl)), root); break;
            case singleNode : nodeToSlime(lastElement(parentUrl), root); break;
            default: throw new IllegalArgumentException();
        }
    }

    private String toParentUrl(HttpRequest request) {
        URI uri = request.getUri();
        String parentUrl = uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + uri.getPath();
        if ( ! parentUrl.endsWith("/"))
            parentUrl = parentUrl + "/";
        return parentUrl;
    }

    private String toNodeParentUrl(HttpRequest request) {
        URI uri = request.getUri();
        return uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + "/nodes/v2/node/";
    }

    private void statesToSlime(Cursor root) {
        Cursor states = root.setObject("states");
        for (Node.State state : Node.State.values())
            toSlime(state, states.setObject(NodeSerializer.toString(state)));
    }

    private void toSlime(Node.State state, Cursor object) {
        object.setString("url", parentUrl + NodeSerializer.toString(state));
        if (recursive)
            nodesToSlime(state, object);
    }

    /** Outputs the nodes in the given state to a node array */
    private void nodesToSlime(Node.State state, Cursor parentObject) {
        Cursor nodeArray = parentObject.setArray("nodes");
        for (NodeType type : NodeType.values())
            toSlime(nodeRepository.nodes().list(state).nodeType(type).asList(), nodeArray);
    }

    /** Outputs all the nodes to a node array */
    private void nodesToSlime(Cursor parentObject) {
        Cursor nodeArray = parentObject.setArray("nodes");
        toSlime(nodeRepository.nodes().list().asList(), nodeArray);
    }

    private void toSlime(List<Node> nodes, Cursor array) {
        for (Node node : nodes) {
            if ( ! filter.matches(node)) continue;
            toSlime(node, recursive, array.addObject());
        }
    }

    private void nodeToSlime(String hostname, Cursor object) {
        Node node = nodeRepository.nodes().node(hostname).orElseThrow(() ->
                new NotFoundException("No node with hostname '" + hostname + "'"));
        toSlime(node, true, object);
    }

    @SuppressWarnings("deprecation")
    private void toSlime(Node node, boolean allFields, Cursor object) {
        object.setString("url", nodeParentUrl + node.hostname());
        if ( ! allFields) return;
        object.setString("id", node.hostname());
        object.setString("state", NodeSerializer.toString(node.state()));
        object.setString("type", NodeSerializer.toString(node.type()));
        object.setString("hostname", node.hostname());
        if (node.parentHostname().isPresent()) {
            object.setString("parentHostname", node.parentHostname().get());
        }
        object.setString("openStackId", node.id());
        object.setString("flavor", node.flavor().name());
        node.reservedTo().ifPresent(reservedTo -> object.setString("reservedTo", reservedTo.value()));
        node.exclusiveTo().ifPresent(exclusiveTo -> object.setString("exclusiveTo", exclusiveTo.serializedForm()));
        if (node.flavor().isConfigured())
            object.setDouble("cpuCores", node.flavor().resources().vcpu());
        NodeResourcesSerializer.toSlime(node.flavor().resources(), object.setObject("resources"));
        if (node.flavor().cost() > 0)
            object.setLong("cost", node.flavor().cost());
        object.setString("environment", node.flavor().getType().name());
        node.allocation().ifPresent(allocation -> {
            toSlime(allocation.owner(), object.setObject("owner"));
            toSlime(allocation.membership(), object.setObject("membership"));
            object.setLong("restartGeneration", allocation.restartGeneration().wanted());
            object.setLong("currentRestartGeneration", allocation.restartGeneration().current());
            object.setString("wantedDockerImage", allocation.membership().cluster().dockerImage()
                    .orElseGet(() -> nodeRepository.containerImages().imageFor(node.type()).withTag(allocation.membership().cluster().vespaVersion()).asString()));
            object.setString("wantedVespaVersion", allocation.membership().cluster().vespaVersion().toFullString());
            NodeResourcesSerializer.toSlime(allocation.requestedResources(), object.setObject("requestedResources"));
            allocation.networkPorts().ifPresent(ports -> NetworkPortsSerializer.toSlime(ports, object.setArray("networkPorts")));
            orchestrator.apply(new HostName(node.hostname()))
                        .ifPresent(info -> {
                            object.setBool("allowedToBeDown", info.status().isSuspended());
                            // TODO: Remove allowedToBeDown as a special-case of orchestratorStatus
                            if (info.status() != HostStatus.NO_REMARKS) {
                                object.setString("orchestratorStatus", info.status().asString());
                            }
                            info.suspendedSince().ifPresent(since -> object.setLong("suspendedSinceMillis", since.toEpochMilli()));
                        });
        });
        object.setLong("rebootGeneration", node.status().reboot().wanted());
        object.setLong("currentRebootGeneration", node.status().reboot().current());
        node.status().osVersion().current().ifPresent(version -> object.setString("currentOsVersion", version.toFullString()));
        node.status().osVersion().wanted().ifPresent(version -> object.setString("wantedOsVersion", version.toFullString()));
        node.status().firmwareVerifiedAt().ifPresent(instant -> object.setLong("currentFirmwareCheck", instant.toEpochMilli()));
        if (node.type().isHost())
            nodeRepository.firmwareChecks().requiredAfter().ifPresent(after -> object.setLong("wantedFirmwareCheck", after.toEpochMilli()));
        node.status().vespaVersion().ifPresent(version -> object.setString("vespaVersion", version.toFullString()));
        currentDockerImage(node).ifPresent(dockerImage -> object.setString("currentDockerImage", dockerImage.asString()));
        object.setLong("failCount", node.status().failCount());
        object.setBool("wantToRetire", node.status().wantToRetire());
        object.setBool("preferToRetire", node.status().preferToRetire());
        object.setBool("wantToDeprovision", node.status().wantToDeprovision());
        toSlime(node.history(), object.setArray("history"));
        ipAddressesToSlime(node.ipConfig().primary(), object.setArray("ipAddresses"));
        ipAddressesToSlime(node.ipConfig().pool().getIpSet(), object.setArray("additionalIpAddresses"));
        addressesToSlime(node.ipConfig().pool().getAddressList(), object);
        node.reports().toSlime(object, "reports");
        node.modelName().ifPresent(modelName -> object.setString("modelName", modelName));
        node.switchHostname().ifPresent(switchHostname -> object.setString("switchHostname", switchHostname));
        nodeRepository.archiveUris().archiveUriFor(node).ifPresent(uri -> object.setString("archiveUri", uri));
    }

    private void toSlime(ApplicationId id, Cursor object) {
        object.setString("tenant", id.tenant().value());
        object.setString("application", id.application().value());
        object.setString("instance", id.instance().value());
    }

    private void toSlime(ClusterMembership membership, Cursor object) {
        object.setString("clustertype", membership.cluster().type().name());
        object.setString("clusterid", membership.cluster().id().value());
        object.setString("group", String.valueOf(membership.cluster().group().get().index()));
        object.setLong("index", membership.index());
        object.setBool("retired", membership.retired());
    }

    private void toSlime(History history, Cursor array) {
        for (History.Event event : history.events()) {
            Cursor object = array.addObject();
            object.setString("event", event.type().name());
            object.setLong("at", event.at().toEpochMilli());
            object.setString("agent", event.agent().name());
        }
    }

    // Hack: For non-docker nodes, return current docker image as default prefix + current Vespa version
    // TODO: Remove current + wanted docker image from response for non-docker types
    private Optional<DockerImage> currentDockerImage(Node node) {
        return node.status().containerImage()
                   .or(() -> Optional.of(node)
                                     .filter(n -> n.flavor().getType() != Flavor.Type.DOCKER_CONTAINER)
                                     .flatMap(n -> n.status().vespaVersion()
                                                    .map(version -> nodeRepository.containerImages().imageFor(n.type()).withTag(version))));
    }

    private void ipAddressesToSlime(Set<String> ipAddresses, Cursor array) {
        ipAddresses.forEach(array::addString);
    }

    private void addressesToSlime(List<Address> addresses, Cursor object) {
        if (addresses.isEmpty()) return;
        // When/if Address becomes richer: add another field (e.g. "addresses") and expand to array of objects
        Cursor addressesArray = object.setArray("additionalHostnames");
        addresses.forEach(address -> addressesArray.addString(address.hostname()));
    }

    private String lastElement(String path) {
        if (path.endsWith("/"))
            path = path.substring(0, path.length()-1);
        int lastSlash = path.lastIndexOf("/");
        if (lastSlash < 0) return path;
        return path.substring(lastSlash+1);
    }

}
