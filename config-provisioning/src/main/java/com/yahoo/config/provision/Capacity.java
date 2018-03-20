// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Optional;

/**
 * A capacity request.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
public final class Capacity {

    private final int nodeCount;

    private final boolean required;

    private final Optional<String> flavor;

    private final NodeType type;

    private Capacity(int nodeCount, Optional<String> flavor, boolean required, NodeType type) {
        this.nodeCount = nodeCount;
        this.required = required;
        this.flavor = flavor;
        this.type = type;
    }

    /** Returns the number of nodes requested */
    public int nodeCount() { return nodeCount; }

    /**
     * The node flavor requested, or empty if no particular flavor is specified.
     * This may be satisfied by the requested flavor or a suitable replacement
     */
    public Optional<String> flavor() { return flavor; }

    /** Returns whether the requested number of nodes must be met exactly for a request for this to succeed */
    public boolean isRequired() { return required; }

    /**
     * Returns the node type (role) requested. This is tenant nodes by default.
     * If some other type is requested the node count and flavor may be ignored
     * and all nodes of the requested type returned instead.
     */
    public NodeType type() { return type; }

    @Override
    public String toString() {
        return nodeCount + " nodes " + ( flavor.isPresent() ? "of flavor " + flavor.get() : "(default flavor)" );
    }

    /** Creates this from a desired node count: The request may be satisfied with a smaller number of nodes. */
    public static Capacity fromNodeCount(int capacity) {
        return fromNodeCount(capacity, Optional.empty(), false);
    }

    public static Capacity fromNodeCount(int nodeCount, Optional<String> flavor, boolean required) {
        return new Capacity(nodeCount, flavor, required, NodeType.tenant);
    }

    /** Creates this from a node type */
    public static Capacity fromRequiredNodeType(NodeType type) {
        return new Capacity(0,  Optional.empty(), true, type);
    }

}
