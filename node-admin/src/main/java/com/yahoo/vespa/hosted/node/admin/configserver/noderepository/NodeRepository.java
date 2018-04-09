// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.vespa.hosted.node.admin.NodeSpec;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.Acl;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAttributes;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author stiankri
 */
public interface NodeRepository {

    List<NodeSpec> getNodes(String baseHostName);

    Optional<NodeSpec> getNode(String hostName);

    Map<Container, Acl> getAcl(String hostname, List<Container> containers);

    void updateNodeAttributes(String hostName, NodeAttributes nodeAttributes);

    void setNodeState(String hostName, Node.State nodeState);
}
