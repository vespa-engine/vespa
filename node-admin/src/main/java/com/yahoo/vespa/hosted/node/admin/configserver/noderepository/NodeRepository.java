// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.vespa.hosted.node.admin.wireguard.WireguardPeer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author stiankri
 */
public interface NodeRepository {

    void addNodes(List<AddNode> nodes);

    List<NodeSpec> getNodes(String baseHostName);

    default NodeSpec getNode(String hostName) {
        return getOptionalNode(hostName).orElseThrow(() -> new NoSuchNodeException(hostName + " not found in node-repo"));
    }

    Optional<NodeSpec> getOptionalNode(String hostName);

    Map<String, Acl> getAcls(String hostname);

    List<WireguardPeer> getExclavePeers();

    List<WireguardPeer> getConfigserverPeers();

    void updateNodeAttributes(String hostName, NodeAttributes nodeAttributes);

    void setNodeState(String hostName, NodeState nodeState);

    default void reboot(String hostname) {
        throw new UnsupportedOperationException("Rebooting not supported in " + getClass().getName());
    }
}
