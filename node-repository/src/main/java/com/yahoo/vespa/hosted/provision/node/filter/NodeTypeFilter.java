// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.yahoo.config.provision.NodeType;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author bratseth
 */
public class NodeTypeFilter {

    private NodeTypeFilter() {}

    private static Predicate<Node> makePredicate(EnumSet<NodeType> types) {
        Objects.requireNonNull(types, "Node types cannot be null");
        if (types.isEmpty()) return node -> true;
        return node -> types.contains(node.type());
    }

    /** Returns a copy of the given filter which only matches for the given type */
    public static Predicate<Node> from(NodeType type) {
        return makePredicate(EnumSet.of(type));
    }

    /** Returns a node filter which matches a comma or space-separated list of types */
    public static Predicate<Node> from(String types) {
        return makePredicate(StringUtilities.split(types).stream()
                .map(NodeType::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(NodeType.class))));
    }

}
