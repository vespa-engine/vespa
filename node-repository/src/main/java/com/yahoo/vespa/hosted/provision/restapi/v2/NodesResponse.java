// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.NodeType;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.filter.NodeFilter;
import com.yahoo.vespa.orchestrator.HostNameNotFoundException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.status.HostStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Set;

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
    private final Orchestrator orchestrator;
    private final NodeRepository nodeRepository;

    private final Slime slime;

    public NodesResponse(ResponseType responseType, HttpRequest request,  
                         Orchestrator orchestrator, NodeRepository nodeRepository) {
        super(200);
        this.parentUrl = toParentUrl(request);
        this.nodeParentUrl = toNodeParentUrl(request);
        filter = NodesApiHandler.toNodeFilter(request);
        this.recursive = request.getBooleanProperty("recursive");
        this.orchestrator = orchestrator;
        this.nodeRepository = nodeRepository;

        slime = new Slime();
        Cursor root = slime.setObject();
        switch (responseType) {
            case nodeList: nodesToSlime(root); break;
            case stateList : statesToSlime(root); break;
            case nodesInStateList: nodesToSlime(stateFromString(lastElement(parentUrl)), root); break;
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
        stream.write(toJson());
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    private byte[] toJson() throws IOException {
        return SlimeUtils.toJsonBytes(slime);
    }

    private void statesToSlime(Cursor root) {
        Cursor states = root.setObject("states");
        for (Node.State state : Node.State.values())
            toSlime(state, states.setObject(NodeStateSerializer.wireNameOf(state)));
    }

    private void toSlime(Node.State state, Cursor object) {
        object.setString("url", parentUrl + NodeStateSerializer.wireNameOf(state));
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
        object.setString("id", node.id());
        object.setString("state", NodeStateSerializer.wireNameOf(node.state()));
        object.setString("type", node.type().name());
        object.setString("hostname", node.hostname());
        object.setString("type", toString(node.type()));
        if (node.parentHostname().isPresent()) {
            object.setString("parentHostname", node.parentHostname().get());
        }
        object.setString("openStackId", node.openStackId());
        object.setString("flavor", node.flavor().name());
        object.setString("canonicalFlavor", node.flavor().canonicalName());
        object.setDouble("minDiskAvailableGb", node.flavor().getMinDiskAvailableGb());
        object.setDouble("minMainMemoryAvailableGb", node.flavor().getMinMainMemoryAvailableGb());
        if (node.flavor().getDescription() != null && ! node.flavor().getDescription().isEmpty())
            object.setString("description", node.flavor().getDescription());
        object.setDouble("minCpuCores", node.flavor().getMinCpuCores());
        if (node.flavor().cost() > 0)
            object.setLong("cost", node.flavor().cost());
        object.setBool("fastDisk", node.flavor().hasFastDisk());
        object.setString("environment", node.flavor().getType().name());
        if (node.allocation().isPresent()) {
            toSlime(node.allocation().get().owner(), object.setObject("owner"));
            toSlime(node.allocation().get().membership(), object.setObject("membership"));
            object.setLong("restartGeneration", node.allocation().get().restartGeneration().wanted());
            object.setLong("currentRestartGeneration", node.allocation().get().restartGeneration().current());
            object.setString("wantedDockerImage", nodeRepository.dockerImage().withTag(node.allocation().get().membership().cluster().vespaVersion()).asString());
            object.setString("wantedVespaVersion", node.allocation().get().membership().cluster().vespaVersion().toFullString());
            try {
                object.setBool("allowedToBeDown", 
                               orchestrator.getNodeStatus(new HostName(node.hostname())) == HostStatus.ALLOWED_TO_BE_DOWN);
            }
            catch (HostNameNotFoundException e) {/* ok */ }
        }
        object.setLong("rebootGeneration", node.status().reboot().wanted());
        object.setLong("currentRebootGeneration", node.status().reboot().current());
        node.status().vespaVersion()
                .filter(version -> !version.isEmpty())
                .ifPresent(version -> {
                    object.setString("vespaVersion", version.toFullString());
                    object.setString("currentDockerImage", nodeRepository.dockerImage().withTag(version).asString());
                    // TODO: Remove these when they are no longer read
                    object.setString("hostedVersion", version.toFullString());
                    object.setString("convergedStateVersion", version.toFullString());
                });
        object.setLong("failCount", node.status().failCount());
        object.setBool("hardwareFailure", node.status().hardwareFailureDescription().isPresent());
        node.status().hardwareFailureDescription().ifPresent(failure -> object.setString("hardwareFailureDescription", failure));
        object.setBool("wantToRetire", node.status().wantToRetire());
        object.setBool("wantToDeprovision", node.status().wantToDeprovision());
        toSlime(node.history(), object.setArray("history"));
        ipAddressesToSlime(node.ipAddresses(), object.setArray("ipAddresses"));
        ipAddressesToSlime(node.additionalIpAddresses(), object.setArray("additionalIpAddresses"));
        node.status().hardwareDivergence().ifPresent(hardwareDivergence -> object.setString("hardwareDivergence", hardwareDivergence));
    }

    private String toString(NodeType type) {
        switch(type) {
            case tenant: return "tenant";
            case host: return "host";
            case proxy: return "proxy";
            case proxyhost: return "proxyhost";
            case config: return "config";
            case confighost: return "confighost";
            default:
                throw new RuntimeException("New type added to enum, not implemented in NodesResponse: " + type.name());
        }
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

    private void ipAddressesToSlime(Set<String> ipAddresses, Cursor array) {
        ipAddresses.forEach(array::addString);
    }

    private String lastElement(String path) {
        if (path.endsWith("/"))
            path = path.substring(0, path.length()-1);
        int lastSlash = path.lastIndexOf("/");
        if (lastSlash < 0) return path;
        return path.substring(lastSlash+1, path.length());
    }

    private static Node.State stateFromString(String stateString) {
        return NodeStateSerializer.fromWireName(stateString)
                .orElseThrow(() -> new RuntimeException("Node state '" + stateString + "' is not known"));
    }

}
