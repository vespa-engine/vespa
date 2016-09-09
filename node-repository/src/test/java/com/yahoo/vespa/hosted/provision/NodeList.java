// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.provision.ClusterSpec;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A filterable node list
 *
 * @author bratseth
 */
public class NodeList {

    private final List<Node> nodes;

    public NodeList(List<Node> nodes) {
        this.nodes = ImmutableList.copyOf(nodes);
    }

    /** Returns the subset of nodes which are retired */
    public NodeList retired() {
        return new NodeList(nodes.stream().filter(node -> node.allocation().get().membership().retired()).collect(Collectors.toList()));
    }

    /** Returns the subset of nodes which are not retired */
    public NodeList nonretired() {
        return new NodeList(nodes.stream().filter(node -> ! node.allocation().get().membership().retired()).collect(Collectors.toList()));
    }

    /** Returns the subset of nodes of the given flavor */
    public NodeList flavor(String flavor) {
        return new NodeList(nodes.stream().filter(node -> node.flavor().name().equals(flavor)).collect(Collectors.toList()));
    }

    /** Returns the subset of nodes which does not have the given flavor */
    public NodeList notFlavor(String flavor) {
        return new NodeList(nodes.stream().filter(node ->  ! node.flavor().name().equals(flavor)).collect(Collectors.toList()));
    }

    /** Returns the subset of nodes assigned to the given cluster type */
    public NodeList type(ClusterSpec.Type type) {
        return new NodeList(nodes.stream().filter(node -> node.allocation().get().membership().cluster().type().equals(type)).collect(Collectors.toList()));
    }

    public int size() { return nodes.size(); }

    /** Returns the immutable list of nodes in this */
    public List<Node> asList() { return nodes; }

}
