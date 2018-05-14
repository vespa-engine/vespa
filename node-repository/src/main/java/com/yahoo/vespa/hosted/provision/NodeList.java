// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.collectingAndThen;

/**
 * A filterable node list
 *
 * @author bratseth
 * @author mpolden
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

    /** Returns the subset of nodes owned by the given application */
    public NodeList owner(ApplicationId application) {
        return nodes.stream()
                .filter(node -> node.allocation().map(a -> a.owner().equals(application)).orElse(false))
                .collect(collectingAndThen(Collectors.toList(), NodeList::new));
    }

    /** Returns the subset of nodes matching the given node type */
    public NodeList nodeType(NodeType nodeType) {
        return nodes.stream()
                .filter(node -> node.type() == nodeType)
                .collect(collectingAndThen(Collectors.toList(), NodeList::new));
    }

    /** Returns the parent nodes of the given child nodes */
    public NodeList parentsOf(Collection<Node> children) {
        return children.stream()
                       .map(Node::parentHostname)
                       .filter(Optional::isPresent)
                       .map(Optional::get)
                       .map(hostName -> nodes.stream().filter(node -> node.hostname().equals(hostName)).findFirst())
                       .filter(Optional::isPresent)
                       .map(Optional::get)
                       .collect(collectingAndThen(Collectors.toList(), NodeList::new));
    }

    /** Returns the child nodes of the given parent node */
    public NodeList childrenOf(Node parent) {
        return nodes.stream()
                    .filter(n -> n.parentHostname()
                                  .map(hostName -> hostName.equals(parent.hostname()))
                                  .orElse(false))
                    .collect(collectingAndThen(Collectors.toList(), NodeList::new));
    }

    public NodeList configServers() {
        return nodes.stream()
                .filter(node -> node.type() == NodeType.config)
                .collect(collectingAndThen(Collectors.toList(), NodeList::new));
    }

    public int size() { return nodes.size(); }

    /** Returns the immutable list of nodes in this */
    public List<Node> asList() { return nodes; }

}
