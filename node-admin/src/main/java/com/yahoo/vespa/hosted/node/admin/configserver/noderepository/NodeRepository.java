// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.node.admin.AclSpec;
import com.yahoo.vespa.hosted.node.admin.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAttributes;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.List;
import java.util.Optional;

/**
 * @author stiankri
 */
public interface NodeRepository {

    List<NodeSpec> getNodes(String baseHostName);

    List<NodeSpec> getNodes(NodeType... nodeTypes);

    Optional<NodeSpec> getNode(String hostName);

    List<AclSpec> getNodeAcl(String hostName);

    void updateNodeAttributes(String hostName, NodeAttributes nodeAttributes);

    void setNodeState(String hostName, Node.State nodeState);
}
