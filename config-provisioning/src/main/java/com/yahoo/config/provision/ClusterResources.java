// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        this.groups = groups;
        this.nodeResources = nodeResources;
    }

    /** Returns the total number of allocated nodes (over all groups) */
    public int nodes() { return nodes; }
    public int groups() { return groups; }
    public NodeResources nodeResources() { return nodeResources; }

    public ClusterResources with(NodeResources resources) {
        return new ClusterResources(nodes, groups, resources);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ClusterResources)) return false;

        ClusterResources other = (ClusterResources)o;
        if (other.nodes != this.nodes) return false;
        if (other.groups != this.groups) return false;
        if (other.nodeResources != this.nodeResources) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, groups, nodeResources);
    }

    @Override
    public String toString() {
        return "cluster resources: " + nodes + " * " + nodeResources + (groups > 1 ? " in " + groups + " groups" : "");
    }

}
