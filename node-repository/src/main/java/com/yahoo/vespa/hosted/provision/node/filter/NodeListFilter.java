// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.google.common.collect.ImmutableSet;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A node filter which matches a particular list of nodes
 *
 * @author bratseth
 */
public class NodeListFilter extends NodeFilter {

    private final Set<Node> nodes;

    private NodeListFilter(List<Node> nodes, NodeFilter next) {
        super(next);
        this.nodes = ImmutableSet.copyOf(nodes);
    }

    @Override
    public boolean matches(Node node) {
        return nodes.contains(node);
    }

    public static NodeListFilter from(Node nodes) {
        return new NodeListFilter(Collections.singletonList(nodes), null);
    }

    public static NodeListFilter from(List<Node> nodes) {
        return new NodeListFilter(nodes, null);
    }

    public static NodeListFilter from(List<Node> nodes, NodeFilter next) {
        return new NodeListFilter(nodes, next);
    }

}
