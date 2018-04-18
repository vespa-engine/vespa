// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.vespa.hosted.node.admin.NodeSpec;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author stiankri
 */
public interface NodeRepository {

    void addNodes(List<AddNode> nodes);

    List<NodeSpec> getNodes(String baseHostName);

    Optional<NodeSpec> getNode(String hostName);

    Map<String, Acl> getAcls(String hostname);

    void updateNodeAttributes(String hostName, NodeAttributes nodeAttributes);

    void setNodeState(String hostName, Node.State nodeState);
}
