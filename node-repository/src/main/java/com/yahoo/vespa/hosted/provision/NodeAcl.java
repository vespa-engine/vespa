// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * A node ACL. The ACL contains the node which the ACL is valid for, and a list of nodes that the node should trust.
 *
 * @author mpolden
 */
public class NodeAcl {

    private final Node node;
    private final List<Node> trustedNodes;

    public NodeAcl(Node node, List<Node> trustedNodes) {
        this.node = node;
        this.trustedNodes = ImmutableList.copyOf(trustedNodes);
    }

    public Node node() {
        return node;
    }

    public List<Node> trustedNodes() {
        return trustedNodes;
    }
}
