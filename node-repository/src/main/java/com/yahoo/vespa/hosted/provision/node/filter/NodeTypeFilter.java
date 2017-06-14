// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.NodeType;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author bratseth
 */
public class NodeTypeFilter extends NodeFilter {

    private final Set<NodeType> types;
    
    protected NodeTypeFilter(Set<NodeType> types, NodeFilter next) {
        super(next);
        Objects.requireNonNull(types, "Node types cannot be null");
        this.types = ImmutableSet.copyOf(types);
    }

    @Override
    public boolean matches(Node node) {
        if (! types.isEmpty() && ! types.contains(node.type())) return false;
        return nextMatches(node);
    }

    /** Returns a copy of the given filter which only matches for the given type */
    public static NodeTypeFilter from(NodeType type, NodeFilter filter) {
        return new NodeTypeFilter(Collections.singleton(type), filter);
    }

    /** Returns a node filter which matches a comma or space-separated list of types */
    public static NodeTypeFilter from(String types, NodeFilter next) {
        return new NodeTypeFilter(StringUtilities.split(types).stream().map(NodeType::valueOf).collect(Collectors.toSet()), next);
    }

}
