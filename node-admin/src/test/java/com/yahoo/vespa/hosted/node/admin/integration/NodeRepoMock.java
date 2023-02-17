// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integration;

import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.AddNode;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NoSuchNodeException;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeState;
import com.yahoo.vespa.hosted.node.admin.wireguard.WireguardPeer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Mock with some simple logic
 *
 * @author dybis
 */
public class NodeRepoMock implements NodeRepository {

    private final Map<String, NodeSpec> nodeSpecByHostname = new ConcurrentHashMap<>();
    private volatile Map<String, Acl> aclByHostname = Map.of();

    @Override
    public void addNodes(List<AddNode> nodes) { }

    @Override
    public List<NodeSpec> getNodes(String baseHostName) {
        return nodeSpecByHostname.values().stream()
                .filter(node -> baseHostName.equals(node.parentHostname().orElse(null)))
                .toList();
    }

    @Override
    public Optional<NodeSpec> getOptionalNode(String hostName) {
        return Optional.ofNullable(nodeSpecByHostname.get(hostName));
    }

    @Override
    public Map<String, Acl> getAcls(String hostname) {
        return aclByHostname;
    }

    @Override
    public List<WireguardPeer> getExclavePeers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<WireguardPeer> getConfigserverPeers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNodeAttributes(String hostName, NodeAttributes nodeAttributes) {
        updateNodeSpec(new NodeSpec.Builder(getNode(hostName))
                .updateFromNodeAttributes(nodeAttributes)
                .build());
    }

    @Override
    public void setNodeState(String hostName, NodeState nodeState) {
        updateNodeSpec(new NodeSpec.Builder(getNode(hostName))
                .state(nodeState)
                .build());
    }

    public void updateNodeSpec(NodeSpec nodeSpec) {
        nodeSpecByHostname.put(nodeSpec.hostname(), nodeSpec);
    }

    public void updateNodeSpec(String hostname, Function<NodeSpec.Builder, NodeSpec.Builder> mapper) {
        nodeSpecByHostname.compute(hostname, (__, nodeSpec) -> {
            if (nodeSpec == null) throw new NoSuchNodeException(hostname);
            return mapper.apply(new NodeSpec.Builder(nodeSpec)).build();
        });
    }

    public void resetNodeSpecs() {
        nodeSpecByHostname.clear();
    }

    public void setAcl(Map<String, Acl> aclByHostname) {
        this.aclByHostname = Map.copyOf(aclByHostname);
    }
}
