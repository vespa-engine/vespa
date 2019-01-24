// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.google.common.collect.ImmutableSet;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Objects;
import java.util.Set;

/**
 * A node ACL. The ACL contains the node which the ACL is valid for,
 * a set of nodes and networks that the node should trust.
 *
 * @author mpolden
 */
public class NodeAcl {

    private final Node node;
    private final Set<Node> trustedNodes;
    private final Set<String> trustedNetworks;
    private final Set<Integer> trustedPorts;

    public NodeAcl(Node node, Set<Node> trustedNodes, Set<String> trustedNetworks, Set<Integer> trustedPorts) {
        this.node = Objects.requireNonNull(node, "node must be non-null");
        this.trustedNodes = ImmutableSet.copyOf(Objects.requireNonNull(trustedNodes, "trustedNodes must be non-null"));
        this.trustedNetworks = ImmutableSet.copyOf(Objects.requireNonNull(trustedNetworks, "trustedNetworks must be non-null"));
        this.trustedPorts = ImmutableSet.copyOf(Objects.requireNonNull(trustedPorts, "trustedPorts must be non-null"));
    }

    public Node node() {
        return node;
    }

    public Set<Node> trustedNodes() {
        return trustedNodes;
    }

    public Set<String> trustedNetworks() {
        return trustedNetworks;
    }

    public Set<Integer> trustedPorts() {
        return trustedPorts;
    }

}
