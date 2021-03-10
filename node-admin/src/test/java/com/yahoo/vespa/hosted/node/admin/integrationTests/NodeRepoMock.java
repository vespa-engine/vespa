// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.AddNode;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Mock with some simple logic
 *
 * @author dybis
 */
public class NodeRepoMock implements NodeRepository {
    private static final Object monitor = new Object();

    private final Map<String, NodeSpec> nodeRepositoryNodesByHostname = new HashMap<>();

    @Override
    public void addNodes(List<AddNode> nodes) { }

    @Override
    public List<NodeSpec> getNodes(String baseHostName) {
        synchronized (monitor) {
            return nodeRepositoryNodesByHostname.values().stream()
                    .filter(node -> baseHostName.equals(node.parentHostname().orElse(null)))
                    .collect(Collectors.toList());
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
        return Map.of();
    }

    @Override
    public void updateNodeAttributes(String hostName, NodeAttributes nodeAttributes) {
        synchronized (monitor) {
            updateNodeRepositoryNode(new NodeSpec.Builder(getNode(hostName))
                    .updateFromNodeAttributes(nodeAttributes)
                    .build());
        }
    }

    @Override
    public void setNodeState(String hostName, NodeState nodeState) {
        synchronized (monitor) {
            updateNodeRepositoryNode(new NodeSpec.Builder(getNode(hostName))
                    .state(nodeState)
                    .build());
        }
    }

    void updateNodeRepositoryNode(NodeSpec nodeSpec) {
        synchronized (monitor) {
            nodeRepositoryNodesByHostname.put(nodeSpec.hostname(), nodeSpec);
        }
    }
}
