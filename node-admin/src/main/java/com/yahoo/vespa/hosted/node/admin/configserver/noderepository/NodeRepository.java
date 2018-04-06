// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.node.admin.ContainerAclSpec;
import com.yahoo.vespa.hosted.node.admin.NodeRepositoryNode;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAttributes;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.List;
import java.util.Optional;

/**
 * @author stiankri
 */
public interface NodeRepository {

    List<NodeRepositoryNode> getContainerNodeSpecs(String baseHostName);

    List<ContainerNodeSpec> getContainerNodeSpecs(NodeType... nodeTypes);

    Optional<NodeRepositoryNode> getContainerNodeSpec(String hostName);

    List<ContainerAclSpec> getContainerAclSpecs(String hostName);

    void updateNodeAttributes(String hostName, NodeAttributes nodeAttributes);

    void setNodeState(String hostName, Node.State nodeState);
}
