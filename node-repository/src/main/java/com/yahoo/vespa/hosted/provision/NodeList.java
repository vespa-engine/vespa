// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.collectingAndThen;

/**
 * A filterable node list. The result of a filter operation is immutable.
 *
 * @author bratseth
 * @author mpolden
 */
public class NodeList implements Iterable<Node> {

    private final List<Node> nodes;
    private final boolean negate;

    public NodeList(List<Node> nodes) {
        this(nodes, true, false);
    }

    private NodeList(List<Node> nodes, boolean copy, boolean negate) {
        this.nodes = copy ? List.copyOf(nodes) : Collections.unmodifiableList(nodes);
        this.negate = negate;
    }

    /** Invert the next filter operation. All other methods that return a {@link NodeList} clears the negation. */
    public NodeList not() {
        return new NodeList(nodes, false, true);
    }

    /** Returns the subset of nodes which are retired */
    public NodeList retired() {
        return filter(node -> node.allocation().get().membership().retired());
    }

    /** Returns the subset of nodes having exactly the given resources */
    public NodeList resources(NodeResources resources) { return filter(node -> node.flavor().resources().equals(resources)); }

    /** Returns the subset of nodes of the given flavor */
    public NodeList flavor(String flavor) {
        return filter(node -> node.flavor().name().equals(flavor));
    }

    /** Returns the subset of nodes assigned to the given cluster type */
    public NodeList type(ClusterSpec.Type type) {
        return filter(node -> node.allocation().isPresent() && node.allocation().get().membership().cluster().type().equals(type));
    }

    /** Returns the subset of nodes that are currently changing their Vespa version */
    public NodeList changingVersion() {
        return filter(node -> node.status().vespaVersion().isPresent() &&
                              node.allocation().isPresent() &&
                              !node.status().vespaVersion().get().equals(node.allocation().get().membership().cluster().vespaVersion()));
    }

    /** Returns the subset of nodes that are currently changing their OS version */
    public NodeList changingOsVersion() {
        return filter(node -> node.status().osVersion().changing());
    }

    /** Returns the subset of nodes that are currently on the given OS version */
    public NodeList onOsVersion(Version version) {
        return filter(node -> node.status().osVersion().matches(version));
    }

    /** Returns the subset of nodes assigned to the given cluster */
    public NodeList cluster(ClusterSpec.Id cluster) {
        return filter(node -> node.allocation().isPresent() && node.allocation().get().membership().cluster().id().equals(cluster));
    }

    /** Returns the subset of nodes owned by the given application */
    public NodeList owner(ApplicationId application) {
        return filter(node -> node.allocation().map(a -> a.owner().equals(application)).orElse(false));
    }

    /** Returns the subset of nodes matching the given node type(s) */
    public NodeList nodeType(NodeType first, NodeType... rest) {
        EnumSet<NodeType> nodeTypes = EnumSet.of(first, rest);
        return filter(node -> nodeTypes.contains(node.type()));
    }

    /** Returns the subset of nodes that are parents */
    public NodeList parents() {
        return filter(n -> n.parentHostname().isEmpty());
    }

    /** Returns the child nodes of the given parent node */
    public NodeList childrenOf(String hostname) {
        return filter(n -> n.parentHostname().map(hostname::equals).orElse(false));
    }

    public NodeList childrenOf(Node parent) {
        return childrenOf(parent.hostname());
    }

    /** Returns the subset of nodes that are in any of the given state(s) */
    public NodeList state(Node.State first, Node.State... rest) {
        return state(EnumSet.of(first, rest));
    }

    /** Returns the subset of nodes that are in any of the given state(s) */
    public NodeList state(Collection<Node.State> nodeStates) {
        return filter(node -> nodeStates.contains(node.state()));
    }

    /** Returns the parent nodes of the given child nodes */
    public NodeList parentsOf(Collection<Node> children) {
        return children.stream()
                       .map(this::parentOf)
                       .filter(Optional::isPresent)
                       .flatMap(Optional::stream)
                       .collect(collectingAndThen(Collectors.toList(), NodeList::wrap));
    }

    /** Returns the parent node of the given child node */
    public Optional<Node> parentOf(Node child) {
        return child.parentHostname()
                .flatMap(parentHostname -> nodes.stream()
                        .filter(node -> node.hostname().equals(parentHostname))
                        .findFirst());
    }

    /** Returns the first n nodes in this */
    public NodeList first(int n) {
        n = Math.min(n, nodes.size());
        return wrap(nodes.subList(negate ? n : 0,
                                  negate ? nodes.size() : n));
    }

    public int size() { return nodes.size(); }

    /** Returns the immutable list of nodes in this */
    public List<Node> asList() { return nodes; }

    /** Returns the nodes of this as a stream */
    public Stream<Node> stream() { return asList().stream(); }

    public NodeList filter(Predicate<Node> predicate) {
        return nodes.stream().filter(negate ? predicate.negate() : predicate)
                    .collect(collectingAndThen(Collectors.toList(), NodeList::wrap));
    }

    @Override
    public Iterator<Node> iterator() {
        return nodes.iterator();
    }

    /** Create a new list containing the given nodes, without copying */
    private static NodeList wrap(List<Node> nodes) {
        return new NodeList(nodes, false, false);
    }

}
