// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v1;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.NodeType;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.jdisc.Response;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Allocation;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * The implementation of the /nodes/v1 API.
 * This dumps the content of the node repository on request, possibly with a host filter to return just the single
 * matching node.
 *
 * @author bratseth
 */
public class
        NodesApiHandler extends LoggingRequestHandler {

    private final NodeRepository nodeRepository;

    public NodesApiHandler(Executor executor, AccessLog accessLog, NodeRepository nodeRepository) {
        super(executor, accessLog);
        this.nodeRepository = nodeRepository;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        return new NodesResponse(Response.Status.OK,
                                 Optional.ofNullable(request.getProperty("hostname")), nodeRepository);
    }

    private static class NodesResponse extends HttpResponse {

        /** If present only the node with this hostname will be present in the response */
        private final Optional<String> hostnameFilter;
        private final NodeRepository nodeRepository;

        public NodesResponse(int status, Optional<String> hostnameFilter, NodeRepository nodeRepository) {
            super(status);
            this.hostnameFilter = hostnameFilter;
            this.nodeRepository = nodeRepository;
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
            Slime slime = new Slime();
            toSlime(slime.setObject());
            return SlimeUtils.toJsonBytes(slime);
        }

        private void toSlime(Cursor root) {
            for (Node.State state : Node.State.values())
                toSlime(state, root);
        }

        private void toSlime(Node.State state, Cursor object) {
            Cursor nodeArray = null; // create if there are nodes
            for (NodeType type : NodeType.values()) {
                List<Node> nodes = nodeRepository.getNodes(type, state);
                for (Node node : nodes) {
                    if (hostnameFilter.isPresent() && !node.hostname().equals(hostnameFilter.get())) continue;
                    if (nodeArray == null)
                        nodeArray = object.setArray(state.name());
                    toSlime(node, nodeArray.addObject());
                }
            }
        }

        private void toSlime(Node node, Cursor object) {
            object.setString("id", node.openStackId());
            object.setString("hostname", node.hostname());
            object.setString("flavor", node.flavor().name());
            Optional<Allocation> allocation = node.allocation();
            if (! allocation.isPresent()) return;
            toSlime(allocation.get().owner(), object.setObject("owner"));
            toSlime(allocation.get().membership(), object.setObject("membership"));
            object.setLong("restartGeneration", allocation.get().restartGeneration().wanted());
        }

        private void toSlime(ApplicationId id, Cursor object) {
            object.setString("tenant", id.tenant().value());
            object.setString("application", id.application().value());
            object.setString("instance", id.instance().value());
        }

        private void toSlime(ClusterMembership membership, Cursor object) {
            object.setString("clustertype", membership.cluster().type().name());
            object.setString("clusterid", membership.cluster().id().value());
            object.setLong("index", membership.index());
            object.setBool("retired", membership.retired());
        }

    }

}