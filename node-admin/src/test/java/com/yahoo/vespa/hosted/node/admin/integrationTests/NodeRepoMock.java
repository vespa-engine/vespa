// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.AddNode;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mock with some simple logic
 *
 * @author dybis
 */
public class NodeRepoMock implements NodeRepository {
    private static final Object monitor = new Object();

    private final Map<String, NodeSpec> nodeRepositoryNodesByHostname = new HashMap<>();
    private final Map<String, Acl> acls = new HashMap<>();

    private final CallOrderVerifier callOrderVerifier;

    public NodeRepoMock(CallOrderVerifier callOrderVerifier) {
        this.callOrderVerifier = callOrderVerifier;
    }

    @Override
    public void addNodes(List<AddNode> nodes) { }

    @Override
    public List<NodeSpec> getNodes(String baseHostName) {
        synchronized (monitor) {
            return new ArrayList<>(nodeRepositoryNodesByHostname.values());
        }
    }

    @Override
    public Optional<NodeSpec> getOptionalNode(String hostName) {
        synchronized (monitor) {
            return Optional.ofNullable(nodeRepositoryNodesByHostname.get(hostName));
        }
    }

    @Override
    public Map<String, Acl> getAcls(String hostname) {
        synchronized (monitor) {
            return acls;
        }
    }

    @Override
    public void updateNodeAttributes(String hostName, NodeAttributes nodeAttributes) {
        synchronized (monitor) {
            callOrderVerifier.add("updateNodeAttributes with HostName: " + hostName + ", " + nodeAttributes);
        }
    }

    @Override
    public void setNodeState(String hostName, Node.State nodeState) {
        Optional<NodeSpec> node = getOptionalNode(hostName);

        synchronized (monitor) {
            node.ifPresent(nrn -> updateNodeRepositoryNode(new NodeSpec.Builder(nrn)
                    .state(nodeState)
                    .build()));
            callOrderVerifier.add("setNodeState " + hostName + " to " + nodeState);
        }
    }

    public void updateNodeRepositoryNode(NodeSpec nodeSpec) {
        nodeRepositoryNodesByHostname.put(nodeSpec.getHostname(), nodeSpec);
    }

    public int getNumberOfContainerSpecs() {
        synchronized (monitor) {
            return nodeRepositoryNodesByHostname.size();
        }
    }
}
