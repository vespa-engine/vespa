// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;

/**
 * The resources of a cluster
 *
 * @author bratseth
 */
public class ClusterResources {

    /** The node count in the cluster */
    private final int nodes;

    /** The number of node groups in the cluster */
    private final int groups;

    /** The resources of each node in the cluster */
    private final NodeResources nodeResources;

    public ClusterResources(int nodes, int groups, NodeResources nodeResources) {
        this.nodes = nodes;
        this.groups = groups == 0 ? 1 : groups; // TODO: Throw on groups == 0 after February 2023
        this.nodeResources = Objects.requireNonNull(nodeResources);
    }

    /** Returns the total number of allocated nodes (over all groups) */
    public int nodes() { return nodes; }
    public int groups() { return groups; }
    public NodeResources nodeResources() { return nodeResources; }

    public ClusterResources with(NodeResources resources) { return new ClusterResources(nodes, groups, resources); }
    public ClusterResources withNodes(int nodes) { return new ClusterResources(nodes, groups, nodeResources); }
    public ClusterResources withGroups(int groups) { return new ClusterResources(nodes, groups, nodeResources); }

    /** Returns true if this is smaller than the given resources in any dimension */
    public boolean smallerThan(ClusterResources other) {
        if (this.nodes < other.nodes) return true;
        if (this.groups < other.groups) return true;
        if (this.nodeResources.isUnspecified() || other.nodeResources.isUnspecified()) return false;

        if ( ! this.nodeResources.justNumbers().satisfies(other.nodeResources.justNumbers())) return true;
        return false;
    }

    /** Returns true if this is within the given limits (inclusive) and is compatible with them */
    public boolean isWithin(ClusterResources min, ClusterResources max) {
        if (this.smallerThan(min)) return false;
        if (max.smallerThan(this)) return false;
        if ( ! this.nodeResources.justNonNumbers().compatibleWith(min.nodeResources.justNonNumbers())) return false;
        if ( ! this.nodeResources.justNonNumbers().compatibleWith(max.nodeResources.justNonNumbers())) return false;
        return true;
    }

    /** Returns the total resources of this, that is the number of nodes times the node resources */
    public NodeResources totalResources() {
        return nodeResources.multipliedBy(nodes);
    }

    public ClusterResources justNumbers() {
        return new ClusterResources(nodes, groups, nodeResources.justNumbers());
    }

    /** Returns the standard cost of these resources, in dollars per hour */
    public double cost() {
        return nodes * nodeResources.cost();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ClusterResources other)) return false;

        if (other.nodes != this.nodes) return false;
        if (other.groups != this.groups) return false;
        if ( ! other.nodeResources.equals(this.nodeResources)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, groups, nodeResources);
    }

    @Override
    public String toString() {
        return nodes + " nodes" +
               (groups > 1 ? " (in " + groups + " groups)" : "") +
               " with " + nodeResources;
    }

}
