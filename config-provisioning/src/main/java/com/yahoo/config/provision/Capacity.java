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

    private final int nodes;
    private final int groups;

    private final boolean required;

    private final boolean canFail;

    private final Optional<NodeResources> nodeResources;

    private final NodeType type;

    private Capacity(int nodes, int groups, Optional<NodeResources> nodeResources, boolean required, boolean canFail, NodeType type) {
        if (nodes > 0 && groups > 0 && nodes % groups != 0)
            throw new IllegalArgumentException("The number of nodes (" + nodes +
                                               ") must be divisible by the number of groups (" + groups + ")");
        this.nodes = nodes;
        this.groups = groups;
        this.required = required;
        this.canFail = canFail;
        this.nodeResources = nodeResources;
        this.type = type;
    }

    /** Returns the number of nodes requested */
    @Deprecated // TODO: Remove after April 2020
    public int nodeCount() { return nodes; }

    /** Returns the number of nodes requested (across all groups), or 0 if not specified */
    public int nodes() { return nodes; }

    /** Returns the number of groups requested, or 0 if not specified */
    public int groups() { return groups; }

    /**
     * The node flavor requested, or empty if no legacy flavor name has been used.
     * This may be satisfied by the requested flavor or a suitable replacement.
     *
     * @deprecated use nodeResources instead
     */
    @Deprecated // TODO: Remove after March 2020
    public Optional<String> flavor() {
        if (nodeResources().isEmpty()) return Optional.empty();
        return nodeResources.map(n -> n.toString());
    }

    /** Returns the resources requested for each node, or empty to leave this decision to provisioning */
    public Optional<NodeResources> nodeResources() { return nodeResources; }

    /** Returns whether the requested number of nodes must be met exactly for a request for this to succeed */
    public boolean isRequired() { return required; }

    /**
     * Returns true if an exception should be thrown if the specified capacity can not be satisfied
     * (to whatever policies are applied and taking required true/false into account).
     * Returns false if it is preferable to still succeed with partially satisfied capacity.
     */
    public boolean canFail() { return canFail; }

    /**
     * Returns the node type (role) requested. This is tenant nodes by default.
     * If some other type is requested the node count and flavor may be ignored
     * and all nodes of the requested type returned instead.
     */
    public NodeType type() { return type; }

    public Capacity withGroups(int groups) {
        return new Capacity(nodes, groups, nodeResources, required, canFail, type);
    }

    @Override
    public String toString() {
        return nodes + " nodes " +
               (groups > 1 ? "(in " + groups + " groups) " : "") +
               (nodeResources.isPresent() ? nodeResources.get() : "with default resources" );
    }

    /** Create a non-required, failable capacity request */
    public static Capacity fromCount(int nodes, int groups, NodeResources resources) {
        return fromCount(nodes, groups, resources, false, true);
    }

    public static Capacity fromCount(int nodes, int groups, NodeResources resources, boolean required, boolean canFail) {
        return new Capacity(nodes, groups, Optional.of(resources), required, canFail, NodeType.tenant);
    }

    public static Capacity fromCount(int nodes, int groups, Optional<NodeResources> resources, boolean required, boolean canFail) {
        return new Capacity(nodes, groups, resources, required, canFail, NodeType.tenant);
    }

    /** Create a non-required, failable capacity request */
    @Deprecated // TODO: Remove after April 2020
    public static Capacity fromCount(int nodes, NodeResources resources) {
        return fromCount(nodes, 0, resources, false, true);
    }

    @Deprecated // TODO: Remove after April 2020
    public static Capacity fromCount(int nodes, NodeResources resources, boolean required, boolean canFail) {
        return new Capacity(nodes, 0, Optional.of(resources), required, canFail, NodeType.tenant);
    }

    @Deprecated // TODO: Remove after April 2020
    public static Capacity fromCount(int nodes, Optional<NodeResources> resources, boolean required, boolean canFail) {
        return new Capacity(nodes, 0, resources, required, canFail, NodeType.tenant);
    }

    /** Creates this from a node type */
    public static Capacity fromRequiredNodeType(NodeType type) {
        return new Capacity(0, 0, Optional.empty(), true, false, type);
    }

}
