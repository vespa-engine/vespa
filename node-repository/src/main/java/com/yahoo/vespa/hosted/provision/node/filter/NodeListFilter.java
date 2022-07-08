// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.yahoo.vespa.hosted.provision.Node;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A node filter which matches a particular list of nodes
 *
 * @author bratseth
 */
public class NodeListFilter {

    private NodeListFilter() {}

    private static Predicate<Node> makePredicate(List<Node> nodes) {
        Objects.requireNonNull(nodes, "nodes cannot be null");
        Set<Node> nodesSet = Set.copyOf(nodes);
        return nodesSet::contains;
    }

    public static Predicate<Node> from(Node nodes) {
        return makePredicate(List.of(nodes));
    }

    public static Predicate<Node> from(List<Node> nodes) {
        return makePredicate(nodes);
    }

}
