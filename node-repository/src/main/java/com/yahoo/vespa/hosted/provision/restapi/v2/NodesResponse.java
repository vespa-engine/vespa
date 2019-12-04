// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.serialization.NetworkPortsSerializer;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.filter.NodeFilter;
import com.yahoo.vespa.hosted.provision.os.OsVersion;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.status.HostStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
* @author bratseth
*/
class NodesResponse extends HttpResponse {

    /** The responses this can create */
    public enum ResponseType { nodeList, stateList, nodesInStateList, singleNode }

    /** The request url minus parameters, with a trailing slash added if missing */
    private final String parentUrl;

    /** The parent url of nodes */
    private final String nodeParentUrl;

    private final NodeFilter filter;
    private final boolean recursive;
    private final Function<HostName, Optional<HostStatus>> orchestrator;
    private final NodeRepository nodeRepository;
    private final Slime slime;
    private final NodeSerializer serializer = new NodeSerializer();

    public NodesResponse(ResponseType responseType, HttpRequest request,  
                         Orchestrator orchestrator, NodeRepository nodeRepository) {
        super(200);
        this.parentUrl = toParentUrl(request);
        this.nodeParentUrl = toNodeParentUrl(request);
        filter = NodesApiHandler.toNodeFilter(request);
        this.recursive = request.getBooleanProperty("recursive");
        this.orchestrator = orchestrator.getNodeStatuses();
        this.nodeRepository = nodeRepository;

        slime = new Slime();
        Cursor root = slime.setObject();
        switch (responseType) {
            case nodeList: nodesToSlime(root); break;
            case stateList : statesToSlime(root); break;
            case nodesInStateList: nodesToSlime(serializer.stateFrom(lastElement(parentUrl)), root); break;
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

    @Override
    public void render(OutputStream stream) throws IOException {
        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    private void statesToSlime(Cursor root) {
        Cursor states = root.setObject("states");
        for (Node.State state : Node.State.values())
            toSlime(state, states.setObject(serializer.toString(state)));
    }

    private void toSlime(Node.State state, Cursor object) {
        object.setString("url", parentUrl + serializer.toString(state));
        if (recursive)
            nodesToSlime(state, object);
    }

    /** Outputs the nodes in the given state to a node array */
    private void nodesToSlime(Node.State state, Cursor parentObject) {
        Cursor nodeArray = parentObject.setArray("nodes");
        for (NodeType type : NodeType.values())
            toSlime(nodeRepository.getNodes(type, state), nodeArray);
    }

    /** Outputs all the nodes to a node array */
    private void nodesToSlime(Cursor parentObject) {
        Cursor nodeArray = parentObject.setArray("nodes");
        toSlime(nodeRepository.getNodes(), nodeArray);
    }

    private void toSlime(List<Node> nodes, Cursor array) {
        for (Node node : nodes) {
            if ( ! filter.matches(node)) continue;
            toSlime(node, recursive, array.addObject());
        }
    }

    private void nodeToSlime(String hostname, Cursor object) {
        Node node = nodeRepository.getNode(hostname).orElseThrow(() ->
                new NotFoundException("No node with hostname '" + hostname + "'"));
        toSlime(node, true, object);
    }

    private void toSlime(Node node, boolean allFields, Cursor object) {
        object.setString("url", nodeParentUrl + node.hostname());
        if ( ! allFields) return;
        object.setString("id", node.hostname());
        object.setString("state", serializer.toString(node.state()));
        object.setString("type", node.type().name());
        object.setString("hostname", node.hostname());
        object.setString("type", serializer.toString(node.type()));
        if (node.parentHostname().isPresent()) {
            object.setString("parentHostname", node.parentHostname().get());
        }
        object.setString("openStackId", node.id());
        object.setString("flavor", node.flavor().name());
        toSlime(node.flavor().resources(), object.setObject("resources"));
        if (node.flavor().cost() > 0)
            object.setLong("cost", node.flavor().cost());
        object.setString("environment", node.flavor().getType().name());
        node.allocation().ifPresent(allocation -> {
            toSlime(allocation.owner(), object.setObject("owner"));
            toSlime(allocation.membership(), object.setObject("membership"));
            object.setLong("restartGeneration", allocation.restartGeneration().wanted());
            object.setLong("currentRestartGeneration", allocation.restartGeneration().current());
            object.setString("wantedDockerImage", dockerImageFor(node.type()).withTag(allocation.membership().cluster().vespaVersion()).asString());
            object.setString("wantedVespaVersion", allocation.membership().cluster().vespaVersion().toFullString());
            toSlime(allocation.requestedResources(), object.setObject("requestedResources"));
            allocation.networkPorts().ifPresent(ports -> NetworkPortsSerializer.toSlime(ports, object.setArray("networkPorts")));
            orchestrator.apply(new HostName(node.hostname()))
                        .map(status -> status == HostStatus.ALLOWED_TO_BE_DOWN)
                        .ifPresent(allowedToBeDown -> object.setBool("allowedToBeDown", allowedToBeDown));
        });
        object.setLong("rebootGeneration", node.status().reboot().wanted());
        object.setLong("currentRebootGeneration", node.status().reboot().current());
        node.status().osVersion().ifPresent(version -> object.setString("currentOsVersion", version.toFullString()));
        nodeRepository.osVersions().targetFor(node.type())
                      .filter(OsVersion::active) // Only include wantedOsVersion when active. When active is false, OS upgrades are paused.
                      .map(OsVersion::version)
                      .ifPresent(version -> object.setString("wantedOsVersion", version.toFullString()));
        node.status().firmwareVerifiedAt().ifPresent(instant -> object.setLong("currentFirmwareCheck", instant.toEpochMilli()));
        if (node.type().isDockerHost())
            nodeRepository.firmwareChecks().requiredAfter().ifPresent(after -> object.setLong("wantedFirmwareCheck", after.toEpochMilli()));
        node.status().vespaVersion().ifPresent(version -> object.setString("vespaVersion", version.toFullString()));
        currentDockerImage(node).ifPresent(dockerImage -> object.setString("currentDockerImage", dockerImage.asString()));
        object.setLong("failCount", node.status().failCount());
        object.setBool("wantToRetire", node.status().wantToRetire());
        object.setBool("wantToDeprovision", node.status().wantToDeprovision());
        toSlime(node.history(), object.setArray("history"));
        ipAddressesToSlime(node.ipAddresses(), object.setArray("ipAddresses"));
        ipAddressesToSlime(node.ipAddressPool().asSet(), object.setArray("additionalIpAddresses"));
        node.reports().toSlime(object, "reports");
        node.modelName().ifPresent(modelName -> object.setString("modelName", modelName));
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
            object.setString("agent", normalizedAgentUntilV6IsGone(event.agent()).name());
        }
    }

    private void toSlime(NodeResources resources, Cursor object) {
        object.setDouble("vcpu", resources.vcpu());
        object.setDouble("memoryGb", resources.memoryGb());
        object.setDouble("diskGb", resources.diskGb());
        object.setDouble("bandwidthGbps", resources.bandwidthGbps());
        object.setString("diskSpeed", serializer.toString(resources.diskSpeed()));
        object.setString("storageType", serializer.toString(resources.storageType()));
    }

    // Hack: For non-docker noder, return current docker image as default prefix + current Vespa version
    // TODO: Remove current + wanted docker image from response for non-docker types
    private Optional<DockerImage> currentDockerImage(Node node) {
        return node.status().dockerImage()
                .or(() -> Optional.of(node)
                        .filter(n -> n.flavor().getType() != Flavor.Type.DOCKER_CONTAINER)
                        .flatMap(n -> n.status().vespaVersion()
                                .map(version -> dockerImageFor(n.type()).withTag(version))));
    }

    // Docker hosts are not running in an image, but return the image of the node type running on it anyway,
    // this allows the docker host to pre-download the (likely) image its node will run
    private DockerImage dockerImageFor(NodeType nodeType) {
        return nodeRepository.dockerImage(nodeType.isDockerHost() ? nodeType.childNodeType() : nodeType);
    }


    /** maven-vespa-plugin @ v6 needs to deserialize nodes w/history. */
    private Agent normalizedAgentUntilV6IsGone(Agent agent) {
        return agent == Agent.NodeFailer ? Agent.system : agent;
    }

    private void ipAddressesToSlime(Set<String> ipAddresses, Cursor array) {
        ipAddresses.forEach(array::addString);
    }

    private String lastElement(String path) {
        if (path.endsWith("/"))
            path = path.substring(0, path.length()-1);
        int lastSlash = path.lastIndexOf("/");
        if (lastSlash < 0) return path;
        return path.substring(lastSlash+1);
    }

}
